package org.cloudback.payment.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @GetMapping("/{orderNo}")
    public R<Payment> getPaymentByOrderNo(@PathVariable String orderNo) {
        return paymentService.getPaymentByOrderNo(orderNo);
    }

    // TODO: 支付宝异步通知回调接口
    // @PostMapping("/alipay/notify")
    // public String alipayNotify(@RequestParam Map<String, String> params) { ... }
}
