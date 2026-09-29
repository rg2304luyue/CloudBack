package org.cloudback.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull(message = "商品ID不能为空")
        Long productId,
        @NotNull(message = "商品数量不能为空")
        @Min(value = 1, message = "商品数量必须大于0")
        @Max(value = 999, message = "商品数量不能超过999")
        Integer quantity
) {}
