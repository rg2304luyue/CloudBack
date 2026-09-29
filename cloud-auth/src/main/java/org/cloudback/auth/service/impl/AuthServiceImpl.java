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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    /** 连续登录失败达到该次数后锁定 */
    private static final int MAX_LOGIN_FAIL = 5;
    /** 登录失败计数的锁定时长（分钟） */
    private static final long LOGIN_LOCK_MINUTES = 15;

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> register(String username, String password, String nickname) {
        // 输入校验
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        if (password.length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }

        username = username.trim();

        User existUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (existUser != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXIST);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname != null && !nickname.isBlank() ? nickname : username);
        user.setStatus(SystemConstants.USER_STATUS_NORMAL);
        user.setRole(SystemConstants.ROLE_BUYER);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXIST);
        }

        log.info("用户注册成功: username={}", username);
        return R.ok("注册成功");
    }

    @Override
    public R<String> login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        username = username.trim();
        String failKey = SystemConstants.LOGIN_FAIL_KEY + username;
        checkLoginAllowed(failKey, username);
        log.info("登录请求: username={}", username);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        // 用户不存在与密码错误统一提示，防止用户名枚举
        if (user == null) {
            recordLoginFail(failKey, username);
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            recordLoginFail(failKey, username);
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // fail-safe: 只有显式 NORMAL 状态才允许登录
        if (!SystemConstants.USER_STATUS_NORMAL.equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }

        clearLoginFail(failKey);

        String role = user.getRole();
        if (role == null) {
            log.warn("用户角色为空, username={}, 默认使用BUYER", username);
            role = SystemConstants.ROLE_BUYER;
        }

        String token = JwtUtil.createToken(user.getId(), user.getUsername(),
                Map.of("role", role));
        log.info("用户登录成功: username={}", username);
        return R.ok("登录成功", token);
    }

    /** 校验登录失败次数，达到上限则拒绝（Redis 故障时放行，不阻断登录） */
    private void checkLoginAllowed(String failKey, String username) {
        try {
            String value = stringRedisTemplate.opsForValue().get(failKey);
            if (value != null && Integer.parseInt(value) >= MAX_LOGIN_FAIL) {
                throw new BusinessException("失败次数过多，请15分钟后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取登录失败计数失败，放行本次登录: username={}, error={}", username, e.getMessage());
        }
    }

    /** 记录一次登录失败：每次失败都续期，使其变为真正的滑动窗口（Redis 故障仅记日志） */
    private void recordLoginFail(String failKey, String username) {
        try {
            stringRedisTemplate.opsForValue().increment(failKey);
            stringRedisTemplate.expire(failKey, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("记录登录失败计数失败: username={}, error={}", username, e.getMessage());
        }
    }

    /** 登录成功后清除失败计数（Redis 故障仅记日志） */
    private void clearLoginFail(String failKey) {
        try {
            stringRedisTemplate.delete(failKey);
        } catch (Exception e) {
            log.warn("清除登录失败计数失败: key={}, error={}", failKey, e.getMessage());
        }
    }
}
