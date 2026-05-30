package org.cloudback.order.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.order.dto.CreateOrderRequest;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.service.OrderService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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

    /** POST /orders — 创建订单：幂等校验 → 查购物车 → 扣库存 → 写库 → 清购物车 → Kafka → Redis 超时 */
    @PostMapping
    public R<Order> createOrder(@RequestHeader("X-User-Id") Long userId,
                                @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(userId, request.addressId(), request.remark(), request.orderToken());
    }

    /** GET /orders/{id} — 查询订单详情（含订单明细），校验用户归属防越权 */
    @GetMapping("/{id}")
    public R<Order> getOrderDetail(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable Long id) {
        return orderService.getOrderDetail(userId, id);
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

    /** GET /orders/token — 生成下单幂等 Token（Redis 存储，30 分钟有效），防止重复提交 */
    @GetMapping("/token")
    public R<String> getOrderToken(@RequestHeader("X-User-Id") Long userId) {
        return orderService.generateOrderToken(userId);
    }
}
