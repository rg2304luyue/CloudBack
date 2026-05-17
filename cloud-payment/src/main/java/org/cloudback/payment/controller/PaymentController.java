package org.cloudback.payment.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付控制器，提供支付记录查询接口。
 * 支付宝异步回调接口预留 TODO。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 根据订单号查询支付记录 */
    @GetMapping("/{orderNo}")
    public R<Payment> getPaymentByOrderNo(@PathVariable String orderNo) {
        return paymentService.getPaymentByOrderNo(orderNo);
    }
}
