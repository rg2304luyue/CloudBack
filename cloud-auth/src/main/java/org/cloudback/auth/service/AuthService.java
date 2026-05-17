package org.cloudback.auth.service;

import org.cloudback.common.result.R;

/**
 * 认证服务接口，定义注册和登录方法。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public interface AuthService {

    /** 用户注册，密码使用 BCrypt 加密存储 */
    R<String> register(String username, String password, String nickname);

    /** 用户登录，校验用户名密码后签发 JWT */
    R<String> login(String username, String password);
}
