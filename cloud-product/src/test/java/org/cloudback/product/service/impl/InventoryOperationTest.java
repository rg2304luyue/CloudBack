package org.cloudback.product.service.impl;

import org.cloudback.common.exception.BusinessException;
import org.cloudback.product.mapper.InventoryOperationMapper;
import org.cloudback.product.mapper.ProductMapper;
import org.cloudback.product.model.entity.InventoryOperation;
import org.cloudback.common.config.TwoLevelCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InventoryOperationTest {
    private ProductMapper products;
    private InventoryOperationMapper operations;
    private ProductServiceImpl service;

    @BeforeEach
    void setup() {
        products = mock(ProductMapper.class);
        operations = mock(InventoryOperationMapper.class);
        service = new ProductServiceImpl(products, operations, null,
                mock(TwoLevelCacheService.class), mock(TwoLevelCacheService.class),
                null, null, null, null);
    }

    private void state(String state, int quantity) {
        InventoryOperation op = new InventoryOperation();
        op.setQuantity(quantity);
        op.setState(state);
        when(operations.lock("o1", 1L)).thenReturn(op);
    }

    @Test void firstDeductWritesReserved() {
        state("EMPTY", 2);
        when(products.deductStockAtomically(1L, 2)).thenReturn(1);
        service.deductStock(1L, 2, "o1");
        var order = inOrder(operations, products);
        order.verify(operations).ensureExists("o1", 1L, 2);
        order.verify(operations).lock("o1", 1L);
        order.verify(products).deductStockAtomically(1L, 2);
        order.verify(operations).setState("o1", 1L, "RESERVED");
    }

    @Test void duplicateDeductDoesNotChangeStock() {
        state("RESERVED", 2);
        service.deductStock(1L, 2, "o1");
        verifyNoInteractions(products);
    }

    @Test void restoredKeyRejectsLateDeduct() {
        state("RESTORED", 2);
        assertThrows(BusinessException.class, () -> service.deductStock(1L, 2, "o1"));
        verifyNoInteractions(products);
    }

    @Test void restoreBeforeDeductClosesWithoutIncreasingStock() {
        state("EMPTY", 2);
        service.restoreStock(1L, 2, "o1");
        verifyNoInteractions(products);
        verify(operations).setState("o1", 1L, "RESTORED");
    }

    @Test void duplicateRestoreDoesNotChangeStock() {
        state("RESTORED", 2);
        service.restoreStock(1L, 2, "o1");
        verifyNoInteractions(products);
        verify(operations, never()).setState(anyString(), anyLong(), anyString());
    }

    @Test void reservedRestoreChangesStockThenState() {
        state("RESERVED", 2);
        when(products.restoreStock(1L, 2)).thenReturn(1);
        service.restoreStock(1L, 2, "o1");
        var order = inOrder(products, operations);
        order.verify(products).restoreStock(1L, 2);
        order.verify(operations).setState("o1", 1L, "RESTORED");
    }

    @Test void quantityMismatchRejectedForBothOperations() {
        state("RESERVED", 3);
        assertThrows(BusinessException.class, () -> service.deductStock(1L, 2, "o1"));
        assertThrows(BusinessException.class, () -> service.restoreStock(1L, 2, "o1"));
        verifyNoInteractions(products);
    }

    @Test void failedRestoreDoesNotCloseReservedRecord() {
        state("RESERVED", 2);
        assertThrows(BusinessException.class, () -> service.restoreStock(1L, 2, "o1"));
        verify(operations, never()).setState(anyString(), anyLong(), anyString());
    }

    @Test void invalidInputRejectedBeforeDatabase() {
        assertThrows(BusinessException.class, () -> service.deductStock(1L, 0, "o1"));
        assertThrows(BusinessException.class, () -> service.restoreStock(1L, 2, null));
        verifyNoInteractions(products, operations);
    }

    @Test void failedDeductDoesNotMarkReserved() {
        state("EMPTY", 2);
        assertThrows(BusinessException.class, () -> service.deductStock(1L, 2, "o1"));
        verify(operations, never()).setState(anyString(), anyLong(), anyString());
    }

    @Test void ledgerWriteFailurePropagatesForTransactionRollback() {
        state("EMPTY", 2);
        when(products.deductStockAtomically(1L, 2)).thenReturn(1);
        when(operations.setState("o1", 1L, "RESERVED")).thenThrow(new IllegalStateException("db failure"));
        assertThrows(IllegalStateException.class, () -> service.deductStock(1L, 2, "o1"));
    }
}
