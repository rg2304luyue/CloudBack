package org.cloudback.cart.dto;

/**
 * 批量勾选/取消勾选购物车商品请求
 */
public record CheckAllCartItemsRequest(Boolean checked) {
}
