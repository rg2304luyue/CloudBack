package org.cloudback.payment.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.service.OutboxService;
import org.cloudback.payment.config.AlipayProperties;
import org.cloudback.payment.mapper.PaymentMapper;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final AlipayClient alipayClient;
    private final AlipayProperties alipayProperties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxService outboxService;

    /** 根据订单号查询支付记录。若本地仍是待支付，主动向支付宝查询真实状态并同步（前端支付结果页依赖此行为）。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Payment> getPaymentByOrderNo(String orderNo, Long userId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            throw new BusinessException("支付记录不存在");
        }
        if (userId != null && !payment.getUserId().equals(userId)) {
            throw new BusinessException("无权查询此订单");
        }
        // 若本地待支付，主动同步支付宝状态（30秒内不重复查询，避免频率限制）
        if (payment.getStatus() == 0) {
            boolean shouldSync = payment.getLastSyncTime() == null
                    || LocalDateTime.now().isAfter(payment.getLastSyncTime().plusSeconds(30));
            if (shouldSync) {
                return syncPaymentStatus(orderNo, userId);
            }
        }
        return R.ok(payment);
    }

    /** 主动向支付宝查询支付状态并同步 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Payment> syncPaymentStatus(String orderNo, Long userId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            throw new BusinessException("支付记录不存在");
        }
        if (userId != null && !payment.getUserId().equals(userId)) {
            throw new BusinessException("无权查询此订单");
        }

        if (payment.getStatus() == 0) {
            log.info("本地状态待支付，主动查询支付宝: orderNo={}", orderNo);
            // 更新查询时间，防止频繁调用支付宝 API
            LocalDateTime syncTime = LocalDateTime.now();
            int updated = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                    .eq(Payment::getOrderNo, orderNo)
                    .eq(Payment::getStatus, 0)
                    .set(Payment::getLastSyncTime, syncTime));
            if (updated == 0) {
                // 条件更新使用当前读；普通 SELECT 在可重复读事务中可能仍返回旧快照。
                return R.ok(selectCurrentPayment(orderNo));
            }
            payment.setLastSyncTime(syncTime);
            try {
                AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
                JSONObject biz = new JSONObject();
                biz.put("out_trade_no", orderNo);
                request.setBizContent(biz.toJSONString());
                AlipayTradeQueryResponse response = alipayClient.execute(request);

                log.info("支付宝查询响应: success={}, tradeStatus={}, tradeNo={}, body={}",
                        response.isSuccess(), response.getTradeStatus(), response.getTradeNo(),
                        response.getBody());

                if (response.isSuccess() && "TRADE_CLOSED".equals(response.getTradeStatus())) {
                    paymentMapper.update(null,
                            new LambdaUpdateWrapper<Payment>()
                                    .eq(Payment::getOrderNo, orderNo)
                                    .eq(Payment::getStatus, 0)
                                    .set(Payment::getStatus, 2));
                    payment = paymentMapper.selectOne(
                            new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
                    log.info("Alipay trade closed: orderNo={}", orderNo);
                } else if (response.isSuccess()
                        && ("TRADE_SUCCESS".equals(response.getTradeStatus())
                        || "TRADE_FINISHED".equals(response.getTradeStatus()))) {
                    markPaid(payment, response.getTradeNo());
                    // 重新从数据库读取，确保返回的是 markPaid 写入后的完整数据
                    payment = paymentMapper.selectOne(
                            new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
                    log.info("支付宝同步支付成功: orderNo={}", orderNo);
                } else {
                    log.warn("支付宝查询未确认支付: orderNo={}, success={}, tradeStatus={}",
                            orderNo, response.isSuccess(), response.getTradeStatus());
                }
            } catch (AlipayApiException e) {
                log.error("主动查询支付宝交易状态失败: orderNo={}", orderNo, e);
            }
        }

        return R.ok(payment);
    }

    /** 创建待支付记录（Kafka 消费者触发）：幂等检查 → INSERT payment(status=0) */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> processPayment(String orderNo, Long userId, BigDecimal amount) {
        Long existCount = paymentMapper.selectCount(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (existCount > 0) {
            log.warn("支付记录已存在，跳过: orderNo={}", orderNo);
            return R.ok("已存在");
        }

        Payment payment = new Payment();
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setAmount(amount);
        payment.setPayMethod("ALIPAY");
        payment.setStatus(0);

        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            log.warn("并发创建支付记录，已存在: orderNo={}", orderNo);
            return R.ok("已存在");
        }

        log.info("待支付记录创建: orderNo={}, amount={}", orderNo, amount);
        return R.ok("待支付");
    }

    /** 生成支付宝电脑网站支付 HTML 表单：校验权限 → 构建 pagePay 请求 → 设置 30 分钟超时 → 返回表单 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> createPayForm(String orderNo, Long userId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            return R.fail("支付记录不存在");
        }
        if (!payment.getUserId().equals(userId)) {
            return R.fail("无权操作此订单");
        }
        if (payment.getStatus() == 0) {
            syncPaymentStatus(orderNo, userId);
            payment = selectCurrentPayment(orderNo);
        }
        if (payment.getStatus() != 0) {
            return R.fail("订单无需支付");
        }
        if (payment.getAmount() == null) {
            return R.fail("支付金额异常");
        }

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayProperties.getNotifyUrl());
        request.setReturnUrl(alipayProperties.getReturnUrl() + "?orderNo=" + orderNo);

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);
        bizContent.put("total_amount", payment.getAmount().toString());
        bizContent.put("subject", "CloudMall订单-" + orderNo);
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        bizContent.put("timeout_express", "30m");
        request.setBizContent(bizContent.toJSONString());

        try {
            String form = alipayClient.pageExecute(request).getBody();
            return R.ok(form);
        } catch (AlipayApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("TRADE_HAS_SUCCESS")) {
                syncPaymentStatus(orderNo, userId);
                return R.fail("order is already paid; payment status is being synchronized");
            }
            log.error("生成支付宝支付表单失败: orderNo={}", orderNo, e);
            return R.fail("创建支付订单失败，请重试");
        }
    }

    /** 处理支付宝异步通知：RSA2 验签 → 校验 app_id → 校验金额 → 匹配交易状态 → 更新本地状态并通知订单服务 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleAlipayNotify(Map<String, String> params) {
        // 1. RSA2 验签
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    params, alipayProperties.getPublicKey(), "UTF-8", "RSA2");
            if (!verified) {
                log.error("支付宝异步通知验签失败");
                return "failure";
            }
        } catch (AlipayApiException e) {
            log.error("支付宝异步通知验签异常", e);
            return "failure";
        }

        // 2. 必填字段与应用身份校验
        String notifyAppId = params.get("app_id");
        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");
        String alipayAmount = params.get("total_amount");
        if (isBlank(notifyAppId) || isBlank(orderNo) || isBlank(tradeNo)
                || isBlank(tradeStatus) || isBlank(alipayAmount)) {
            log.error("支付宝通知缺少必填字段");
            return "failure";
        }
        if (isBlank(alipayProperties.getAppId()) || !alipayProperties.getAppId().equals(notifyAppId)) {
            log.error("支付宝通知 app_id 不匹配");
            return "failure";
        }

        // 3. 本地支付记录与金额校验。未知订单返回 failure，让支付宝稍后重试。
        Payment localPayment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (localPayment == null || localPayment.getAmount() == null
                || !"ALIPAY".equals(localPayment.getPayMethod())) {
            log.error("支付宝通知找不到有效的本地支付记录: orderNo={}", orderNo);
            return "failure";
        }
        try {
            BigDecimal callbackAmount = new BigDecimal(alipayAmount);
            if (callbackAmount.signum() <= 0 || localPayment.getAmount().compareTo(callbackAmount) != 0) {
                log.error("支付宝通知金额不匹配: orderNo={}", orderNo);
                return "failure";
            }
        } catch (NumberFormatException e) {
            log.error("支付宝通知金额格式非法: orderNo={}", orderNo);
            return "failure";
        }

        // 4. 只确认已经被本地可靠处理的明确状态
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            if (!markPaid(localPayment, tradeNo)) {
                return "failure";
            }
            log.info("支付宝异步通知处理成功: orderNo={}, tradeNo={}", orderNo, tradeNo);
            return "success";
        } else if ("TRADE_CLOSED".equals(tradeStatus)) {
            int rows = paymentMapper.update(null,
                    new LambdaUpdateWrapper<Payment>()
                            .eq(Payment::getOrderNo, orderNo)
                            .eq(Payment::getStatus, 0)
                            .set(Payment::getStatus, 2));
            Payment current = rows == 0 ? selectCurrentPayment(orderNo) : localPayment;
            boolean handled = rows == 1 || (current != null && (current.getStatus() == 2
                    || (current.getStatus() == 1 && tradeNo.equals(current.getTradeNo()))));
            if (handled) {
                log.info("支付宝交易已关闭（超时）: orderNo={}", orderNo);
                return "success";
            }
        }

        log.warn("支付宝通知状态未被处理: tradeStatus={}, orderNo={}", tradeStatus, orderNo);
        return "failure";
    }

    /** 标记支付成功：幂等检查 → 条件 UPDATE payment SET status=1 → 仅成功时 Kafka 通知订单服务 */
    private boolean markPaid(Payment payment, String tradeNo) {
        String orderNo = payment.getOrderNo();
        if (payment.getStatus() == 1) {
            boolean sameTrade = tradeNo.equals(payment.getTradeNo());
            if (!sameTrade) {
                log.error("已支付订单收到不同交易号: orderNo={}", orderNo);
            }
            return sameTrade;
        }
        if (payment.getStatus() != 0) {
            log.warn("非待支付订单不能标记支付成功: orderNo={}, status={}", orderNo, payment.getStatus());
            return false;
        }

        int rows = paymentMapper.update(null,
                new LambdaUpdateWrapper<Payment>()
                        .eq(Payment::getOrderNo, orderNo)
                        .eq(Payment::getStatus, 0)
                        .set(Payment::getStatus, 1)
                        .set(Payment::getTradeNo, tradeNo)
                        .set(Payment::getPayTime, LocalDateTime.now()));

        if (rows == 0) {
            Payment current = selectCurrentPayment(orderNo);
            return current != null && current.getStatus() == 1 && tradeNo.equals(current.getTradeNo());
        }

        // 仅更新成功时通过 Outbox 可靠发送 Kafka 通知
        JSONObject resultMsg = new JSONObject();
        resultMsg.put("orderNo", orderNo);
        resultMsg.put("status", "SUCCESS");
        resultMsg.put("tradeNo", tradeNo);
        outboxService.saveMessage(SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT, orderNo, resultMsg.toJSONString());

        log.info("支付成功已标记: orderNo={}, tradeNo={}", orderNo, tradeNo);
        return true;
    }

    private Payment selectCurrentPayment(String orderNo) {
        return paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderNo, orderNo).last("FOR UPDATE"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
