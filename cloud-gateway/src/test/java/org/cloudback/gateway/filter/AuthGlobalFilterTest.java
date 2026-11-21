package org.cloudback.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** 网关是唯一的外部边界：库存接口与 /internal/ 路径必须对任何外部用户 403。 */
class AuthGlobalFilterTest {

    private AuthGlobalFilter filter;
    private final AtomicBoolean forwarded = new AtomicBoolean();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(true);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        filter = new AuthGlobalFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "internal-token");
        forwarded.set(false);
    }

    @Test
    void stockDeductIsForbiddenForExternalCallers() {
        assertEquals(HttpStatus.FORBIDDEN,
                status(MockServerHttpRequest.post("/api/products/1/stock/deduct")));
        assertFalse(forwarded.get());
    }

    @Test
    void stockRestoreIsForbiddenWithTrailingSlashOrApiPrefixTricks() {
        assertEquals(HttpStatus.FORBIDDEN,
                status(MockServerHttpRequest.post("/api/products/1/stock/restore/")));
        assertEquals(HttpStatus.FORBIDDEN,
                status(MockServerHttpRequest.post("/api//products/1/stock/restore")));
        assertFalse(forwarded.get());
    }

    @Test
    void sellerLookupIsForbiddenForExternalCallers() {
        assertEquals(HttpStatus.FORBIDDEN,
                status(MockServerHttpRequest.get("/api/products/seller?sellerId=1")));
        assertFalse(forwarded.get());
    }

    @Test
    void internalPathIsForbiddenAnywhereInThePath() {
        assertEquals(HttpStatus.FORBIDDEN, status(MockServerHttpRequest.get("/api/orders/internal/1/status")));
        assertFalse(forwarded.get());
    }

    @Test
    void publicProductDetailStillPasses() {
        // 放行分支不写状态码（由下游决定），因此断言"已转发"而不是 200
        assertNull(status(MockServerHttpRequest.get("/api/products/12")));
        assertTrue(forwarded.get());
    }

    @Test
    void nonAsciiDigitsAreNotTreatedAsAProductId() {
        // 全角数字不是合法路径参数：不能进白名单，而是按未登录处理
        assertEquals(HttpStatus.UNAUTHORIZED, status(MockServerHttpRequest.get("/api/products/１２")));
    }

    private HttpStatus status(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        filter.filter(exchange, chain).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }
}