package org.cloudback.user.dto;

import jakarta.validation.constraints.NotNull;

public record ProcessApplicationRequest(
        @NotNull(message = "审批结果不能为空") Boolean approved
) {}
