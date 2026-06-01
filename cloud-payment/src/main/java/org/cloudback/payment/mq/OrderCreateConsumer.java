package org.cloudback.payment.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Kafka 消费者：监听 order-create 消息，创建待支付记录。
 * 实际支付由用户手动发起支付宝页面支付完成。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private final PaymentService paymentService;

    /** 消费订单创建消息：解析 orderNo/userId/amount → 创建待支付记录（幂等，已存在则跳过） */
    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_ORDER_CREATE, groupId = "payment-consumer-group")
    public void onOrderCreate(String message) {
        log.info("收到订单创建消息: {}", message);

        String orderNo;
        Long userId;
        BigDecimal totalAmount;
        try {
            JSONObject msg = JSON.parseObject(message);
            orderNo = msg.getString("orderNo");
            userId = msg.getLong("userId");
            totalAmount = msg.getBigDecimal("totalAmount");
        } catch (JSONException e) {
            log.error("订单创建消息反序列化失败, 丢弃无效消息: {}", message, e);
            return;
        }

        if (orderNo == null || userId == null || totalAmount == null) {
            log.error("订单创建消息缺少必要字段: orderNo={}, userId={}, amount={}", orderNo, userId, totalAmount);
            return;
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("订单金额无效: orderNo={}, amount={}", orderNo, totalAmount);
            return;
        }

        try {
            paymentService.processPayment(orderNo, userId, totalAmount);
            log.info("待支付记录已创建: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("创建待支付记录失败, 消息将被重试: orderNo={}", orderNo, e);
            throw e;
        }
    }
}
