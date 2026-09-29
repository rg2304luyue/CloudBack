package org.cloudback.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cloud.internal.token:}")
    private String internalToken;

    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            PublicRoute.exact(HttpMethod.POST, "/auth/login"),
            PublicRoute.exact(HttpMethod.POST, "/auth/register"),
            PublicRoute.exact(HttpMethod.GET, "/auth/health"),
            PublicRoute.exact(HttpMethod.POST, "/payment/notify/alipay"),
            PublicRoute.exact(HttpMethod.GET, "/payment/return/alipay"),
            PublicRoute.exact(HttpMethod.GET, "/products"),
            PublicRoute.pattern(HttpMethod.GET, "/products/{id}"),
            PublicRoute.exact(HttpMethod.GET, "/products/hot"),
            PublicRoute.exact(HttpMethod.GET, "/products/search"),
            PublicRoute.exact(HttpMethod.GET, "/products/suggest"),
            PublicRoute.exact(HttpMethod.GET, "/categories")
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest originalRequest = exchange.getRequest();
        String path = normalizePath(originalRequest.getURI().getPath());
        HttpMethod method = originalRequest.getMethod();

        // internal/* 只允许服务间调用：网关注入 X-Internal-Token 后，任何登录用户都会通过校验，
        // 因此必须在网关层直接拦截外部请求，避免任意用户查询他人订单状态/批量拉取商品。
        if (isInternalPath(path)) {
            return forbidden(exchange, "该路径不允许外部访问");
        }

        if (isPublic(method, path)) {
            ServerHttpRequest request = withInternalToken(stripTrustedHeaders(originalRequest)).build();
            return chain.filter(exchange.mutate().request(request).build());
        }

        String token = originalRequest.getHeaders().getFirst(SystemConstants.TOKEN_HEADER);
        if (token == null || !token.startsWith(SystemConstants.TOKEN_PREFIX)) {
            return unauthorized(exchange, "未登录或Token已过期");
        }

        token = token.substring(SystemConstants.TOKEN_PREFIX.length());
        Claims claims = JwtUtil.parseToken(token);
        if (claims == null) {
            return unauthorized(exchange, "Token已过期，请重新登录");
        }
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        if (role == null) role = SystemConstants.ROLE_BUYER;
        if (claims.getExpiration().before(new Date())) {
            return unauthorized(exchange, "Token已过期，请重新登录");
        }

        ServerHttpRequest request = withInternalToken(stripTrustedHeaders(originalRequest))
                .header("X-User-Id", String.valueOf(userId))
                .header("X-Username", username)
                .header(SystemConstants.USER_ROLE_HEADER, role)
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    /** 为转发请求注入内部 Token（所有下游服务只信任携带该头的请求） */
    private ServerHttpRequest.Builder withInternalToken(ServerHttpRequest.Builder builder) {
        if (internalToken != null && !internalToken.isBlank()) {
            builder.header(SystemConstants.INTERNAL_TOKEN_HEADER, internalToken);
        }
        return builder;
    }

    private ServerHttpRequest.Builder stripTrustedHeaders(ServerHttpRequest request) {
        return request.mutate().headers(headers -> {
            headers.remove("X-User-Id");
            headers.remove("X-Username");
            headers.remove(SystemConstants.USER_ROLE_HEADER);
            headers.remove(SystemConstants.INTERNAL_TOKEN_HEADER);
        });
    }

    private boolean isInternalPath(String path) {
        return path != null && (path.startsWith("/internal/") || path.contains("/internal/"));
    }

    private boolean isPublic(HttpMethod method, String path) {
        return PUBLIC_ROUTES.stream().anyMatch(route -> route.matches(method, path));
    }

    private String normalizePath(String path) {
        if (path != null && path.startsWith("/api/")) {
            return path.substring(4);
        }
        return path == null ? "" : path;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("code", status.value());
        bodyMap.put("message", message);
        bodyMap.put("data", null);
        byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(bodyMap);
        } catch (Exception e) {
            bodyBytes = "{\"code\":403,\"message\":\"禁止访问\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bodyBytes);
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.FORBIDDEN, message);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, message);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private record PublicRoute(HttpMethod method, String path, boolean singleSegmentWildcard) {
        static PublicRoute exact(HttpMethod method, String path) {
            return new PublicRoute(method, path, false);
        }

        static PublicRoute pattern(HttpMethod method, String path) {
            return new PublicRoute(method, path, true);
        }

        boolean matches(HttpMethod requestMethod, String requestPath) {
            if (!method.equals(requestMethod)) return false;
            if (!singleSegmentWildcard) return path.equals(requestPath);
            String prefix = path.substring(0, path.indexOf("{id}"));
            if (!requestPath.startsWith(prefix)) return false;
            String tail = requestPath.substring(prefix.length());
            return !tail.isBlank() && !tail.contains("/") && tail.chars().allMatch(Character::isDigit);
        }
    }
}

