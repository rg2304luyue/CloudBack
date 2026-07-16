package org.cloudback.payment.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.payment.mapper.PaymentMapper;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Reconciles recent pending payments after a lost browser return or Alipay notify. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final int BATCH_SIZE = 20;
    private static final int PAYMENT_TIMEOUT_MINUTES = 30;
    private static final int QUERY_INTERVAL_SECONDS = 30;

    private final PaymentMapper paymentMapper;
    private final PaymentService paymentService;

    @Scheduled(initialDelay = 10000, fixedDelay = QUERY_INTERVAL_SECONDS * 1000L)
    public void reconcilePendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(PAYMENT_TIMEOUT_MINUTES);

        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<Payment>()
                .eq(Payment::getStatus, 0)
                .ge(Payment::getCreateTime, cutoff)
                .and(query -> query.isNull(Payment::getLastSyncTime)
                        .or()
                        .le(Payment::getLastSyncTime, now.minusSeconds(QUERY_INTERVAL_SECONDS)))
                .orderByAsc(Payment::getCreateTime)
                .last("LIMIT " + BATCH_SIZE);

        List<Payment> pendingPayments = paymentMapper.selectList(wrapper);
        for (Payment payment : pendingPayments) {
            if (paymentMapper.claimSync(payment.getId(),
                    now.minusSeconds(QUERY_INTERVAL_SECONDS), now) == 0) {
                continue;
            }
            try {
                paymentService.syncPaymentStatus(payment.getOrderNo(), null);
            } catch (Exception e) {
                log.warn("Failed to reconcile pending payment: orderNo={}", payment.getOrderNo(), e);
            }
        }
    }
}
