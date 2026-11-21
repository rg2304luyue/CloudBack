package org.cloudback.payment.scheduler;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.result.R;
import org.cloudback.common.service.OutboxService;
import org.cloudback.payment.feign.OrderFeignClient;
import org.cloudback.payment.mapper.PaymentMapper;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Reconciles pending payments regardless of order age after a lost return or notify. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final int BATCH_SIZE = 20;
    private static final int QUERY_INTERVAL_SECONDS = 30;
    private static final int REVERSE_INTERVAL_MINUTES = 2;
    /**
     * 已关闭/已失败支付单的反向复核窗口：支付宝收银台的有效期是 30 分钟，
     * 超过这个窗口的订单不可能再有"取消后才付款"的情况。不加窗口会让每一条
     * 从未付款的历史关闭单被永久反复查单。
     */
    private static final int LATE_PAYMENT_WINDOW_HOURS = 24;

    private final PaymentMapper paymentMapper;
    private final PaymentService paymentService;
    private final OrderFeignClient orderFeignClient;
    private final OutboxService outboxService;

    @Scheduled(initialDelay = 10000, fixedDelay = QUERY_INTERVAL_SECONDS * 1000L)
    public void reconcilePendingPayments() {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<Payment>()
                .eq(Payment::getStatus, 0)
                .and(query -> query.isNull(Payment::getLastSyncTime)
                        .or()
                        .le(Payment::getLastSyncTime, now.minusSeconds(QUERY_INTERVAL_SECONDS)))
                // Rotate through pending records; oldest orders must not monopolize every batch.
                .orderByAsc(Payment::getLastSyncTime)
                .orderByAsc(Payment::getId)
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

    /**
     * 反向对账（每 2 分钟）：支付成功但订单侧可能未收到支付结果的兜底。
     * 订单仍待支付 → 重发 payment-result；订单已取消 → 自动退款；订单不存在 → 人工排查。
     * 复用 lastSyncTime 条件更新做节流与多实例抢占。
     */
    @Scheduled(initialDelay = 60000, fixedDelay = REVERSE_INTERVAL_MINUTES * 60 * 1000L)
    public void reconcilePaidPayments() {
        LocalDateTime now = LocalDateTime.now();
        // 反向对账同时覆盖「已支付」与「已关闭/已失败但可能已扣款」两类记录：
        // 后者是「订单取消后用户才付款」的竞态落点，支付单会停在 3(CLOSED) 而不是 1(PAID)，
        // 只扫 status=1 会让这笔钱永远没有兜底。
        LocalDateTime lateCutoff = now.minusHours(LATE_PAYMENT_WINDOW_HOURS);
        List<Payment> paidPayments = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .and(query -> query.eq(Payment::getStatus, SystemConstants.PAYMENT_STATUS_PAID)
                        .or(late -> late
                                .in(Payment::getStatus, SystemConstants.PAYMENT_STATUS_FAILED,
                                        SystemConstants.PAYMENT_STATUS_CLOSED)
                                .ge(Payment::getCreateTime, lateCutoff)))
                .and(query -> query.isNull(Payment::getLastSyncTime)
                        .or()
                        .le(Payment::getLastSyncTime, now.minusMinutes(REVERSE_INTERVAL_MINUTES)))
                .orderByAsc(Payment::getLastSyncTime)
                .orderByAsc(Payment::getId)
                .last("LIMIT " + BATCH_SIZE));

        for (Payment payment : paidPayments) {
            if (paymentMapper.claimPaidSync(payment.getId(),
                    now.minusMinutes(REVERSE_INTERVAL_MINUTES), now) == 0) {
                continue;
            }
            try {
                reconcilePaidPayment(payment);
            } catch (Exception e) {
                log.warn("Reverse reconcile failed: orderNo={}", payment.getOrderNo(), e);
            }
        }
    }

    private void reconcilePaidPayment(Payment payment) {
        String orderNo = payment.getOrderNo();
        R<Map<String, Object>> orderResult;
        try {
            orderResult = orderFeignClient.getOrderStatus(orderNo);
        } catch (Exception e) {
            log.warn("反向对账查询订单状态失败，下轮重试: orderNo={}", orderNo, e);
            return;
        }
        if (orderResult == null || orderResult.getCode() != 200 || orderResult.getData() == null) {
            log.error("反向对账：支付成功但订单不存在，需人工排查: orderNo={}", orderNo);
            return;
        }
        Object statusObj = orderResult.getData().get("status");
        Integer orderStatus = statusObj instanceof Number number ? number.intValue() : null;
        if (orderStatus == null) {
            log.error("反向对账：订单状态响应异常，需人工排查: orderNo={}, data={}", orderNo, orderResult.getData());
            return;
        }

        boolean paidLocally = SystemConstants.PAYMENT_STATUS_PAID.equals(payment.getStatus());
        if (SystemConstants.ORDER_STATUS_UNPAID.equals(orderStatus)) {
            if (!paidLocally) {
                // 支付单停在关闭/失败，但订单仍待支付：交由支付服务向支付宝确认是否真的收过钱，
                // 确有收款会入账并退款（settleLatePaymentIfCharged），避免这里凭空补发「已支付」。
                paymentService.syncPaymentStatus(orderNo, null);
                log.warn("反向对账：支付单状态与订单不一致，已触发状态复核: orderNo={}, paymentStatus={}",
                        orderNo, payment.getStatus());
                return;
            }
            // 订单仍待支付：重发支付结果消息（格式与 PaymentServiceImpl.markPaid 一致，订单端幂等）。
            // 走 Outbox 而不是裸发，保证发送失败时仍会被重试并计入失败计数。
            JSONObject resultMsg = new JSONObject();
            resultMsg.put("orderNo", orderNo);
            resultMsg.put("status", "SUCCESS");
            resultMsg.put("tradeNo", payment.getTradeNo());
            outboxService.saveMessage(SystemConstants.KAFKA_TOPIC_PAYMENT_RESULT,
                    orderNo, resultMsg.toJSONString());
            log.info("反向对账：订单仍待支付，已重发支付结果消息: orderNo={}", orderNo);
        } else if (SystemConstants.ORDER_STATUS_CANCELLED.equals(orderStatus)) {
            if (!paidLocally) {
                // 支付单停在关闭/失败但订单已取消：先向支付宝确认是否真的收过钱。
                // 确有收款会入账并自动退款；没有收款则什么都不做，避免对未付款订单发起退款。
                paymentService.syncPaymentStatus(orderNo, null);
                log.warn("反向对账：订单已取消且支付单未标记成功，已触发一次状态复核: orderNo={}, paymentStatus={}",
                        orderNo, payment.getStatus());
                return;
            }
            log.error("反向对账：支付成功但订单已取消，发起自动退款: orderNo={}", orderNo);
            paymentService.refundByOrder(orderNo, payment.getTradeNo());
        }
        // 其他状态（已支付/已发货/已完成）无需处理，lastSyncTime 已在抢占时更新
    }
}
