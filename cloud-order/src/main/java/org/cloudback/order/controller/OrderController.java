package org.cloudback.order.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.cloudback.common.result.R;
import org.cloudback.order.dto.CreateOrderRequest;
import org.cloudback.order.dto.OrderRequestResult;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.service.OrderRequestService;
import org.cloudback.order.service.OrderService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 订单控制器，提供下单、订单查询、取消订单接口。
 * 用户身份通过 X-User-Id 请求头获取。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRequestService orderRequests;

    @GetMapping("/requests/{token}")
    public R<OrderRequestResult> requestResult(@RequestHeader("X-User-Id") Long userId,
                                               @PathVariable String token) {
        return R.ok(orderRequests.result(userId, token));
    }

    /** POST /orders — 创建订单：幂等校验 → 查购物车 → 扣库存 → 写库 → 清购物车 → Kafka → Redis 超时 */
    @PostMapping
    public R<Order> createOrder(@RequestHeader("X-User-Id") Long userId,
                                @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(userId, request.addressId(), request.remark(), request.orderToken());
    }

    /** GET /orders/{id} — 查询订单详情（含订单明细），校验用户归属防越权 */
    @GetMapping("/{id}")
    public R<Order> getOrderDetail(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable Long id) {
        return orderService.getOrderDetail(userId, id);
    }

    /** 按订单号查询当前用户的订单，供支付结果页核对状态。 */
    @GetMapping("/by-no/{orderNo}")
    public R<Order> getOrderByNo(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable String orderNo) {
        return orderService.getOrderByNo(userId, orderNo);
    }

    /** GET /orders — 分页查询当前用户的订单列表，按创建时间降序 */
    @GetMapping
    public R<List<Order>> getOrderList(@RequestHeader("X-User-Id") Long userId,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size) {
        return orderService.getOrderList(userId, page, size);
    }

    /** PATCH /orders/{id}/cancel — 取消待支付订单，回滚库存并从 Redis 超时 ZSet 中移除 */
    @PatchMapping("/{id}/cancel")
    public R<String> cancelOrder(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable Long id) {
        return orderService.cancelOrder(userId, id);
    }

    /** PATCH /orders/{id}/receive — 确认收货，仅已发货状态可操作 */
    @PatchMapping("/{id}/receive")
    public R<String> receiveOrder(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable Long id) {
        return orderService.receiveOrder(userId, id);
    }

    /** 获取或复用当前用户尚未完成的持久化下单请求。 */
    @GetMapping("/token")
    public R<String> getOrderToken(@RequestHeader("X-User-Id") Long userId) {
        return orderService.generateOrderToken(userId);
    }

    /** GET /orders/internal/status — 内部接口：按订单号查询订单状态（支付服务发起支付前校验） */
    @GetMapping("/internal/status")
    public R<Map<String, Object>> getInternalOrderStatus(@RequestParam String orderNo) {
        return orderService.getInternalOrderStatus(orderNo);
    }
}
