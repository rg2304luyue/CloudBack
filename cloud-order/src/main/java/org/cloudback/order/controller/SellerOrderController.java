package org.cloudback.order.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.service.OrderService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/seller/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderService orderService;

    /** 卖家查看包含自己商品的订单 */
    @GetMapping
    public R<List<Order>> getSellerOrders(@RequestHeader("X-User-Id") Long sellerId,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size) {
        return orderService.getSellerOrders(sellerId, page, size);
    }

    /** 卖家发货（已支付 → 已发货） */
    @PutMapping("/{id}/ship")
    public R<String> shipOrder(@RequestHeader("X-User-Id") Long sellerId,
                               @PathVariable Long id) {
        return orderService.shipOrder(sellerId, id);
    }
}

