package org.cloudback.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        String nickname,
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @Email(message = "邮箱格式不正确")
        String email,
        @Pattern(regexp = "^https?://.*", message = "头像地址必须是http(s)链接")
        @Size(max = 512, message = "头像地址过长")
        String avatar
) {}
