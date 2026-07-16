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

    /** 纯查询：根据订单号查询支付记录（只读） */
    R<Payment> getPaymentByOrderNo(String orderNo, Long userId);

    /** 主动向支付宝查询最新支付状态并同步到本地 */
    R<Payment> syncPaymentStatus(String orderNo, Long userId);

    /** 创建待支付记录（由订单创建 Kafka 消费者触发），幂等：已存在则跳过 */
    R<String> processPayment(String orderNo, Long userId, BigDecimal amount);

    /** 生成支付宝电脑网站支付 HTML 表单，设置 30 分钟超时 */
    R<String> createPayForm(String orderNo, Long userId);

    /** 处理支付宝异步通知：RSA2 验签 → 校验金额 → 匹配交易状态 → 更新本地状态并通知订单服务 */
    String handleAlipayNotify(Map<String, String> params);
}
