package org.cloudback.order.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.common.service.OutboxService;
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
import org.cloudback.order.service.OrderRequestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final long ORDER_TIMEOUT_MINUTES = 30;
    private static final int MAX_EXPIRED_ORDERS_PER_SCAN = 20;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartFeignClient cartFeignClient;
    private final ProductFeignClient productFeignClient;
    private final UserFeignClient userFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OutboxService outboxService;
    private final PlatformTransactionManager transactionManager;
    private final OrderRequestService orderRequests;

    @Value("${cloud.internal.token}")
    private String internalToken;

    @Override
    public R<Order> createOrder(Long userId, Long addressId, String remark, String orderToken) {
        return R.ok("下单成功", orderRequests.create(userId, orderToken,
                orderNo -> prepareOrder(userId, addressId, remark, orderNo), this::executeOrder));
    }

    private Order prepareOrder(Long userId, Long addressId, String remark, String orderNo) {
        R<List<CartItemDTO>> cartResult = cartFeignClient.getCheckedItems(userId);
        if (cartResult == null || cartResult.getCode() != 200) {
            throw new BusinessException("购物车服务暂时不可用");
        }
        if (cartResult.getData() == null || cartResult.getData().isEmpty()) {
            throw new BusinessException("购物车中没有已勾选的商品");
        }

        List<CartItemDTO> checkedItems = cartResult.getData();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItemDTO item : checkedItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException("商品数量必须大于0, productId=" + item.getProductId());
            }
            ProductDTO product = loadPublishedProduct(item.getProductId());
            BigDecimal unitPrice = product.getPrice();
            if (unitPrice == null) {
                throw new BusinessException("商品价格异常，无法下单，productId=" + item.getProductId());
            }
            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderNo(orderNo);
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(product.getName());
            orderItem.setProductImage(product.getMainImage());
            orderItem.setPrice(unitPrice);
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
        fillAddress(userId, addressId, order);
        order.setOrderItems(orderItems);
        return order;
    }

    private void executeOrder(Order order) {
        String orderNo = order.getOrderNo();
        for (OrderItem item : order.getOrderItems()) {
            R<String> stockResult = productFeignClient.deductStock(item.getProductId(), item.getQuantity(), orderNo, internalToken);
            if (stockResult == null) {
                throw new BusinessException("库存服务未返回结果");
            }
            if (stockResult.getCode() != 200) {
                throw new BusinessException(stockResult.getCode(), stockResult.getMessage());
            }
        }

        saveOrderAndItems(order, order.getOrderItems());
        saveOrderCreateOutbox(order, order.getUserId(), order.getTotalAmount());
        registerAfterCommitSideEffects(order.getUserId(), order.getOrderItems().stream()
                .map(OrderItem::getProductId).toList(), orderNo);
        log.info("Order created: orderNo={}, totalAmount={}", orderNo, order.getTotalAmount());
    }

    @Override
    public R<Order> getOrderDetail(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        order.setOrderItems(items);
        return R.ok(order);
    }

    @Override
    public R<Order> getOrderByNo(Long userId, String orderNo) {
        if (userId == null || orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        return R.ok(order);
    }

    @Override
    public R<List<Order>> getOrderList(Long userId, Integer page, Integer size) {
        Page<Order> orderPage = new Page<>(page, size);
        orderMapper.selectPage(orderPage, new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));
        return R.ok(orderPage.getRecords(), (int) orderPage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> cancelOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, SystemConstants.ORDER_STATUS_UNPAID)
                .set(Order::getStatus, SystemConstants.ORDER_STATUS_CANCELLED));
        if (rows == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR.getCode(), "只能取消待支付的订单");
        }
        restoreStockAndCleanup(order);
        return R.ok("订单已取消");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByNo(String orderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            log.debug("Timeout cancel skipped, order not found: orderNo={}", orderNo);
            return;
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, SystemConstants.ORDER_STATUS_UNPAID)
                .set(Order::getStatus, SystemConstants.ORDER_STATUS_CANCELLED));
        if (rows == 0) {
            log.debug("Timeout cancel skipped, order status changed: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }
        restoreStockAndCleanup(order);
    }

    @Override
    public int scanExpiredOrders() {
        List<Order> expiredOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, SystemConstants.ORDER_STATUS_UNPAID)
                .lt(Order::getCreateTime, LocalDateTime.now().minusMinutes(ORDER_TIMEOUT_MINUTES))
                .last("LIMIT " + MAX_EXPIRED_ORDERS_PER_SCAN));
        int count = 0;
        for (Order order : expiredOrders) {
            try {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                if (Boolean.TRUE.equals(tx.execute(status -> doCancelOrder(order)))) {
                    count++;
                    log.info("DB fallback cancelled expired order: orderNo={}", order.getOrderNo());
                }
            } catch (Exception e) {
                log.error("DB fallback cancel failed: orderNo={}", order.getOrderNo(), e);
            }
        }
        return count;
    }

    @Override
    public R<List<Order>> getSellerOrders(Long sellerId, Integer page, Integer size) {
        List<Long> sellerProductIds;
        try {
            R<List<ProductDTO>> productResult = productFeignClient.getProductsBySellerId(sellerId);
            if (productResult.getCode() != 200 || productResult.getData() == null || productResult.getData().isEmpty()) {
                return R.ok(Collections.emptyList(), 0);
            }
            sellerProductIds = productResult.getData().stream().map(ProductDTO::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Load seller product ids failed, sellerId={}", sellerId, e);
            return R.ok(Collections.emptyList(), 0);
        }

        Page<OrderItem> itemPage = new Page<>(page, size);
        orderItemMapper.selectPage(itemPage, new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getProductId, sellerProductIds)
                .select(OrderItem::getOrderId)
                .groupBy(OrderItem::getOrderId)
                .orderByDesc(OrderItem::getOrderId));

        List<Long> orderIds = itemPage.getRecords().stream().map(OrderItem::getOrderId).distinct().collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return R.ok(Collections.emptyList(), 0);
        }

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getId, orderIds)
                .orderByDesc(Order::getCreateTime));
        List<OrderItem> allItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        Map<Long, List<OrderItem>> itemsMap = allItems.stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        for (Order order : orders) {
            order.setOrderItems(itemsMap.getOrDefault(order.getId(), Collections.emptyList()));
        }
        return R.ok(orders, (int) itemPage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> shipOrder(Long sellerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        if (!SystemConstants.ORDER_STATUS_PAID.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR.getCode(), "只能对已支付的订单发货");
        }

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        List<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toList());
        Set<Long> sellerProductIds = loadSellerProductIds(sellerId);
        if (productIds.isEmpty() || !sellerProductIds.containsAll(productIds)) {
            throw new BusinessException("仅可对全部商品均属于您的订单执行整单发货");
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, SystemConstants.ORDER_STATUS_PAID)
                .set(Order::getStatus, SystemConstants.ORDER_STATUS_SHIPPED));
        if (rows == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR.getCode(), "订单状态已变化，请刷新后重试");
        }
        return R.ok("发货成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> receiveOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        if (!SystemConstants.ORDER_STATUS_SHIPPED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR.getCode(), "只能确认已发货的订单");
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, SystemConstants.ORDER_STATUS_SHIPPED)
                .set(Order::getStatus, SystemConstants.ORDER_STATUS_COMPLETED));
        if (rows == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR.getCode(), "订单状态已变化，请刷新后重试");
        }
        return R.ok("已确认收货");
    }

    @Override
    public R<String> generateOrderToken(Long userId) {
        return R.ok(orderRequests.issue(userId));
    }

    private ProductDTO loadPublishedProduct(Long productId) {
        R<ProductDTO> productResult = productFeignClient.getProductDetail(productId);
        if (productResult.getCode() != 200 || productResult.getData() == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        ProductDTO product = productResult.getData();
        if (product.getName() == null) {
            throw new BusinessException("商品名称缺失，无法下单，productId=" + productId);
        }
        return product;
    }

    private void fillAddress(Long userId, Long addressId, Order order) {
        if (addressId == null || addressId <= 0) {
            throw new BusinessException("请选择有效的收货地址");
        }
        R<Map<String, Object>> addressResult;
        try {
            addressResult = userFeignClient.getAddressById(userId, addressId);
        } catch (Exception e) {
            throw new BusinessException("收货地址查询失败，请稍后重试");
        }
        if (addressResult == null || addressResult.getCode() != 200 || addressResult.getData() == null) {
            throw new BusinessException("收货地址不存在或暂时无法使用");
        }
        Map<String, Object> addr = addressResult.getData();
        if (userId == null || !userId.toString().equals(Objects.toString(addr.get("userId"), ""))) {
            throw new BusinessException("收货地址不属于当前用户");
        }
        String receiverName = requiredAddressText(addr, "receiverName");
        String phone = requiredAddressText(addr, "phone");
        String detail = requiredAddressText(addr, "detail");
        order.setReceiverName(receiverName);
        order.setReceiverPhone(phone);
        order.setReceiverAddress(Objects.toString(addr.get("province"), "")
                + Objects.toString(addr.get("city"), "")
                + Objects.toString(addr.get("district"), "") + detail);
    }

    private String requiredAddressText(Map<String, Object> address, String field) {
        Object value = address.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessException("收货地址信息不完整，请完善姓名、电话和详细地址");
        }
        return text.trim();
    }

    private void saveOrderAndItems(Order order, List<OrderItem> orderItems) {
        orderMapper.insert(order);
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }
    }

    private void saveOrderCreateOutbox(Order order, Long userId, BigDecimal totalAmount) {
        Map<String, Object> kafkaMsg = new HashMap<>();
        kafkaMsg.put("orderId", order.getId());
        kafkaMsg.put("orderNo", order.getOrderNo());
        kafkaMsg.put("userId", userId);
        kafkaMsg.put("totalAmount", totalAmount);
        kafkaMsg.put("createTime", Objects.toString(order.getCreateTime(), ""));
        outboxService.saveMessage(SystemConstants.KAFKA_TOPIC_ORDER_CREATE, order.getOrderNo(), JSON.toJSONString(kafkaMsg));
    }

    private void registerAfterCommitSideEffects(Long userId, List<Long> orderedProductIds, String orderNo) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long productId : orderedProductIds) {
                    try {
                        cartFeignClient.removeItem(userId, productId);
                    } catch (Exception e) {
                        log.error("Remove ordered cart item failed: userId={}, productId={}", userId, productId, e);
                    }
                }
                long expireTime = System.currentTimeMillis() + ORDER_TIMEOUT_MINUTES * 60 * 1000;
                try {
                    redisTemplate.opsForZSet().add("cloud:order:timeout", orderNo, expireTime);
                } catch (Exception e) {
                    log.warn("Order committed; timeout will use database fallback: orderNo={}", orderNo, e);
                }
            }
        });
    }

    private boolean doCancelOrder(Order order) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, SystemConstants.ORDER_STATUS_UNPAID)
                .set(Order::getStatus, SystemConstants.ORDER_STATUS_CANCELLED));
        if (rows == 0) {
            return false;
        }
        restoreStockAndCleanup(order);
        return true;
    }

    private void restoreStockAndCleanup(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            saveStockRestoreMessage(order.getOrderNo(), order.getId(), item.getProductId(), item.getQuantity());
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    redisTemplate.opsForZSet().remove("cloud:order:timeout", order.getOrderNo());
                } catch (Exception e) {
                    log.warn("Cancelled order timeout cleanup deferred: orderNo={}", order.getOrderNo(), e);
                }
            }
        });
    }

    private void saveStockRestoreMessage(String orderNo, Long orderId, Long productId, Integer quantity) {
        JSONObject restoreMsg = new JSONObject();
        restoreMsg.put("orderNo", orderNo);
        restoreMsg.put("orderId", orderId);
        restoreMsg.put("productId", productId);
        restoreMsg.put("quantity", quantity);
        restoreMsg.put("idempotencyKey", orderNo + ":" + productId);
        outboxService.saveMessage(SystemConstants.KAFKA_TOPIC_STOCK_RESTORE,
                orderNo + ":" + productId,
                restoreMsg.toJSONString());
    }

    private Set<Long> loadSellerProductIds(Long sellerId) {
        try {
            R<List<ProductDTO>> result = productFeignClient.getProductsBySellerId(sellerId);
            if (result.getCode() != 200 || result.getData() == null) {
                throw new BusinessException("无法验证卖家商品");
            }
            return result.getData().stream().map(ProductDTO::getId).collect(Collectors.toSet());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("验证卖家商品失败");
        }
    }
}
