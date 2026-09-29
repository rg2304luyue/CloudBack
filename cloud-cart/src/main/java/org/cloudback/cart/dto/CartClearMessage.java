package org.cloudback.cart.dto;

import java.util.List;

/**
 * 下单成功后清理购物车的 Kafka 消息（topic: cart-clear）。
 * payload JSON 示例: {"userId":123,"productIds":[1,2]}
 */
public record CartClearMessage(Long userId, List<Long> productIds) {
}
