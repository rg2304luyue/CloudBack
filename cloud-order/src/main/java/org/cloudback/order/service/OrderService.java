package org.cloudback.order.service;

import org.cloudback.common.result.R;
import org.cloudback.order.model.entity.Order;
import java.util.List;

public interface OrderService {
    R<Order> createOrder(Long userId, Long addressId, String remark);

    R<Order> getOrderDetail(Long userId, Long orderId);

    R<List<Order>> getOrderList(Long userId, Integer page, Integer size);

    R<String> cancelOrder(Long userId, Long orderId);
}
