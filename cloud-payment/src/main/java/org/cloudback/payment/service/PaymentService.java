package org.cloudback.payment.service;

import org.cloudback.common.result.R;
import org.cloudback.payment.model.entity.Payment;
import java.math.BigDecimal;

public interface PaymentService {
    R<Payment> getPaymentByOrderNo(String orderNo);

    R<String> processPayment(String orderNo, Long userId, BigDecimal amount);
}
