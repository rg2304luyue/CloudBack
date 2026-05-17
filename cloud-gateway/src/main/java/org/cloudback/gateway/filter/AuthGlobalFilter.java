package org.cloudback.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.utils.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_URLS = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行
        for (String whiteUrl : WHITE_URLS) {
            if (path.contains(whiteUrl)) {
                return chain.filter(exchange);
            }
        }

        // 获取Token
        String token = exchange.getRequest().getHeaders().getFirst(SystemConstants.TOKEN_HEADER);
        if (token == null || !token.startsWith(SystemConstants.TOKEN_PREFIX)) {
            return unauthorized(exchange, "未登录或Token已过期");
        }

        token = token.substring(SystemConstants.TOKEN_PREFIX.length());
        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);

        if (userId == null || JwtUtil.isTokenExpired(token)) {
            return unauthorized(exchange, "Token已过期，请重新登录");
        }

        // 将用户信息写入请求头，传递给下游服务
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-Username", username)
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
