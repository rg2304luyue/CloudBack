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

    /** 根据订单号查询支付记录。若本地仍是待支付，主动向支付宝查询真实状态并同步（前端支付结果页依赖此行为）。 */
    @Override
    public R<Payment> getPaymentByOrderNo(String orderNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            throw new BusinessException("支付记录不存在");
        }
        // 若本地待支付，主动同步支付宝状态
        if (payment.getStatus() == 0) {
            return syncPaymentStatus(orderNo);
        }
        return R.ok(payment);
    }

    /** 主动向支付宝查询支付状态并同步 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Payment> syncPaymentStatus(String orderNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            throw new BusinessException("支付记录不存在");
        }

        if (payment.getStatus() == 0) {
            log.info("本地状态待支付，主动查询支付宝: orderNo={}", orderNo);
            try {
                AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
                JSONObject biz = new JSONObject();
                biz.put("out_trade_no", orderNo);
                request.setBizContent(biz.toJSONString());
                AlipayTradeQueryResponse response = alipayClient.execute(request);

                log.info("支付宝查询响应: success={}, tradeStatus={}, tradeNo={}, body={}",
                        response.isSuccess(), response.getTradeStatus(), response.getTradeNo(),
                        response.getBody());

                if (response.isSuccess() && "TRADE_SUCCESS".equals(response.getTradeStatus())) {
                    markPaid(orderNo, response.getTradeNo());
                    payment.setStatus(1);
                    payment.setTradeNo(response.getTradeNo());
                    payment.setPayTime(LocalDateTime.now());
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
    public R<String> createPayForm(String orderNo, Long userId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            return R.fail("支付记录不存在");
        }
        if (!payment.getUserId().equals(userId)) {
            return R.fail("无权操作此订单");
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

        // 2. 校验 app_id
        String notifyAppId = params.get("app_id");
        if (notifyAppId != null && !notifyAppId.equals(alipayProperties.getAppId())) {
            log.error("支付宝通知 app_id 不匹配: expected={}, actual={}", alipayProperties.getAppId(), notifyAppId);
            return "failure";
        }

        // 3. 获取必要字段
        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        if (orderNo == null || tradeNo == null) {
            log.error("支付宝通知缺少必要字段: orderNo={}, tradeNo={}", orderNo, tradeNo);
            return "failure";
        }

        // 4. 校验支付金额
        String alipayAmount = params.get("total_amount");
        if (alipayAmount != null) {
            Payment localPayment = paymentMapper.selectOne(
                    new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
            if (localPayment != null && localPayment.getAmount() != null) {
                BigDecimal alipayDecimal = new BigDecimal(alipayAmount);
                if (localPayment.getAmount().compareTo(alipayDecimal) != 0) {
                    log.error("支付金额不匹配: orderNo={}, local={}, alipay={}",
                            orderNo, localPayment.getAmount(), alipayAmount);
                    return "failure";
                }
            }
        }

        // 5. 处理交易状态
        String tradeStatus = params.get("trade_status");
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            markPaid(orderNo, tradeNo);
            log.info("支付宝异步通知处理成功: orderNo={}, tradeNo={}", orderNo, tradeNo);
            return "success";
        } else if ("TRADE_CLOSED".equals(tradeStatus)) {
            // 支付超时关闭，标记支付失败
            paymentMapper.update(null,
                    new LambdaUpdateWrapper<Payment>()
                            .eq(Payment::getOrderNo, orderNo)
                            .eq(Payment::getStatus, 0)
                            .set(Payment::getStatus, 2));
            log.info("支付宝交易已关闭（超时）: orderNo={}", orderNo);
            return "success";
        }

        log.info("支付宝通知非终态: trade_status={}, orderNo={}", tradeStatus, orderNo);
        return "success";
    }

    /** 标记支付成功：幂等检查 → 条件 UPDATE payment SET status=1 → 仅成功时 Kafka 通知订单服务 */
    private void markPaid(String orderNo, String tradeNo) {
        Payment existPayment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (existPayment == null) {
            log.warn("支付记录不存在，无法标记支付成功: orderNo={}", orderNo);
            return;
        }
        if (existPayment.getStatus() == 1) {
            log.info("支付已处理，幂等跳过: orderNo={}", orderNo);
            return;
        }

        int rows = paymentMapper.update(null,
                new LambdaUpdateWrapper<Payment>()
                        .eq(Payment::getOrderNo, orderNo)
                        .eq(Payment::getStatus, 0)
                        .set(Payment::getStatus, 1)
                        .set(Payment::getTradeNo, tradeNo)
                        .set(Payment::getPayTime, LocalDateTime.now()));

        if (rows == 0) {
            log.warn("更新支付状态影响 0 行，可能已被并发处理: orderNo={}", orderNo);
            return;
        }

        // 仅更新成功时发送 Kafka 通知
        JSONObject resultMsg = new JSONObject();
        resultMsg.put("orderNo", orderNo);
        resultMsg.put("status", "SUCCESS");
        kafkaTemplate.send(SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT, resultMsg.toJSONString())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 支付结果发送失败: orderNo={}", orderNo, ex);
                    }
                });

        log.info("支付成功已标记: orderNo={}, tradeNo={}", orderNo, tradeNo);
    }
}
