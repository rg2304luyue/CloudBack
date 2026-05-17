package org.cloudback.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
public class JwtUtil {

    private static final String SECRET = "CloudBack2026SecretKeyForJwtTokenGenerationMustBeLongEnough";
    private static final long EXPIRE_SECONDS = 7200L; // 2小时

    private static SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

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

    public static String createToken(Long userId, String username) {
        return createToken(userId, username, null);
    }

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

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return Long.valueOf(claims.getSubject());
    }

    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        return claims.get("username", String.class);
    }

    public static boolean isTokenExpired(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return true;
        return claims.getExpiration().before(new Date());
    }
}
