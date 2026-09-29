package org.cloudback.common.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.service.TokenBlacklistService;
import org.cloudback.common.utils.JwtUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 黑名单过滤器：校验请求 Token 是否已被吊销（如用户被封禁）。
 * 无 Authorization 头的公开接口直接放行。
 *
 * @author CloudBack
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtBlacklistFilter extends OncePerRequestFilter {

    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(SystemConstants.TOKEN_HEADER);
        if (header == null || !header.startsWith(SystemConstants.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(SystemConstants.TOKEN_PREFIX.length());
        Claims claims = JwtUtil.parseToken(token);
        if (claims != null) {
            String username = claims.get("username", String.class);
            Date issuedAt = claims.getIssuedAt();
            if (username != null && issuedAt != null
                    && tokenBlacklistService.isBlacklisted(username, issuedAt.getTime() / 1000)) {
                log.warn("已吊销的 Token 被拦截: username={}", username);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"code\":401,\"message\":\"登录状态已失效，请重新登录\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
