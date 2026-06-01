package org.cloudback.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cloudback.auth.dto.LoginRequest;
import org.cloudback.auth.dto.RegisterRequest;
import org.cloudback.auth.service.AuthService;
import org.cloudback.common.result.R;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器，提供注册和登录接口。
 * 两个接口均在 Gateway 白名单中，无需 Token。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 用户注册 */
    @PostMapping("/register")
    public R<String> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.username(), request.password(), request.nickname());
    }

    /** 用户登录，返回 JWT Token */
    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }
}
