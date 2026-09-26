package org.cloudback.order.service;

import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.order.feign.ProductFeignClient;
import org.cloudback.order.mapper.OrderMapper;
import org.cloudback.order.mapper.OrderRequestMapper;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.model.entity.OrderItem;
import org.cloudback.order.model.entity.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRequestServiceTest {
    @Mock OrderRequestMapper requests;
    @Mock OrderMapper orders;
    @Mock ProductFeignClient products;
    @Mock PlatformTransactionManager transactionManager;

    private final AtomicReference<OrderRequest> stored = new AtomicReference<>();
    private OrderRequestService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        lenient().when(requests.find(anyString())).thenAnswer(invocation -> tokenIs(invocation.getArgument(0)) ? stored.get() : null);
        lenient().when(requests.lock(anyString())).thenAnswer(invocation -> tokenIs(invocation.getArgument(0)) ? stored.get() : null);
        lenient().when(requests.update(any())).thenReturn(1);
        service = new OrderRequestService(requests, orders, products, transactionManager);
        ReflectionTestUtils.setField(service, "internalToken", "internal");
    }

    @Test
    void validationFailureKeepsTheSameReadyRequestReusable() {
        stored.set(request("READY"));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(7L, "token",
                orderNo -> { throw new BusinessException("地址无效"); }, ignored -> fail("must not execute")));

        assertEquals(42201, failure.getCode());
        assertEquals("READY", stored.get().getStatus());
        verify(requests, never()).update(any());
        verifyNoInteractions(products);
    }

    @Test
    void unknownExecutionOutcomeRestoresSnapshotAndEndsAsFailed() {
        stored.set(request("READY"));
        when(products.restoreStock(11L, 2, "order-1", "internal")).thenReturn(R.ok("restored"));

        assertThrows(BusinessException.class, () -> service.create(7L, "token", this::snapshot,
                ignored -> { throw new IllegalStateException("response lost"); }));

        assertEquals("FAILED", stored.get().getStatus());
        assertNull(stored.get().getActiveUserId());
        verify(products).restoreStock(11L, 2, "order-1", "internal");
    }

    @Test
    void failedRestoreRemainsCompensatingAndKeepsUserBlocked() {
        stored.set(request("READY"));
        when(products.restoreStock(11L, 2, "order-1", "internal")).thenReturn(R.fail("offline"));

        assertThrows(BusinessException.class, () -> service.create(7L, "token", this::snapshot,
                ignored -> { throw new IllegalStateException("response lost"); }));

        assertEquals("COMPENSATING", stored.get().getStatus());
        assertEquals(7L, stored.get().getActiveUserId());
    }

    @Test
    void succeededRequestReplaysOrderWithoutExecutingAgain() {
        stored.set(request("READY"));
        Order snapshot = snapshot("order-1");
        when(orders.selectOne(any())).thenReturn(snapshot);
        AtomicInteger executions = new AtomicInteger();

        Order first = service.create(7L, "token", ignored -> snapshot, order -> executions.incrementAndGet());
        Order replay = service.create(7L, "token", ignored -> fail("must not prepare again"),
                order -> fail("must not execute again"));

        assertEquals("order-1", first.getOrderNo());
        assertSame(snapshot, replay);
        assertEquals(1, executions.get());
        assertEquals("SUCCEEDED", stored.get().getStatus());
    }

    @Test
    void staleProcessingRequestIsRecoveredByScheduler() {
        OrderRequest request = request("PROCESSING");
        request.setSnapshot(com.alibaba.fastjson2.JSON.toJSONString(snapshot("order-1")));
        request.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        stored.set(request);
        when(requests.recoverable(any(), any())).thenReturn(List.of("token"));
        when(products.restoreStock(11L, 2, "order-1", "internal")).thenReturn(R.ok("restored"));

        service.recover();

        assertEquals("FAILED", stored.get().getStatus());
        assertNull(stored.get().getActiveUserId());
    }

    @Test
    void lockedReadyRowIsReportedAsProcessingInsteadOfSafeToResubmit() {
        OrderRequest visible = request("READY");
        stored.set(visible);
        when(requests.lock("token")).thenReturn(null);

        var result = service.result(7L, "token");

        assertEquals("PROCESSING", result.status());
        verify(requests, never()).update(any());
    }

    @Test
    void expiredReadyRequestIsPersistedAndReleasesTheUser() {
        OrderRequest expired = request("READY");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        stored.set(expired);

        var result = service.result(7L, "token");

        assertEquals("EXPIRED", result.status());
        assertNull(stored.get().getActiveUserId());
        verify(requests).update(expired);
    }

    @Test
    void issuingDoesNotReturnAnExpiredRequestLockedByAnotherWorker() {
        OrderRequest expired = request("READY");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        stored.set(expired);
        when(requests.active(7L)).thenReturn(expired);
        when(requests.lock("token")).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.issue(7L));

        assertEquals("下单请求正在创建，请稍后重试", failure.getMessage());
        verify(requests, times(3)).active(7L);
        verify(requests, never()).insert(any());
    }

    private boolean tokenIs(String token) {
        return stored.get() != null && stored.get().getToken().equals(token);
    }

    private static OrderRequest request(String status) {
        OrderRequest request = new OrderRequest();
        request.setToken("token");
        request.setUserId(7L);
        request.setActiveUserId(7L);
        request.setOrderNo("order-1");
        request.setStatus(status);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        request.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return request;
    }

    private Order snapshot(String orderNo) {
        Order order = new Order();
        order.setUserId(7L);
        order.setOrderNo(orderNo);
        OrderItem item = new OrderItem();
        item.setProductId(11L);
        item.setQuantity(2);
        order.setOrderItems(List.of(item));
        return order;
    }
}
