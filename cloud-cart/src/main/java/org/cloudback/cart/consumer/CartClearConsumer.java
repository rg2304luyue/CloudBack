package org.cloudback.cart.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.cart.dto.CartClearMessage;
import org.cloudback.cart.service.CartService;
import org.cloudback.common.constant.SystemConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 下单成功后的购物车清理消费者。
 * 订单服务向 cart-clear topic 发送 {"userId":123,"productIds":[1,2]}，
 * 此处逐个删除对应购物车项（HDEL 幂等，不存在则跳过）。
 * 处理失败时异常抛出，由 Kafka 重试机制保证最终一致。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartClearConsumer {

    private final CartService cartService;

    /**
     * 与 PaymentResultConsumer 一致：生产者使用 String 序列化并带 __TypeId__ 头，
     * 这里直接接收字符串后用 fastjson2 解析，避免 JsonDeserializer 的
     * trusted.packages 校验（java.lang.String 不在白名单）导致的消费失败。
     */
    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_CART_CLEAR)
    public void onCartClear(String payload) {
        CartClearMessage message;
        try {
            message = JSON.parseObject(payload, CartClearMessage.class);
        } catch (Exception e) {
            log.error("购物车清理消息反序列化失败, 丢弃无效消息: {}", payload, e);
            return;
        }
        if (message == null || message.userId() == null || message.productIds() == null) {
            log.warn("收到无效的购物车清理消息: {}", payload);
            return;
        }
        log.info("收到购物车清理消息: userId={}, productIds={}", message.userId(), message.productIds());
        for (Long productId : message.productIds()) {
            if (productId != null) {
                // HDEL 不存在的 field 为幂等操作，直接复用现有删除逻辑
                cartService.removeItem(message.userId(), productId);
            }
        }
        log.info("购物车清理完成: userId={}, count={}", message.userId(), message.productIds().size());
    }
}
