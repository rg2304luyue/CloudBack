package org.cloudback.order.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.order.feign.ProductFeignClient;
import org.cloudback.order.mapper.OrderItemMapper;
import org.cloudback.order.mapper.OrderMapper;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.model.entity.OrderItem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消：每 60 秒扫描超过 30 分钟未支付的订单，
 * 将其状态改为已取消，并回滚库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductFeignClient productFeignClient;

    @Scheduled(fixedRate = 60000)
    public void cancelExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, SystemConstants.ORDER_STATUS_UNPAID)
                .le(Order::getCreateTime, deadline);
        List<Order> expiredOrders = orderMapper.selectList(wrapper);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("扫描到 {} 个超时未支付订单，开始自动取消", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                cancelSingleOrder(order);
                log.info("自动取消超时订单: orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.error("取消超时订单失败, orderNo={}, 将在下次重试",
                        order.getOrderNo(), e);
            }
        }

        log.info("超时订单处理完毕");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelSingleOrder(Order order) {
        order.setStatus(SystemConstants.ORDER_STATUS_CANCELLED);
        orderMapper.updateById(order);

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            productFeignClient.restoreStock(item.getProductId(), item.getQuantity());
        }
    }
}
