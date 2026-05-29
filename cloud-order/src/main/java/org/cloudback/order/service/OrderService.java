package org.cloudback.order.service;

import org.cloudback.common.result.R;
import org.cloudback.order.model.entity.Order;
import java.util.List;

/**
 * 订单服务接口，提供下单、订单查询、取消订单功能。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public interface OrderService {

    /** 创建订单：获取购物车 → 扣库存 → 查地址 → 创建订单明细 → 清空购物车 → Kafka 通知支付 */
    R<Order> createOrder(Long userId, Long addressId, String remark);

    /** 查询订单详情 */
    R<Order> getOrderDetail(Long userId, Long orderId);

    /** 分页查询用户订单列表 */
    R<List<Order>> getOrderList(Long userId, Integer page, Integer size);

    /** 取消订单（仅待支付状态可取消） */
    R<String> cancelOrder(Long userId, Long orderId);

    /** 卖家查看包含自己商品的订单 */
    R<List<Order>> getSellerOrders(Long sellerId, Integer page, Integer size);

    /** 卖家发货（已支付 → 已发货） */
    R<String> shipOrder(Long sellerId, Long orderId);

    /** 确认收货（已发货 → 已完成） */
    R<String> receiveOrder(Long userId, Long orderId);
}
