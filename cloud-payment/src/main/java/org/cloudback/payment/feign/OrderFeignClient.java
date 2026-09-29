package org.cloudback.payment.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 订单服务内部接口客户端。内部令牌由 common 的 Feign 拦截器自动注入。
 */
@FeignClient(name = "cloud-order")
public interface OrderFeignClient {

    /** GET /orders/internal/status — 按订单号查询订单状态（data.status 为订单状态码） */
    @GetMapping("/orders/internal/status")
    R<Map<String, Object>> getOrderStatus(@RequestParam("orderNo") String orderNo);
}
