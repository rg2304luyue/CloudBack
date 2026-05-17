package org.cloudback.auth.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.auth.service.AuthService;
import org.cloudback.common.result.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public R<String> register(@RequestParam String username,
                              @RequestParam String password,
                              @RequestParam(required = false) String nickname) {
        return authService.register(username, password, nickname);
    }

    /** 用户登录，返回 JWT Token */
    @PostMapping("/login")
    public R<String> login(@RequestParam String username,
                           @RequestParam String password) {
        return authService.login(username, password);
    }
}
