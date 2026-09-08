package org.cloudback.payment.scheduler;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.cloudback.payment.mapper.PaymentMapper;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentReconciliationSchedulerTest {
    private final PaymentMapper mapper = mock(PaymentMapper.class);
    private final PaymentService service = mock(PaymentService.class);
    private final PaymentReconciliationScheduler scheduler = new PaymentReconciliationScheduler(mapper, service);

    @BeforeEach
    void mapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Payment.class);
    }

    @Test
    void oldPendingPaymentsRemainEligibleAndBatchesRotateByLastAttempt() {
        Payment old = payment(1L);
        old.setCreateTime(LocalDateTime.now().minusDays(3));
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<Payment> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertFalse(sql.contains("create_time"));
            assertTrue(sql.contains("status ="));
            assertTrue(sql.contains("last_sync_time IS NULL"));
            assertTrue(sql.contains("ORDER BY last_sync_time ASC,id ASC"));
            assertTrue(sql.contains("LIMIT 20"));
            return List.of(old);
        });
        when(mapper.claimSync(eq(1L), any(), any())).thenReturn(1);
        scheduler.reconcilePendingPayments();
        verify(service).syncPaymentStatus("order-1", null);
    }

    @Test
    void lostClaimDoesNotQueryProvider() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(payment(1L)));
        scheduler.reconcilePendingPayments();
        verifyNoInteractions(service);
    }

    @Test
    void failedPaymentDoesNotPreventOtherPaymentsFromReconciling() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(payment(1L), payment(2L)));
        when(mapper.claimSync(anyLong(), any(), any())).thenReturn(1);
        when(service.syncPaymentStatus("order-1", null)).thenThrow(new IllegalStateException("offline"));
        scheduler.reconcilePendingPayments();
        verify(service).syncPaymentStatus("order-2", null);
    }

    private Payment payment(long id) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrderNo("order-" + id);
        return payment;
    }
}
