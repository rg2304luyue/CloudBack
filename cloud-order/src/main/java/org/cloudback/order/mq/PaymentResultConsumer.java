package org.cloudback.order.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.order.mapper.OrderMapper;
import org.cloudback.order.model.entity.Order;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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

    /** 消费支付结果消息：解析 orderNo + status → 仅 SUCCESS 且当前 UNPAID 时更新为 PAID（条件 UPDATE 防覆盖） */
    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT, groupId = "order-consumer-group")
    public void onPaymentResult(String message) {
        log.info("收到支付结果消息: {}", message);

        String orderNo;
        String status;
        try {
            JSONObject msg = JSON.parseObject(message);
            orderNo = msg.getString("orderNo");
            status = msg.getString("status");
        } catch (JSONException e) {
            log.error("支付结果消息反序列化失败, 丢弃无效消息: {}", message, e);
            return; // 无效消息不重试
        }

        if (orderNo == null || status == null) {
            log.error("支付结果消息缺少必要字段, orderNo={}, status={}", orderNo, status);
            return;
        }

        if (!"SUCCESS".equals(status)) {
            log.warn("支付失败，订单状态不变: orderNo={}", orderNo);
            return;
        }

        try {
            // 仅当订单状态为「待支付」时更新，防止覆盖已取消/已完成的订单
            int rows = orderMapper.update(null,
                    new LambdaUpdateWrapper<Order>()
                            .eq(Order::getOrderNo, orderNo)
                            .eq(Order::getStatus, SystemConstants.ORDER_STATUS_UNPAID)
                            .set(Order::getStatus, SystemConstants.ORDER_STATUS_PAID)
                            .set(Order::getPayTime, LocalDateTime.now()));

            if (rows > 0) {
                log.info("订单状态已更新为已支付: orderNo={}", orderNo);
            } else {
                log.warn("订单状态更新影响0行, 可能已被处理或状态不匹配: orderNo={}", orderNo);
            }
        } catch (Exception e) {
            log.error("更新订单状态失败, 消息将被重试: orderNo={}", orderNo, e);
            throw e; // 抛出异常触发 Kafka 重试
        }
    }
}
