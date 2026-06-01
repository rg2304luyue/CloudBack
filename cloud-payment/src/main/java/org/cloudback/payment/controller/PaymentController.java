package org.cloudback.payment.controller;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.payment.config.AlipayProperties;
import org.cloudback.payment.dto.CreatePaymentRequest;
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
    private final AlipayProperties alipayProperties;

    /** GET /payment/{orderNo} — 纯查询支付记录（只读，不触发支付宝同步） */
    @GetMapping("/{orderNo}")
    public R<Payment> getPaymentByOrderNo(@PathVariable String orderNo) {
        return paymentService.getPaymentByOrderNo(orderNo);
    }

    /** POST /payment/{orderNo}/sync — 主动向支付宝同步最新支付状态 */
    @PostMapping("/{orderNo}/sync")
    public R<Payment> syncPaymentStatus(@PathVariable String orderNo) {
        return paymentService.syncPaymentStatus(orderNo);
    }

    /** POST /payment/alipay — 发起支付宝电脑网站支付，返回支付表单 HTML（含 30 分钟超时） */
    @PostMapping("/alipay")
    public R<String> pay(@RequestBody CreatePaymentRequest request,
                         @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return R.fail("用户未登录");
        }
        return paymentService.createPayForm(request.orderNo(), userId);
    }

    /** POST /payment/notify/alipay — 支付宝异步通知回调（服务端对服务端），RSA2 验签后更新状态 */
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

    /** GET /payment/return/alipay — 支付宝同步回跳（浏览器重定向），主动向支付宝查询最新状态并同步后重定向到前端 */
    @GetMapping("/return/alipay")
    public void returnAlipay(HttpServletRequest request,
                             HttpServletResponse response) throws Exception {
        String orderNo = request.getParameter("out_trade_no");
        if (orderNo == null || orderNo.isEmpty()) {
            response.sendRedirect(alipayProperties.getFrontendUrl() + "/payment/result?error=no_order");
            return;
        }

        R<Payment> paymentResult;
        try {
            // 主动向支付宝查询并同步最新状态
            paymentResult = paymentService.syncPaymentStatus(orderNo);
        } catch (Exception e) {
            log.warn("同步回跳查询支付状态异常: orderNo={}", orderNo, e);
            response.sendRedirect(alipayProperties.getFrontendUrl() + "/payment/result?error=query_failed");
            return;
        }

        if (paymentResult.getData() != null && paymentResult.getData().getStatus() == 1) {
            response.sendRedirect(alipayProperties.getFrontendUrl() + "/payment/result?orderNo="
                    + URLEncoder.encode(orderNo, StandardCharsets.UTF_8));
        } else {
            response.sendRedirect(alipayProperties.getFrontendUrl() + "/payment/result?error=not_paid&orderNo="
                    + URLEncoder.encode(orderNo, StandardCharsets.UTF_8));
        }
    }
}
