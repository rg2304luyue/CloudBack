package org.cloudback.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
    Long addressId,

    @Size(max = 500, message = "备注最长500字")
    String remark,

    @NotBlank(message = "下单令牌不能为空")
    String orderToken
) {}
