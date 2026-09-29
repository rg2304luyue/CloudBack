package org.cloudback.cart.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 批量勾选/取消勾选购物车商品请求
 */
public record CheckAllCartItemsRequest(
        @NotNull(message = "checked不能为空")
        Boolean checked
) {
}
