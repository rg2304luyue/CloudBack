package org.cloudback.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 订单服务实现，处理下单全流程。
 * 流程: 幂等Token校验 → 获取购物车勾选商品 → 计算金额 → 查地址 → 逐个扣库存 → 写库 → 清购物车 → Kafka通知支付
 * 整个过程由 @Transactional 保护，任何一个步骤失败则全部回滚。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 创建订单：幂等Token校验 → 查购物车勾选商品 → 计算金额 → 查地址 → 逐个扣库存 → 写库 → 清购物车 → Kafka通知支付 → Redis ZSet超时；任一步骤失败回滚已扣库存 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Order> createOrder(Long userId, Long addressId, String remark, String orderToken) {
        // 幂等性校验：原子删除 token，若 key 不存在则已消费
        if (orderToken == null || orderToken.isBlank()) {
            throw new BusinessException("下单令牌不能为空");
        }
        String tokenKey = "cloud:order:token:" + userId;
        String storedToken = (String) redisTemplate.opsForValue().get(tokenKey);
        if (storedToken == null) {
            throw new BusinessException("请勿重复提交订单");
        }
        if (!storedToken.equals(orderToken)) {
            throw new BusinessException("下单令牌无效");
        }
        redisTemplate.delete(tokenKey);

        // 检查购物车服务是否正常
        R<List<CartItemDTO>> cartResult = cartFeignClient.getCheckedItems(userId);
        if (cartResult.getCode() != 200) {
            throw new BusinessException("购物车服务暂时不可用");
        }
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
                // 安全处理 null 字段
                String fullAddress = Objects.toString(addr.get("province"), "")
                        + Objects.toString(addr.get("city"), "")
                        + Objects.toString(addr.get("district"), "")
                        + Objects.toString(addr.get("detail"), "");
                order.setReceiverAddress(fullAddress);
            }
        }

        // 先扣库存，全部成功后再写订单；若任一扣减失败，回滚已扣的库存
        List<CartItemDTO> deductedItems = new ArrayList<>();
        try {
            for (CartItemDTO item : checkedItems) {
                R<String> stockResult = productFeignClient.deductStock(item.getProductId(), item.getQuantity());
                if (stockResult.getCode() != 200) {
                    throw new BusinessException(stockResult.getCode(), stockResult.getMessage());
                }
                deductedItems.add(item);
            }

            // 库存扣减成功后写入订单和明细
            saveOrderAndItems(order, orderItems);

            // final 副本供 TransactionSynchronization 内部类使用
            final BigDecimal finalAmount = totalAmount;
            final String finalOrderNo = orderNo;

            // 事务提交后再执行非 DB 副作用
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 清空购物车
                    cartFeignClient.clearCart(userId);

                    // Kafka 通知支付服务
                    Map<String, Object> kafkaMsg = new HashMap<>();
                    kafkaMsg.put("orderId", order.getId());
                    kafkaMsg.put("orderNo", finalOrderNo);
                    kafkaMsg.put("userId", userId);
                    kafkaMsg.put("totalAmount", finalAmount);
                    kafkaMsg.put("createTime", Objects.toString(order.getCreateTime(), ""));
                    kafkaTemplate.send(SystemConstants.KAFKA_TOPIC_ORDER_CREATE, JSON.toJSONString(kafkaMsg));
                    log.info("Kafka 消息已发送: orderNo={}", finalOrderNo);

                    // 写入 ZSet 用于超时自动取消
                    String timeoutKey = "cloud:order:timeout";
                    long expireTime = System.currentTimeMillis() + ORDER_TIMEOUT_MINUTES * 60 * 1000;
                    redisTemplate.opsForZSet().add(timeoutKey, finalOrderNo, expireTime);
                }
            });

            log.info("订单创建成功: orderNo={}, totalAmount={}", orderNo, totalAmount);
            return R.ok("下单成功", order);
        } catch (Exception e) {
            // 订单写入失败或后续步骤失败 → 回滚所有已扣库存
            for (CartItemDTO item : deductedItems) {
                try {
                    productFeignClient.restoreStock(item.getProductId(), item.getQuantity());
                } catch (Exception ex) {
                    log.error("回滚库存失败: productId={}", item.getProductId(), ex);
                }
            }
            throw e instanceof BusinessException ? (BusinessException) e : new BusinessException(e.getMessage());
        }
    }

    /** 查询订单详情（含订单明细），校验 userId 归属防越权访问 */
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

    /** 分页查询用户订单列表，按创建时间降序，使用 MyBatis-Plus 分页插件 */
    @Override
    public R<List<Order>> getOrderList(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime);
        Page<Order> orderPage = new Page<>(page, size);
        orderMapper.selectPage(orderPage, wrapper);
        return R.ok(orderPage.getRecords(), (int) orderPage.getTotal());
    }

    /** 用户取消待支付订单：仅 UNPAID 状态可取消 → 改状态为 CANCELLED → 回滚库存 → 移除 Redis ZSet */
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
        doCancelOrder(order);
        return R.ok("订单已取消");
    }

    /** 按 orderNo 取消超时订单（供 OrderTimeoutScheduler 调度器调用），仅 UNPAID 状态生效 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByNo(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            log.debug("超时取消：订单不存在, orderNo={}", orderNo);
            return;
        }
        if (!order.getStatus().equals(SystemConstants.ORDER_STATUS_UNPAID)) {
            log.debug("超时取消：订单状态已变更, orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }
        doCancelOrder(order);
    }

    /** DB 兜底扫描：查询超时未支付的订单并取消，防止 Redis ZSet 数据丢失 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanExpiredOrders() {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, SystemConstants.ORDER_STATUS_UNPAID)
                .lt(Order::getCreateTime, java.time.LocalDateTime.now().minusMinutes(ORDER_TIMEOUT_MINUTES))
                .last("LIMIT " + MAX_EXPIRED_ORDERS_PER_SCAN);

        List<Order> expiredOrders = orderMapper.selectList(wrapper);
        if (expiredOrders.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Order order : expiredOrders) {
            try {
                doCancelOrder(order);
                count++;
                log.info("DB兜底取消超时订单: orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.error("DB兜底取消订单失败: orderNo={}", order.getOrderNo(), e);
            }
        }
        return count;
    }

    /** 取消订单公共逻辑：改状态为 CANCELLED → 逐个回滚库存（best-effort） → 移除 Redis 超时 ZSet */
    private void doCancelOrder(Order order) {
        order.setStatus(SystemConstants.ORDER_STATUS_CANCELLED);
        orderMapper.updateById(order);

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            try {
                productFeignClient.restoreStock(item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                log.error("回滚库存失败, 继续处理下一项: productId={}", item.getProductId(), e);
            }
        }

        redisTemplate.opsForZSet().remove("cloud:order:timeout", order.getOrderNo());
    }

    /** 事务写入订单主表 + 订单明细表，先插入订单获取 ID 后回填到订单项 */
    private void saveOrderAndItems(Order order, List<OrderItem> orderItems) {
        orderMapper.insert(order);
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }
    }

    // ==================== 卖家订单 ====================

    /** 卖家查看包含自己商品的订单：先查出包含卖家商品的 order_id（分页），再查订单并填充明细 */
    @Override
    public R<List<Order>> getSellerOrders(Long sellerId, Integer page, Integer size) {
        List<Long> sellerProductIds;
        try {
            R<List<ProductDTO>> productResult = productFeignClient.getProductsBySellerId(sellerId);
            if (productResult.getCode() != 200 || productResult.getData() == null
                    || productResult.getData().isEmpty()) {
                return R.ok(Collections.emptyList(), 0);
            }
            sellerProductIds = productResult.getData().stream()
                    .map(ProductDTO::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取卖家商品列表失败, sellerId={}", sellerId, e);
            return R.ok(Collections.emptyList(), 0);
        }

        // 先查出分页的 order_id
        Page<OrderItem> itemPage = new Page<>(page, size);
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getProductId, sellerProductIds)
                .select(OrderItem::getOrderId)
                .groupBy(OrderItem::getOrderId)
                .orderByDesc(OrderItem::getOrderId);
        orderItemMapper.selectPage(itemPage, itemWrapper);

        List<Long> orderIds = itemPage.getRecords().stream()
                .map(OrderItem::getOrderId).distinct().collect(Collectors.toList());

        if (orderIds.isEmpty()) {
            return R.ok(Collections.emptyList(), 0);
        }

        // 查订单并填充明细
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<Order>()
                .in(Order::getId, orderIds)
                .orderByDesc(Order::getCreateTime);
        List<Order> orders = orderMapper.selectList(orderWrapper);

        for (Order order : orders) {
            List<OrderItem> orderItems = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
            order.setOrderItems(orderItems);
        }

        return R.ok(orders, (int) itemPage.getTotal());
    }

    /** 卖家发货：校验订单包含该卖家商品 → 已支付(1) → 已发货(2) */
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
        // 直接查订单明细中的 product_id，然后用 Feign 验证归属
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        // 只传本订单涉及的商品 ID，避免拉取全部商品
        List<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toList());
        Set<Long> sellerProductIds;
        try {
            R<List<ProductDTO>> result = productFeignClient.getProductsBySellerId(sellerId);
            if (result.getCode() != 200 || result.getData() == null) {
                throw new BusinessException("无法验证卖家商品");
            }
            sellerProductIds = result.getData().stream()
                    .map(ProductDTO::getId).collect(Collectors.toSet());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("验证卖家商品失败");
        }
        boolean hasSellerProduct = productIds.stream().anyMatch(sellerProductIds::contains);
        if (!hasSellerProduct) {
            throw new BusinessException("该订单不包含您的商品");
        }

        order.setStatus(SystemConstants.ORDER_STATUS_SHIPPED);
        orderMapper.updateById(order);
        return R.ok("发货成功");
    }

    /** 买家确认收货：校验归属 → 已发货(2) → 已完成(3) */
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

    /** 生成下单幂等 Token（UUID），存入 Redis ORDER_TIMEOUT_MINUTES 分钟，提交订单时消费该 Token 防止重复提交 */
    @Override
    public R<String> generateOrderToken(Long userId) {
        String token = UUID.randomUUID().toString();
        String key = "cloud:order:token:" + userId;
        redisTemplate.opsForValue().set(key, token, ORDER_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        return R.ok(token);
    }
}
