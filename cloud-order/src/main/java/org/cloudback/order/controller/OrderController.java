package org.cloudback.order.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.service.OrderService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/create")
    public R<Order> createOrder(@RequestHeader("X-User-Id") Long userId,
                                @RequestParam(required = false) Long addressId,
                                @RequestParam(required = false) String remark) {
        return orderService.createOrder(userId, addressId, remark);
    }

    @GetMapping("/detail/{id}")
    public R<Order> getOrderDetail(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable Long id) {
        return orderService.getOrderDetail(userId, id);
    }

    @GetMapping("/list")
    public R<List<Order>> getOrderList(@RequestHeader("X-User-Id") Long userId,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size) {
        return orderService.getOrderList(userId, page, size);
    }

    @PutMapping("/cancel/{id}")
    public R<String> cancelOrder(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable Long id) {
        return orderService.cancelOrder(userId, id);
    }
}
