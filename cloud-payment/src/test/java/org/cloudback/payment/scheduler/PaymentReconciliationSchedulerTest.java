package org.cloudback.payment.scheduler;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.result.R;
import org.cloudback.payment.feign.OrderFeignClient;
import org.cloudback.payment.mapper.PaymentMapper;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.common.service.OutboxService;
import org.cloudback.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentReconciliationSchedulerTest {
    private final PaymentMapper mapper = mock(PaymentMapper.class);
    private final PaymentService service = mock(PaymentService.class);
    private final OrderFeignClient orderFeignClient = mock(OrderFeignClient.class);
    private final OutboxService outboxService = mock(OutboxService.class);
    private final PaymentReconciliationScheduler scheduler =
            new PaymentReconciliationScheduler(mapper, service, orderFeignClient, outboxService);

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

    @Test
    void reverseScanBoundsClosedPaymentsByAgeButAlwaysCoversPaidOnes() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        scheduler.reconcilePaidPayments();

        ArgumentCaptor<LambdaQueryWrapper<Payment>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // 已支付无条件纳入；已关闭/已失败只在 24 小时窗口内复核，否则历史关闭单会被永久反复查单
        assertTrue(sql.contains("status ="), sql);
        assertTrue(sql.contains("status IN"), sql);
        assertTrue(sql.contains("create_time >="), sql);
        assertTrue(sql.indexOf("(status =") >= 0, "状态条件必须整体加括号: " + sql);
    }

    @Test
    void closedPaymentOnUnpaidOrderIsRecheckedInsteadOfBlindResend() {
        // 支付单停在 CLOSED 而订单仍待支付：不能凭空补发「已支付」，要触发一次状态复核
        Payment closed = payment(1L);
        closed.setStatus(SystemConstants.PAYMENT_STATUS_CLOSED);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(closed));
        when(mapper.claimPaidSync(eq(1L), any(), any())).thenReturn(1);
        when(orderFeignClient.getOrderStatus("order-1")).thenAnswer(invocation -> {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("status", SystemConstants.ORDER_STATUS_UNPAID);
            return R.ok(data);
        });

        scheduler.reconcilePaidPayments();

        verify(service).syncPaymentStatus("order-1", null);
        verify(outboxService, never()).saveMessage(anyString(), anyString(), anyString());
    }

    @Test
    void paidPaymentOnUnpaidOrderIsResentThroughOutbox() {
        Payment paid = payment(1L);
        paid.setStatus(SystemConstants.PAYMENT_STATUS_PAID);
        paid.setTradeNo("trade-1");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(paid));
        when(mapper.claimPaidSync(eq(1L), any(), any())).thenReturn(1);
        when(orderFeignClient.getOrderStatus("order-1")).thenAnswer(invocation -> {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("status", SystemConstants.ORDER_STATUS_UNPAID);
            return R.ok(data);
        });

        scheduler.reconcilePaidPayments();

        verify(outboxService).saveMessage(eq(SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT), eq("order-1"), anyString());
        verify(service, never()).syncPaymentStatus(anyString(), any());
    }

    private Payment payment(long id) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrderNo("order-" + id);
        return payment;
    }
}
