package org.cloudback.order.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.order.mapper.OrderMapper;
import org.cloudback.order.model.entity.Order;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * Kafka 消费者：监听支付结果消息，更新订单状态为已支付。
 * 消费组: order-consumer-group
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final OrderMapper orderMapper;

    /** 消费支付结果消息 → 更新订单状态为已支付 */
    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT, groupId = "order-consumer-group")
    @Transactional(rollbackFor = Exception.class)
    public void onPaymentResult(String message) {
        log.info("收到支付结果消息: {}", message);
        try {
            JSONObject msg = JSON.parseObject(message);
            String orderNo = msg.getString("orderNo");
            String status = msg.getString("status");

            if (!"SUCCESS".equals(status)) {
                log.warn("支付失败，订单状态不变: orderNo={}", orderNo);
                return;
            }

            orderMapper.update(null,
                    new LambdaUpdateWrapper<Order>()
                            .eq(Order::getOrderNo, orderNo)
                            .set(Order::getStatus, SystemConstants.ORDER_STATUS_PAID)
                            .set(Order::getPayTime, LocalDateTime.now()));

            log.info("订单状态已更新为已支付: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("处理支付结果消息异常", e);
        }
    }
}
