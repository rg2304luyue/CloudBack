package org.cloudback.order.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
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
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建订单 */
    @PostMapping("/create")
    public R<Order> createOrder(@RequestHeader("X-User-Id") Long userId,
                                @RequestParam(required = false) Long addressId,
                                @RequestParam(required = false) String remark) {
        return orderService.createOrder(userId, addressId, remark);
    }

    /** 订单详情 */
    @GetMapping("/detail/{id}")
    public R<Order> getOrderDetail(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable Long id) {
        return orderService.getOrderDetail(userId, id);
    }

    /** 订单列表 */
    @GetMapping("/list")
    public R<List<Order>> getOrderList(@RequestHeader("X-User-Id") Long userId,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size) {
        return orderService.getOrderList(userId, page, size);
    }

    /** 取消订单 */
    @PutMapping("/cancel/{id}")
    public R<String> cancelOrder(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable Long id) {
        return orderService.cancelOrder(userId, id);
    }
}
