package org.cloudback.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * JWT 黑名单服务：封禁用户时记录封禁时间戳，早于该时间签发的 Token 一律失效。
 * Redis 读取异常时 fail-open（放行并告警），不阻塞正常请求。
 *
 * @author CloudBack
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TokenBlacklistService {

    /** 黑名单 TTL 与 Token 有效期一致（2 小时） */
    private static final Duration BLACKLIST_TTL = Duration.ofHours(2);

    private final StringRedisTemplate stringRedisTemplate;

    /** 封禁用户：以当前 epoch 秒为值，此前签发的所有 Token 立即失效。写失败 fail-open（不回滚改密结果） */
    public void disableUserTokens(String username) {
        try {
            long nowEpochSec = System.currentTimeMillis() / 1000;
            stringRedisTemplate.opsForValue().set(
                    SystemConstants.TOKEN_BLACKLIST_KEY + username,
                    String.valueOf(nowEpochSec),
                    BLACKLIST_TTL);
        } catch (Exception e) {
            log.error("写入 Token 黑名单失败，该用户旧 Token 未吊销: username={}, error={}", username, e.getMessage());
        }
    }

    /** 判断指定时间签发的 Token 是否已被吊销（iat <= 封禁时间戳视为已吊销） */
    public boolean isBlacklisted(String username, long issuedAtEpochSec) {
        try {
            String value = stringRedisTemplate.opsForValue()
                    .get(SystemConstants.TOKEN_BLACKLIST_KEY + username);
            if (value == null) {
                return false;
            }
            return issuedAtEpochSec <= Long.parseLong(value);
        } catch (Exception e) {
            log.warn("读取 Token 黑名单失败，放行请求: username={}, error={}", username, e.getMessage());
            return false;
        }
    }
}
