package org.cloudback.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

/**
 * 认证服务实现，处理注册和登录逻辑。
 * 注册：校验用户名唯一性 → BCrypt 加密密码 → 入库
 * 登录：查询用户 → 验证密码 → 检查状态 → 签发 JWT
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    @Override
    public R<String> register(String username, String password, String nickname) {
        User existUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (existUser != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXIST);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setStatus(SystemConstants.USER_STATUS_NORMAL);
        user.setRole(SystemConstants.ROLE_BUYER);

        userMapper.insert(user);
        log.info("用户注册成功: username={}", username);
        return R.ok("注册成功");
    }

    @Override
    public R<String> login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        if (user.getStatus().equals(SystemConstants.USER_STATUS_DISABLED)) {
            throw new BusinessException("账号已被禁用");
        }

        String role = user.getRole() != null ? user.getRole() : SystemConstants.ROLE_BUYER;
        String token = JwtUtil.createToken(user.getId(), user.getUsername(),
                java.util.Map.of("role", role));
        log.info("用户登录成功: username={}", username);
        return R.ok("登录成功", token);
    }
}
