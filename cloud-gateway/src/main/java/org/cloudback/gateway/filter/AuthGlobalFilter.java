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
import java.util.regex.Pattern;

@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cloud.internal.token:}")
    private String internalToken;

    /** 内部保留的路径段，任何外部请求都不得进入 */
    private static final String INTERNAL_SEGMENT = "internal";

    /** 库存扣减/回补：只允许 order 服务经 Feign 调用，外部用户一律 403 */
    private static final Pattern STOCK_PATH = Pattern.compile("^/products/[0-9]{1,19}/stock/(deduct|restore)$");

    /** 按卖家批量取商品：只允许 order 服务经 Feign 调用 */
    private static final String SELLER_LOOKUP_PATH = "/products/seller";

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

        // 库存扣减/回补与按卖家取商品的路径不含 /internal/ 段，网关注入 X-Internal-Token 后
        // 控制器里的令牌校验对外部登录用户形同虚设（见 README：真正的边界是网关的 403）。
        // 这里在鉴权前直接拦截，服务间调用不经过网关，因此不受影响。
        if (isServiceOnlyPath(path)) {
            return forbidden(exchange, "该接口仅供服务间调用");
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

    /**
     * 内部路径按“路径段”判定：/internal/xxx 或 /xxx/internal/yyy 都算，但 /products/internalized 不算。
     * 逐段比较可避免 contains("/internal/") 被编码、大小写或多余分隔符绕过的歧义。
     */
    private boolean isInternalPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (INTERNAL_SEGMENT.equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /** 只允许服务间调用的业务路径（不含 /internal/ 段，必须单独拦截） */
    private boolean isServiceOnlyPath(String path) {
        return STOCK_PATH.matcher(path).matches() || SELLER_LOOKUP_PATH.equals(path);
    }

    private boolean isPublic(HttpMethod method, String path) {
        return PUBLIC_ROUTES.stream().anyMatch(route -> route.matches(method, path));
    }

    /**
     * 归一化路径后再做任何判定：
     * 去掉网关前缀 /api、折叠重复斜杠、去掉末尾斜杠。
     * 否则 /api//products/1/stock/deduct 或 /products/1/stock/deduct/ 这类写法会绕过
     * 下面的 403 判断，却仍可能被下游控制器匹配到。
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String normalized = path.startsWith("/api/") ? path.substring(4) : path;
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
            // 只接受 ASCII 数字：Character.isDigit 会把阿拉伯-印度数字、全角数字也判为数字，
            // 与下游 Spring MVC 的 long 转换语义不一致。
            return !tail.isEmpty() && tail.chars().allMatch(c -> c >= '0' && c <= '9');
        }
    }
}

