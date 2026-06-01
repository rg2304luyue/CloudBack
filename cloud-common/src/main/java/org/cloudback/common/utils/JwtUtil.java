package org.cloudback.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类，提供 Token 的创建、解析、校验功能。
 * Token 有效期 2 小时，签名算法 HMAC-SHA256。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
public class JwtUtil {

    /** HMAC-SHA256 密钥，通过 JwtSecretConfig 从环境变量注入 */
    private static String SECRET;

    /** Token 有效期: 2 小时 */
    private static final long EXPIRE_SECONDS = 7200L;

    /** 由 Spring 配置类调用，注入 JWT 密钥。启动时即校验，配置缺失立即报错而非运行时 500。 */
    public static void setSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 环境变量未设置，请在 .env 中配置");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足，至少需要32字符以保证安全");
        }
        SECRET = secret;
    }

    private static SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /** 创建 Token（空额外参数） */
    public static String createToken(Long userId, String username, Map<String, Object> extra) {
        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_SECONDS * 1000))
                .signWith(getSecretKey());

        if (extra != null && !extra.isEmpty()) {
            extra.forEach(builder::claim);
        }
        return builder.compact();
    }

    /** 创建 Token（无额外参数） */
    public static String createToken(Long userId, String username) {
        return createToken(userId, username, null);
    }

    /** 解析 Token，返回载荷。过期或无效返回 null */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.warn("Token无效: {}", e.getMessage());
            return null;
        }
    }

    /** 从 Token 中提取用户 ID */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return Long.valueOf(claims.getSubject());
    }

    /** 从 Token 中提取用户名 */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return claims.get("username", String.class);
    }

    /** 从 Token 中提取用户角色 */
    public static String getRole(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return claims.get("role", String.class);
    }

    /** 判断 Token 是否已过期 */
    public static boolean isTokenExpired(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return true;
        return claims.getExpiration().before(new Date());
    }
}
