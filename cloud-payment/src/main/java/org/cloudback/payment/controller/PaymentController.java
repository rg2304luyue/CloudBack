package org.cloudback.payment.controller;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.payment.model.entity.Payment;
import org.cloudback.payment.service.PaymentService;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{orderNo}")
    public R<Payment> getPaymentByOrderNo(@PathVariable String orderNo) {
        return paymentService.getPaymentByOrderNo(orderNo);
    }

    /** 发起支付宝页面支付，返回支付表单 HTML */
    @PostMapping("/pay/{orderNo}")
    public R<String> pay(@PathVariable String orderNo,
                         @RequestHeader("X-User-Id") Long userId) {
        return paymentService.createPayForm(orderNo, userId);
    }

    /** 支付宝异步通知（服务端回调） */
    @PostMapping("/notify/alipay")
    public String notifyAlipay(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String[] values = entry.getValue();
            StringBuilder valStr = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) valStr.append(",");
                valStr.append(values[i]);
            }
            params.put(entry.getKey(), valStr.toString());
        }

        log.info("收到支付宝异步通知: {}", JSON.toJSONString(params));
        return paymentService.handleAlipayNotify(params);
    }

    /** 支付宝同步回跳 —— 查询支付宝确认支付结果后跳转前端 */
    @GetMapping("/return/alipay")
    public void returnAlipay(HttpServletRequest request,
                             HttpServletResponse response) throws Exception {
        // out_trade_no 即订单号，支付宝回跳时附带
        String orderNo = request.getParameter("out_trade_no");
        if (orderNo == null || orderNo.isEmpty()) {
            response.sendRedirect("http://localhost:4173/payment/result?error=no_order");
            return;
        }

        // 触发主动查询支付宝，若已支付则更新本地状态
        try {
            paymentService.getPaymentByOrderNo(orderNo);
        } catch (Exception e) {
            log.warn("同步回跳查询支付状态异常: orderNo={}", orderNo, e);
        }

        String query = "orderNo=" + URLEncoder.encode(orderNo, StandardCharsets.UTF_8);
        response.sendRedirect("http://localhost:4173/payment/result?" + query);
    }
}
