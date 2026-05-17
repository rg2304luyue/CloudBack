package org.cloudback.payment.service;

import org.cloudback.common.result.R;
import org.cloudback.payment.model.entity.Payment;

import java.math.BigDecimal;

/**
 * 支付服务接口，提供支付记录查询和支付处理功能。
 * 后续对接支付宝沙箱时只需修改 processPayment 实现。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public interface PaymentService {

    /** 根据订单号查询支付记录 */
    R<Payment> getPaymentByOrderNo(String orderNo);

    /** 处理支付，创建支付记录并返回交易号 */
    R<String> processPayment(String orderNo, Long userId, BigDecimal amount);
}
