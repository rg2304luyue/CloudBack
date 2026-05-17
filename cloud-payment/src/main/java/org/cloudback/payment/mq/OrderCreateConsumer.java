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

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {
    private final PaymentService paymentService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_ORDER_CREATE, groupId = "payment-consumer-group")
    public void onOrderCreate(String message) {
        log.info("收到订单创建消息: {}", message);
        try {
            JSONObject msg = JSON.parseObject(message);
            String orderNo = msg.getString("orderNo");
            Long userId = msg.getLong("userId");
            BigDecimal totalAmount = msg.getBigDecimal("totalAmount");

            // 处理支付
            paymentService.processPayment(orderNo, userId, totalAmount);

            // 支付完成后，通知订单服务更新状态
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
