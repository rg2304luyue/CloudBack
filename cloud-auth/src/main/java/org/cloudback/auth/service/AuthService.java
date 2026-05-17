package org.cloudback.auth.service;

import org.cloudback.common.result.R;

public interface AuthService {
    /**
     * 用户注册
     */
    R<String> register(String username, String password, String nickname);

    /**
     * 用户登录，返回JWT token
     */
    R<String> login(String username, String password);
}
