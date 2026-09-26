package org.cloudback.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.service.OutboxService;
import org.cloudback.order.feign.*;
import org.cloudback.order.mapper.OrderItemMapper;
import org.cloudback.order.mapper.OrderMapper;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.model.entity.OrderItem;
import org.cloudback.order.service.OrderRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceRegressionTest {
    @Mock OrderMapper orderMapper;
    @Mock OrderItemMapper orderItemMapper;
    @Mock CartFeignClient cartFeignClient;
    @Mock ProductFeignClient productFeignClient;
    @Mock UserFeignClient userFeignClient;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock OutboxService outboxService;
    @Mock PlatformTransactionManager transactionManager;
    @Mock OrderRequestService orderRequests;
    @InjectMocks OrderServiceImpl service;

    @BeforeEach
    void initializeMapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Order.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), OrderItem.class);
        lenient().when(orderRequests.create(anyLong(), anyString(), any(), any())).thenAnswer(invocation -> {
            Function<String, Order> prepare = invocation.getArgument(2);
            Consumer<Order> execute = invocation.getArgument(3);
            Order order = prepare.apply("order-request-1");
            execute.accept(order);
            return order;
        });
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void committedOrderDoesNotRestoreStock() {
        prepareValidCheckout();
        when(productFeignClient.deductStock(eq(11L), eq(1), anyString(), any())).thenReturn(R.ok("deducted"));
        service.createOrder(7L, 9L, "", "token");
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(productFeignClient, never()).restoreStock(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void cancellationPersistsCompensationInsteadOfCallingRemoteStock() {
        TransactionSynchronizationManager.initSynchronization();
        Order order = new Order();
        order.setId(1L);
        order.setUserId(7L);
        order.setOrderNo("order-1");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        OrderItem item = item(11L);
        item.setQuantity(2);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        assertEquals(200, service.cancelOrder(7L, 1L).getCode());
        verify(outboxService).saveMessage(eq(SystemConstants.KAFKA_TOPIC_STOCK_RESTORE), eq("order-1:11"), argThat(payload ->
                payload.contains("\"orderNo\":\"order-1\"") && payload.contains("\"quantity\":2")));
        verifyNoInteractions(productFeignClient, redisTemplate);
    }

    @Test
    void cancellationOutboxFailurePropagatesForTransactionRollback() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(7L);
        order.setOrderNo("order-1");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        OrderItem item = item(11L);
        item.setQuantity(2);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));
        doThrow(new IllegalStateException("database unavailable"))
                .when(outboxService).saveMessage(anyString(), anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> service.cancelOrder(7L, 1L));
        verifyNoInteractions(productFeignClient, redisTemplate);
    }

    private void prepareValidCheckout() {
        prepareCheckout();
        when(userFeignClient.getAddressById(7L, 9L)).thenReturn(R.ok(Map.of(
                "userId", 7L, "receiverName", "Buyer", "phone", "13800000000", "detail", "Street")));
        TransactionSynchronizationManager.initSynchronization();
    }

    private void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }

    @Test
    void missingAddressStopsBeforeStockAndPersistence() {
        prepareCheckout();
        assertThrows(BusinessException.class, () -> service.createOrder(7L, null, "", "token"));
        verifyNoInteractions(userFeignClient);
        verifyNoCheckoutWrites();
    }

    @Test
    void nonexistentAddressStopsBeforeStockAndPersistence() {
        prepareCheckout();
        when(userFeignClient.getAddressById(7L, 9L)).thenReturn(R.fail("地址不存在"));
        assertThrows(BusinessException.class, () -> service.createOrder(7L, 9L, "", "token"));
        verifyNoCheckoutWrites();
    }

    @Test
    void anotherUsersAddressStopsBeforeStockAndPersistence() {
        prepareCheckout();
        when(userFeignClient.getAddressById(7L, 9L)).thenReturn(R.ok(Map.of(
                "userId", 8L, "receiverName", "Buyer", "phone", "13800000000", "detail", "Street")));
        assertThrows(BusinessException.class, () -> service.createOrder(7L, 9L, "", "token"));
        verifyNoCheckoutWrites();
    }

    @Test
    void addressServiceFailureStopsBeforeStockAndPersistence() {
        prepareCheckout();
        when(userFeignClient.getAddressById(7L, 9L)).thenThrow(new IllegalStateException("offline"));
        assertThrows(BusinessException.class, () -> service.createOrder(7L, 9L, "", "token"));
        verifyNoCheckoutWrites();
    }

    @Test
    void mixedSellerOrderCannotBeShippedAsWhole() {
        prepareShipment(List.of(item(11L), item(12L)));
        assertThrows(BusinessException.class, () -> service.shipOrder(3L, 1L));
        verify(orderMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void emptyOrderCannotBeShipped() {
        prepareShipment(List.of());
        assertThrows(BusinessException.class, () -> service.shipOrder(3L, 1L));
        verify(orderMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void singleSellerOrderCanBeShippedWithPaidStateGuard() {
        prepareShipment(List.of(item(11L)));
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<Order> update = invocation.getArgument(1);
            assertTrue(update.getSqlSegment().contains("status ="));
            assertTrue(update.getSqlSegment().contains("id ="));
            assertTrue(update.getSqlSet().contains("status="));
            assertTrue(update.getParamNameValuePairs().containsValue(SystemConstants.ORDER_STATUS_PAID));
            assertTrue(update.getParamNameValuePairs().containsValue(SystemConstants.ORDER_STATUS_SHIPPED));
            return 1;
        });
        assertEquals(200, service.shipOrder(3L, 1L).getCode());
    }

    @Test
    void shipmentLosingStateRaceIsRejected() {
        prepareShipment(List.of(item(11L)));
        when(orderMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.shipOrder(3L, 1L));
    }

    @Test
    void orderNumberLookupIncludesCurrentUserInDatabasePredicate() {
        Order own = new Order();
        own.setUserId(7L);
        own.setOrderNo("order-123");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<Order> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("user_id ="));
            assertTrue(query.getSqlSegment().contains("order_no ="));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
            assertTrue(query.getParamNameValuePairs().containsValue("order-123"));
            return own;
        });
        assertSame(own, service.getOrderByNo(7L, "order-123").getData());
    }

    @Test
    void orderNumberOutsideCurrentUserReturnsNotFound() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getOrderByNo(7L, "another-users-order"));
    }

    @Test
    void invalidOrderLookupDoesNotQueryDatabase() {
        assertThrows(BusinessException.class, () -> service.getOrderByNo(null, "order-123"));
        assertThrows(BusinessException.class, () -> service.getOrderByNo(7L, " "));
        verifyNoInteractions(orderMapper);
    }

    private void prepareCheckout() {
        CartItemDTO cart = new CartItemDTO();
        cart.setProductId(11L);
        cart.setQuantity(1);
        when(cartFeignClient.getCheckedItems(7L)).thenReturn(R.ok(List.of(cart)));
        ProductDTO product = product(11L);
        product.setName("Product");
        product.setPrice(BigDecimal.TEN);
        when(productFeignClient.getProductDetail(11L)).thenReturn(R.ok(product));
    }

    private void verifyNoCheckoutWrites() {
        verify(productFeignClient, never()).deductStock(anyLong(), anyInt(), any(), any());
        verify(productFeignClient, never()).restoreStock(anyLong(), anyInt(), any(), any());
        verifyNoInteractions(orderMapper, orderItemMapper, outboxService);
    }

    private void prepareShipment(List<OrderItem> items) {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(SystemConstants.ORDER_STATUS_PAID);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(items);
        when(productFeignClient.getProductsBySellerId(3L)).thenReturn(R.ok(List.of(product(11L))));
    }

    private static ProductDTO product(Long id) {
        ProductDTO product = new ProductDTO();
        product.setId(id);
        return product;
    }

    private static OrderItem item(Long productId) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        return item;
    }
}
