package org.cloudback.payment.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.service.OutboxService;
import org.cloudback.payment.config.AlipayProperties;
import org.cloudback.payment.feign.OrderFeignClient;
import org.cloudback.payment.mapper.PaymentMapper;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final OrderFeignClient orderFeignClient;
    private final PlatformTransactionManager transactionManager;

    /** 短事务模板：把支付宝网络调用排除在事务边界之外，避免长事务占用连接 */
    private TransactionTemplate transaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(10);
        return tx;
    }

    /** 根据订单号查询支付记录。若本地仍是待支付，主动向支付宝查询真实状态并同步（前端支付结果页依赖此行为）。 */
    @Override
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

    /** 主动向支付宝查询支付状态并同步（抢占 → 事务外查询支付宝 → 短事务应用结果） */
    @Override
    public R<Payment> syncPaymentStatus(String orderNo, Long userId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            throw new BusinessException("支付记录不存在");
        }
        if (userId != null && !payment.getUserId().equals(userId)) {
            throw new BusinessException("无权查询此订单");
        }

        if (!SystemConstants.PAYMENT_STATUS_UNPAID.equals(payment.getStatus())) {
            return R.ok(payment);
        }

        log.info("本地状态待支付，主动查询支付宝: orderNo={}", orderNo);
        // 事务 1：条件更新抢占同步权并提交，防止并发重复查询支付宝
        LocalDateTime syncTime = LocalDateTime.now();
        boolean claimed = Boolean.TRUE.equals(transaction().execute(tx -> paymentMapper.update(null,
                new LambdaUpdateWrapper<Payment>()
                        .eq(Payment::getOrderNo, orderNo)
                        .eq(Payment::getStatus, SystemConstants.PAYMENT_STATUS_UNPAID)
                        .set(Payment::getLastSyncTime, syncTime)) > 0));
        if (!claimed) {
            // 条件更新使用当前读；普通 SELECT 在可重复读事务中可能仍返回旧快照。
            return R.ok(selectCurrentPayment(orderNo));
        }

        // 事务外调用支付宝外网 API
        AlipayTradeQueryResponse response;
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            JSONObject biz = new JSONObject();
            biz.put("out_trade_no", orderNo);
            request.setBizContent(biz.toJSONString());
            response = alipayClient.execute(request);

            log.info("支付宝查询响应: success={}, tradeStatus={}, tradeNo={}, body={}",
                    response.isSuccess(), response.getTradeStatus(), response.getTradeNo(),
                    response.getBody());
        } catch (AlipayApiException e) {
            log.error("主动查询支付宝交易状态失败: orderNo={}", orderNo, e);
            return R.ok(selectCurrentPayment(orderNo));
        }

        // 事务 2：应用查询结果（状态更新与 outbox 保持原子）
        if (response.isSuccess() && "TRADE_CLOSED".equals(response.getTradeStatus())) {
            transaction().executeWithoutResult(tx -> paymentMapper.update(null,
                    new LambdaUpdateWrapper<Payment>()
                            .eq(Payment::getOrderNo, orderNo)
                            .eq(Payment::getStatus, SystemConstants.PAYMENT_STATUS_UNPAID)
                            .set(Payment::getStatus, SystemConstants.PAYMENT_STATUS_FAILED)));
            log.info("Alipay trade closed: orderNo={}", orderNo);
        } else if (response.isSuccess()
                && ("TRADE_SUCCESS".equals(response.getTradeStatus())
                || "TRADE_FINISHED".equals(response.getTradeStatus()))) {
            transaction().executeWithoutResult(tx -> markPaid(payment, response.getTradeNo()));
            log.info("支付宝同步支付成功: orderNo={}", orderNo);
        } else {
            log.warn("支付宝查询未确认支付: orderNo={}, success={}, tradeStatus={}",
                    orderNo, response.isSuccess(), response.getTradeStatus());
        }

        // 重新从数据库读取，确保返回的是结果应用后的完整数据
        return R.ok(selectCurrentPayment(orderNo));
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

    /** 生成支付宝电脑网站支付 HTML 表单：校验权限与订单状态 → 构建 pagePay 请求 → 设置 30 分钟超时 → 返回表单 */
    @Override
    public R<String> createPayForm(String orderNo, Long userId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            return R.fail("支付记录不存在");
        }
        if (!payment.getUserId().equals(userId)) {
            return R.fail("无权操作此订单");
        }
        if (SystemConstants.PAYMENT_STATUS_UNPAID.equals(payment.getStatus())) {
            syncPaymentStatus(orderNo, userId);
            payment = selectCurrentPayment(orderNo);
        }
        if (!SystemConstants.PAYMENT_STATUS_UNPAID.equals(payment.getStatus())) {
            return R.fail("订单无需支付");
        }
        if (payment.getAmount() == null) {
            return R.fail("支付金额异常");
        }

        // 发起支付前向订单服务确认订单状态。fail-closed：确认失败宁可拦截也不放行资损窗口
        R<Map<String, Object>> orderResult;
        try {
            orderResult = orderFeignClient.getOrderStatus(orderNo);
        } catch (Exception e) {
            log.error("确认订单状态失败，拒绝发起支付: orderNo={}", orderNo, e);
            return R.fail("订单状态确认失败，请稍后重试");
        }
        if (orderResult == null) {
            return R.fail("订单状态确认失败，请稍后重试");
        }
        if (orderResult.getCode() != 200 || orderResult.getData() == null) {
            return R.fail("订单不存在");
        }
        Object statusObj = orderResult.getData().get("status");
        Integer orderStatus = statusObj instanceof Number number ? number.intValue() : null;
        if (orderStatus == null) {
            return R.fail("订单状态确认失败，请稍后重试");
        }
        if (SystemConstants.ORDER_STATUS_CANCELLED.equals(orderStatus)) {
            // 同步关闭本地待支付单，保持双侧状态一致
            paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                    .eq(Payment::getOrderNo, orderNo)
                    .eq(Payment::getStatus, SystemConstants.PAYMENT_STATUS_UNPAID)
                    .set(Payment::getStatus, SystemConstants.PAYMENT_STATUS_CLOSED));
            return R.fail("订单已取消，无法支付");
        }
        if (!SystemConstants.ORDER_STATUS_UNPAID.equals(orderStatus)) {
            return R.fail("订单当前状态不可支付");
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

    /** 订单取消联动关闭支付单：条件 UPDATE 0→3；已支付记录错误日志（由退款流程兜底）；已关闭幂等返回 */
    @Override
    public R<String> closePayment(String orderNo) {
        int rows = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getOrderNo, orderNo)
                .eq(Payment::getStatus, SystemConstants.PAYMENT_STATUS_UNPAID)
                .set(Payment::getStatus, SystemConstants.PAYMENT_STATUS_CLOSED));
        if (rows > 0) {
            log.info("支付单已随订单取消关闭: orderNo={}", orderNo);
            return R.ok("支付单已关闭");
        }
        Payment current = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (current == null) {
            log.warn("关闭支付单时未找到记录: orderNo={}", orderNo);
            return R.ok("无支付记录");
        }
        if (SystemConstants.PAYMENT_STATUS_PAID.equals(current.getStatus())) {
            log.error("订单取消到达时支付已完成，需走退款流程: orderNo={}", orderNo);
            return R.ok("已支付，走退款流程");
        }
        // 已关闭或其他终态：幂等
        return R.ok("支付单已关闭");
    }

    /** 支付成功但订单已取消的自动退款：校验本地已支付 → 支付宝退款（out_request_no=orderNo 幂等）→ 条件 UPDATE 1→4 */
    @Override
    public R<String> refundByOrder(String orderNo, String tradeNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            throw new BusinessException("退款失败：支付记录不存在, orderNo=" + orderNo);
        }
        if (SystemConstants.PAYMENT_STATUS_REFUNDED.equals(payment.getStatus())) {
            return R.ok("已退款");
        }
        if (!SystemConstants.PAYMENT_STATUS_PAID.equals(payment.getStatus())) {
            throw new BusinessException("退款失败：支付单状态异常, orderNo=" + orderNo + ", status=" + payment.getStatus());
        }
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new BusinessException("退款失败：支付金额异常, orderNo=" + orderNo);
        }
        if (tradeNo != null && payment.getTradeNo() != null && !tradeNo.equals(payment.getTradeNo())) {
            log.error("退款请求交易号与本地不一致: orderNo={}, requestTradeNo={}, localTradeNo={}",
                    orderNo, tradeNo, payment.getTradeNo());
        }

        // 支付宝退款（网络调用保持在事务外）
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", orderNo);
        biz.put("refund_amount", payment.getAmount().toPlainString());
        biz.put("out_request_no", orderNo);
        request.setBizContent(biz.toJSONString());

        AlipayTradeRefundResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            log.error("调用支付宝退款接口失败: orderNo={}", orderNo, e);
            throw new BusinessException("退款调用失败，将重试: " + orderNo);
        }
        log.info("支付宝退款响应: orderNo={}, success={}, code={}, subCode={}, body={}",
                orderNo, response.isSuccess(), response.getCode(), response.getSubCode(), response.getBody());

        if (!response.isSuccess() && !"REFUND_SUCCESS".equals(response.getSubCode())) {
            throw new BusinessException("支付宝退款未成功: orderNo=" + orderNo + ", subCode=" + response.getSubCode());
        }

        int rows = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getOrderNo, orderNo)
                .eq(Payment::getStatus, SystemConstants.PAYMENT_STATUS_PAID)
                .set(Payment::getStatus, SystemConstants.PAYMENT_STATUS_REFUNDED));
        if (rows == 0) {
            Payment current = paymentMapper.selectOne(
                    new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
            if (current != null && SystemConstants.PAYMENT_STATUS_REFUNDED.equals(current.getStatus())) {
                return R.ok("已退款");
            }
            throw new BusinessException("退款状态更新失败: orderNo=" + orderNo);
        }
        log.info("退款成功: orderNo={}, amount={}", orderNo, payment.getAmount());
        return R.ok("退款成功");
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
