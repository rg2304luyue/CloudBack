package org.cloudback.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
    @NotNull(message = "请选择收货地址")
    @Positive(message = "收货地址无效")
    Long addressId,

    @Size(max = 500, message = "备注最长500字")
    String remark,

    @NotBlank(message = "下单令牌不能为空")
    String orderToken
) {}
