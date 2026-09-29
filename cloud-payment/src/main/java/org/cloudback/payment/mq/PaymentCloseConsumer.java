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

/**
 * Kafka 消费者：监听 payment-close 消息，订单取消后联动关闭待支付单。
 * 消费组: payment-consumer-group
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCloseConsumer {

    private final PaymentService paymentService;

    /** 消费支付单关闭消息：解析 orderNo → 条件关闭待支付单（幂等） */
    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_PAYMENT_CLOSE, groupId = "payment-consumer-group")
    public void onPaymentClose(String message) {
        log.info("收到支付单关闭消息: {}", message);

        String orderNo;
        try {
            JSONObject msg = JSON.parseObject(message);
            orderNo = msg.getString("orderNo");
        } catch (JSONException e) {
            log.error("支付单关闭消息反序列化失败, 丢弃无效消息: {}", message, e);
            return;
        }

        if (orderNo == null || orderNo.isBlank()) {
            log.error("支付单关闭消息缺少 orderNo: {}", message);
            return;
        }

        try {
            paymentService.closePayment(orderNo);
        } catch (Exception e) {
            log.error("关闭支付单失败, 消息将被重试: orderNo={}", orderNo, e);
            throw e;
        }
    }
}
