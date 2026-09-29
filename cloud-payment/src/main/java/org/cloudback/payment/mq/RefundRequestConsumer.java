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
 * Kafka 消费者：监听 refund-request 消息，处理「支付成功但订单已取消」的自动退款。
 * 消费组: payment-consumer-group
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestConsumer {

    private final PaymentService paymentService;

    /** 消费退款请求消息：解析 orderNo/tradeNo → 调支付宝退款（失败抛异常触发重试，兜底进 DLQ） */
    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_REFUND_REQUEST, groupId = "payment-consumer-group")
    public void onRefundRequest(String message) {
        log.info("收到退款请求消息: {}", message);

        String orderNo;
        String tradeNo;
        try {
            JSONObject msg = JSON.parseObject(message);
            orderNo = msg.getString("orderNo");
            tradeNo = msg.getString("tradeNo");
        } catch (JSONException e) {
            log.error("退款请求消息反序列化失败, 丢弃无效消息: {}", message, e);
            return;
        }

        if (orderNo == null || orderNo.isBlank()) {
            log.error("退款请求消息缺少 orderNo: {}", message);
            return;
        }

        try {
            paymentService.refundByOrder(orderNo, tradeNo);
            log.info("退款处理完成: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("退款处理失败, 消息将被重试: orderNo={}", orderNo, e);
            throw e;
        }
    }
}
