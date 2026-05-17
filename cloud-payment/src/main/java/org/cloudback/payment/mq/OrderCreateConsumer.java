package org.cloudback.payment.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Kafka 消费者：监听 order-create 消息，处理支付并回写支付结果。
 * 消费组: payment-consumer-group
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private final PaymentService paymentService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 消费订单创建消息 → 处理支付 → 发送支付结果 */
    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_ORDER_CREATE, groupId = "payment-consumer-group")
    public void onOrderCreate(String message) {
        log.info("收到订单创建消息: {}", message);
        try {
            JSONObject msg = JSON.parseObject(message);
            String orderNo = msg.getString("orderNo");
            Long userId = msg.getLong("userId");
            BigDecimal totalAmount = msg.getBigDecimal("totalAmount");

            paymentService.processPayment(orderNo, userId, totalAmount);

            JSONObject resultMsg = new JSONObject();
            resultMsg.put("orderNo", orderNo);
            resultMsg.put("userId", userId);
            resultMsg.put("status", "SUCCESS");
            kafkaTemplate.send(SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT, resultMsg.toJSONString());

            log.info("支付结果已发送: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("处理支付消息异常", e);
        }
    }
}
