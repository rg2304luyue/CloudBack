package org.cloudback.auth.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.auth.service.AuthService;
import org.cloudback.common.result.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public R<String> register(@RequestParam String username,
                              @RequestParam String password,
                              @RequestParam(required = false) String nickname) {
        return authService.register(username, password, nickname);
    }

    @PostMapping("/login")
    public R<String> login(@RequestParam String username,
                           @RequestParam String password) {
        return authService.login(username, password);
    }
}
