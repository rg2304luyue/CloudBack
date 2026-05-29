package org.cloudback.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.order.feign.CartFeignClient;
import org.cloudback.order.feign.CartItemDTO;
import org.cloudback.order.feign.ProductDTO;
import org.cloudback.order.feign.ProductFeignClient;
import org.cloudback.order.feign.UserFeignClient;
import org.cloudback.order.mapper.OrderItemMapper;
import org.cloudback.order.mapper.OrderMapper;
import org.cloudback.order.model.entity.Order;
import org.cloudback.order.model.entity.OrderItem;
import org.cloudback.order.service.OrderService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单服务实现，处理下单全流程。
 * 流程: 获取购物车勾选商品 → 扣减库存 → 查询地址 → 创建订单及明细 → 清空购物车 → Kafka 通知支付
 * 整个过程由 @Transactional 保护，任一步骤失败则全部回滚。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartFeignClient cartFeignClient;
    private final ProductFeignClient productFeignClient;
    private final UserFeignClient userFeignClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 创建订单：先写订单后扣库存，避免订单入库失败而库存已被扣减 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Order> createOrder(Long userId, Long addressId, String remark) {
        R<List<CartItemDTO>> cartResult = cartFeignClient.getCheckedItems();
        if (cartResult.getData() == null || cartResult.getData().isEmpty()) {
            throw new BusinessException("购物车中没有已勾选的商品");
        }

        List<CartItemDTO> checkedItems = cartResult.getData();

        String orderNo = IdUtil.getSnowflakeNextIdStr();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItemDTO item : checkedItems) {
            BigDecimal itemTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            String productName = item.getName();
            String productImage = item.getMainImage();

            if (productName == null || productImage == null) {
                try {
                    R<ProductDTO> productResult = productFeignClient.getProductDetail(item.getProductId());
                    if (productResult.getCode() == 200 && productResult.getData() != null) {
                        if (productName == null) productName = productResult.getData().getName();
                        if (productImage == null) productImage = productResult.getData().getMainImage();
                    }
                } catch (Exception e) {
                    log.warn("获取商品信息失败, productId={}", item.getProductId(), e);
                }
            }
            if (productName == null) {
                throw new BusinessException("商品名称缺失，无法下单，productId=" + item.getProductId());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderNo(orderNo);
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(productName);
            orderItem.setProductImage(productImage);
            orderItem.setPrice(item.getPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setTotalAmount(itemTotal);
            orderItems.add(orderItem);
        }

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(SystemConstants.ORDER_STATUS_UNPAID);
        order.setRemark(remark);

        if (addressId != null) {
            R<Map<String, Object>> addressResult = userFeignClient.getAddressById(userId, addressId);
            if (addressResult.getCode() == 200 && addressResult.getData() != null) {
                Map<String, Object> addr = addressResult.getData();
                order.setReceiverName((String) addr.get("receiverName"));
                order.setReceiverPhone((String) addr.get("phone"));
                String fullAddress = addr.get("province").toString() + addr.get("city").toString() +
                                     addr.get("district").toString() + addr.get("detail").toString();
                order.setReceiverAddress(fullAddress);
            }
        }

        // 先写订单和明细，确保订单入库成功
        orderMapper.insert(order);
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 订单写成功再扣库存，若失败则回滚订单
        for (CartItemDTO item : checkedItems) {
            R<String> stockResult = productFeignClient.deductStock(item.getProductId(), item.getQuantity());
            if (stockResult.getCode() != 200) {
                throw new BusinessException(stockResult.getCode(), stockResult.getMessage());
            }
        }

        cartFeignClient.clearCart();

        Map<String, Object> kafkaMsg = new HashMap<>();
        kafkaMsg.put("orderId", order.getId());
        kafkaMsg.put("orderNo", orderNo);
        kafkaMsg.put("userId", userId);
        kafkaMsg.put("totalAmount", totalAmount);
        kafkaMsg.put("createTime", order.getCreateTime().toString());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(SystemConstants.KAFKA_TOPIC_ORDER_CREATE, JSON.toJSONString(kafkaMsg));
                log.info("Kafka 消息已发送: orderNo={}", orderNo);
            }
        });

        log.info("订单创建成功: orderNo={}, totalAmount={}", orderNo, totalAmount);
        return R.ok("下单成功", order);
    }

    /** 查询订单详情（含订单明细），校验 userId 防越权 */
    @Override
    public R<Order> getOrderDetail(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        order.setOrderItems(items);
        return R.ok(order);
    }

    /** 分页查询用户订单列表，按创建时间降序 */
    @Override
    public R<List<Order>> getOrderList(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime);
        Page<Order> orderPage = new Page<>(page, size);
        orderMapper.selectPage(orderPage, wrapper);
        return R.ok(orderPage.getRecords(), (int) orderPage.getTotal());
    }

    /** 取消订单，仅待支付状态可取消，同时回滚库存 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> cancelOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        if (!order.getStatus().equals(SystemConstants.ORDER_STATUS_UNPAID)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR.getCode(), "只能取消待支付的订单");
        }

        order.setStatus(SystemConstants.ORDER_STATUS_CANCELLED);
        orderMapper.updateById(order);

        // 回滚库存：查询订单明细，逐个恢复库存和销量
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            productFeignClient.restoreStock(item.getProductId(), item.getQuantity());
        }

        return R.ok("订单已取消");
    }

    // ==================== 卖家订单 ====================

    /** 查询包含某卖家商品的订单（通过 order_item 表关联） */
    @Override
    public R<List<Order>> getSellerOrders(Long sellerId, Integer page, Integer size) {
        // 1. 查出包含该卖家商品的 order_id 列表（去重）
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getProductId, sellerId) // 注意：这里需要用子查询或自定义SQL
                .select(OrderItem::getOrderId);
        // 上面的写法不对，order_item 没有 sellerId 字段
        // 正确做法：先查该卖家的所有商品 ID，再查包含这些商品的订单

        // 使用自定义查询：先从 product 服务获取卖家商品 ID 列表
        // 简化方案：直接在 order_item 上按 productId IN (...) 查询
        // 需要先通过 Feign 获取卖家的商品 ID 列表
        List<Long> sellerProductIds;
        try {
            R<List<ProductDTO>> productResult = productFeignClient.getProductsBySellerId(sellerId);
            if (productResult.getCode() != 200 || productResult.getData() == null
                    || productResult.getData().isEmpty()) {
                return R.ok(new ArrayList<>(), 0);
            }
            sellerProductIds = productResult.getData().stream()
                    .map(ProductDTO::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取卖家商品列表失败, sellerId={}", sellerId, e);
            return R.ok(new ArrayList<>(), 0);
        }

        // 2. 查出包含这些商品的 order_id（去重）
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getProductId, sellerProductIds)
                .select(OrderItem::getOrderId)
                .groupBy(OrderItem::getOrderId)
                .orderByDesc(OrderItem::getOrderId);
        List<OrderItem> items = orderItemMapper.selectList(wrapper);
        List<Long> orderIds = items.stream().map(OrderItem::getOrderId)
                .distinct().collect(Collectors.toList());

        if (orderIds.isEmpty()) {
            return R.ok(new ArrayList<>(), 0);
        }

        // 3. 按 orderIds 查订单并分页
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<Order>()
                .in(Order::getId, orderIds)
                .orderByDesc(Order::getCreateTime);
        Page<Order> orderPage = new Page<>(page, size);
        orderMapper.selectPage(orderPage, orderWrapper);

        // 填充订单明细
        List<Order> orders = orderPage.getRecords();
        for (Order order : orders) {
            List<OrderItem> orderItems = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
            order.setOrderItems(orderItems);
        }

        return R.ok(orders, (int) orderPage.getTotal());
    }

    /** 卖家发货：已支付(1) → 已发货(2) */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> shipOrder(Long sellerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        if (!order.getStatus().equals(SystemConstants.ORDER_STATUS_PAID)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR.getCode(), "只能对已支付的订单发货");
        }
        // 校验该订单包含此卖家的商品
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        List<Long> sellerProductIds;
        try {
            R<List<ProductDTO>> result = productFeignClient.getProductsBySellerId(sellerId);
            if (result.getCode() != 200 || result.getData() == null) {
                throw new BusinessException("无法验证卖家商品");
            }
            sellerProductIds = result.getData().stream()
                    .map(ProductDTO::getId).collect(Collectors.toList());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("验证卖家商品失败");
        }
        boolean hasSellerProduct = items.stream()
                .anyMatch(item -> sellerProductIds.contains(item.getProductId()));
        if (!hasSellerProduct) {
            throw new BusinessException("该订单不包含您的商品");
        }

        order.setStatus(SystemConstants.ORDER_STATUS_SHIPPED);
        orderMapper.updateById(order);
        return R.ok("发货成功");
    }

    /** 确认收货，仅已发货状态可操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> receiveOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        if (!order.getStatus().equals(SystemConstants.ORDER_STATUS_SHIPPED)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR.getCode(), "只能确认已发货的订单");
        }
        order.setStatus(SystemConstants.ORDER_STATUS_COMPLETED);
        orderMapper.updateById(order);
        return R.ok("已确认收货");
    }
}
