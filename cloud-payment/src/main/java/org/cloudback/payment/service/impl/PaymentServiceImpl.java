package org.cloudback.payment.service.impl;

import com.alibaba.fastjson2.JSON;
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

    @Override
    public R<Payment> getPaymentByOrderNo(String orderNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) {
            throw new BusinessException("支付记录不存在");
        }

        // 如果本地记录仍是待支付，主动向支付宝查询真实状态
        if (payment.getStatus() == 0) {
            try {
                AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
                JSONObject biz = new JSONObject();
                biz.put("out_trade_no", orderNo);
                request.setBizContent(biz.toJSONString());
                AlipayTradeQueryResponse response = alipayClient.execute(request);

                if (response.isSuccess() && "TRADE_SUCCESS".equals(response.getTradeStatus())) {
                    markPaid(orderNo, response.getTradeNo());
                    payment.setStatus(1);
                    payment.setTradeNo(response.getTradeNo());
                    payment.setPayTime(LocalDateTime.now());
                }
            } catch (AlipayApiException e) {
                log.warn("主动查询支付宝交易状态失败: orderNo={}", orderNo, e);
            }
        }

        return R.ok(payment);
    }

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

        paymentMapper.insert(payment);
        log.info("待支付记录创建: orderNo={}, amount={}", orderNo, amount);
        return R.ok("待支付");
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleAlipayNotify(Map<String, String> params) {
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

        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            log.info("支付宝通知非成功状态: trade_status={}", tradeStatus);
            return "success";
        }

        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        markPaid(orderNo, tradeNo);
        log.info("支付宝异步通知处理成功: orderNo={}, tradeNo={}", orderNo, tradeNo);
        return "success";
    }

    /** 标记支付成功并通知订单服务 */
    private void markPaid(String orderNo, String tradeNo) {
        Payment existPayment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (existPayment != null && existPayment.getStatus() == 1) {
            log.info("支付已处理，幂等跳过: orderNo={}", orderNo);
            return;
        }

        paymentMapper.update(null,
                new LambdaUpdateWrapper<Payment>()
                        .eq(Payment::getOrderNo, orderNo)
                        .eq(Payment::getStatus, 0)
                        .set(Payment::getStatus, 1)
                        .set(Payment::getTradeNo, tradeNo)
                        .set(Payment::getPayTime, LocalDateTime.now()));

        JSONObject resultMsg = new JSONObject();
        resultMsg.put("orderNo", orderNo);
        resultMsg.put("status", "SUCCESS");
        kafkaTemplate.send(SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT, resultMsg.toJSONString());

        log.info("支付成功已标记: orderNo={}, tradeNo={}", orderNo, tradeNo);
    }
}
