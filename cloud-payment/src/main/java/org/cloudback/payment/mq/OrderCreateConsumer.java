package org.cloudback.payment.mq;

import com.alibaba.fastjson2.JSON;
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

    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_ORDER_CREATE, groupId = "payment-consumer-group")
    public void onOrderCreate(String message) {
        log.info("收到订单创建消息: {}", message);
        try {
            JSONObject msg = JSON.parseObject(message);
            String orderNo = msg.getString("orderNo");
            Long userId = msg.getLong("userId");
            BigDecimal totalAmount = msg.getBigDecimal("totalAmount");

            paymentService.processPayment(orderNo, userId, totalAmount);
            log.info("待支付记录已创建: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("创建待支付记录异常", e);
        }
    }
}
