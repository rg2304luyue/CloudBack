package org.cloudback.order.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.order.mapper.OrderMapper;
import org.cloudback.order.model.entity.Order;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {
    private final OrderMapper orderMapper;

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

            // 更新订单状态为已支付
            Order order = new Order();
            order.setStatus(SystemConstants.ORDER_STATUS_PAID);
            order.setPayTime(LocalDateTime.now());

            orderMapper.update(order,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                            .eq(Order::getOrderNo, orderNo)
                            .set(Order::getStatus, SystemConstants.ORDER_STATUS_PAID)
                            .set(Order::getPayTime, LocalDateTime.now()));

            log.info("订单状态已更新为已支付: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("处理支付结果消息异常", e);
        }
    }
}
