package org.cloudback.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.auth.service.AuthService;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.entity.User;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.mapper.UserMapper;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.common.utils.JwtUtil;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;

    @Override
    public R<String> register(String username, String password, String nickname) {
        // 1. 检查用户名是否已存在
        User existUser = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );
        if (existUser != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXIST);
        }

        // 2. 创建用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password));  // BCrypt加密密码
        user.setNickname(nickname != null ? nickname : username);
        user.setStatus(SystemConstants.USER_STATUS_NORMAL);

        userMapper.insert(user);
        log.info("用户注册成功: username={}", username);
        return R.ok("注册成功");
    }

    @Override
    public R<String> login(String username, String password) {
        // 1. 查询用户
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        // 2. 校验密码
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 3. 检查用户状态
        if (user.getStatus().equals(SystemConstants.USER_STATUS_DISABLED)) {
            throw new BusinessException("账号已被禁用");
        }

        // 4. 签发JWT
        String token = JwtUtil.createToken(user.getId(), user.getUsername());
        log.info("用户登录成功: username={}", username);
        return R.ok("登录成功", token);
    }
}
