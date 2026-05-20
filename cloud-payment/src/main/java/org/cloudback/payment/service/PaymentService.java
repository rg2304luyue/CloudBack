package org.cloudback.payment.service;

import org.cloudback.common.result.R;
import org.cloudback.payment.model.entity.Payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付服务接口，提供支付记录查询、支付发起和异步通知处理。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public interface PaymentService {

    /** 根据订单号查询支付记录 */
    R<Payment> getPaymentByOrderNo(String orderNo);

    /** 创建待支付记录（由 Kafka 消费者调用） */
    R<String> processPayment(String orderNo, Long userId, BigDecimal amount);

    /** 生成支付宝页面支付表单 HTML */
    R<String> createPayForm(String orderNo, Long userId);

    /** 处理支付宝异步通知，验签并更新支付状态 */
    String handleAlipayNotify(Map<String, String> params);
}
