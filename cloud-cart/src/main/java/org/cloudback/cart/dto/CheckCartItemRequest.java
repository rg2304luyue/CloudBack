package org.cloudback.cart.dto;

import jakarta.validation.constraints.NotNull;

public record CheckCartItemRequest(
        @NotNull(message = "checked不能为空")
        Boolean checked
) {}
