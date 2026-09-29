package org.cloudback.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部认证过滤器：所有下游服务只信任网关注入的内部 Token。
 * 未配置 cloud.internal.token 时放行并告警（避免未配置环境全挂）。
 * 仅在 Servlet 应用中生效，WebFlux 网关不会实例化本过滤器。
 *
 * @author CloudBack
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InternalAuthFilter extends OncePerRequestFilter {

    @Value("${cloud.internal.token:}")
    private String internalToken;

    private volatile boolean warned = false;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (internalToken == null || internalToken.isBlank()) {
            if (!warned) {
                warned = true;
                log.warn("cloud.internal.token 未配置，内部认证过滤器处于放行状态，请在 .env 中配置 CLOUD_INTERNAL_TOKEN");
            }
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(SystemConstants.INTERNAL_TOKEN_HEADER);
        if (header != null && MessageDigest.isEqual(
                header.getBytes(StandardCharsets.UTF_8),
                internalToken.getBytes(StandardCharsets.UTF_8))) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("非法的内部请求被拦截: {} {}", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":403,\"message\":\"非法的内部请求\"}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
