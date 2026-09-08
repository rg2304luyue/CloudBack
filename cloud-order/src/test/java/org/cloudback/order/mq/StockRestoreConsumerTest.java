package org.cloudback.order.mq;

import org.cloudback.common.result.R;
import org.cloudback.order.feign.ProductFeignClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockRestoreConsumerTest {
    private final ProductFeignClient products = mock(ProductFeignClient.class);
    private final StockRestoreConsumer consumer = new StockRestoreConsumer(products);
    private static final String MESSAGE = "{\"orderNo\":\"o1\",\"productId\":11,\"quantity\":2,\"idempotencyKey\":\"o1:11\"}";

    @Test
    void redeliveryUsesSameDurableBusinessKey() {
        when(products.restoreStock(eq(11L), eq(2), eq("o1"), any())).thenReturn(R.ok("done"));
        consumer.onStockRestore(MESSAGE);
        consumer.onStockRestore(MESSAGE);
        verify(products, times(2)).restoreStock(eq(11L), eq(2), eq("o1"), any());
    }

    @Test
    void businessFailureIsRetried() {
        when(products.restoreStock(eq(11L), eq(2), eq("o1"), any())).thenReturn(R.fail("unavailable"));
        assertThrows(IllegalStateException.class, () -> consumer.onStockRestore(MESSAGE));
    }

    @Test
    void mismatchedBusinessKeyCannotChangeStock() {
        consumer.onStockRestore(MESSAGE.replace("o1:11", "other:11"));
        verifyNoInteractions(products);
    }
}
