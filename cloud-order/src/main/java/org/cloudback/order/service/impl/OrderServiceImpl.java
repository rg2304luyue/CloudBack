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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartFeignClient cartFeignClient;
    private final ProductFeignClient productFeignClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<Order> createOrder(Long userId, Long addressId, String remark) {
        // 1. 从购物车获取已勾选商品
        R<List<Map<String, Object>>> cartResult = cartFeignClient.getCheckedItems();
        if (cartResult.getData() == null || cartResult.getData().isEmpty()) {
            throw new BusinessException("购物车中没有已勾选的商品");
        }

        List<Map<String, Object>> checkedItems = cartResult.getData();

        // 2. 扣减库存
        for (Map<String, Object> item : checkedItems) {
            Long productId = Long.valueOf(item.get("productId").toString());
            Integer quantity = Integer.valueOf(item.get("quantity").toString());
            R<String> stockResult = productFeignClient.deductStock(productId, quantity);
            if (stockResult.getCode() != 200) {
                throw new BusinessException(stockResult.getCode(), stockResult.getMessage());
            }
        }

        // 3. 计算总金额 + 构建订单明细
        String orderNo = IdUtil.getSnowflakeNextIdStr();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (Map<String, Object> item : checkedItems) {
            Long productId = Long.valueOf(item.get("productId").toString());
            String productName = (String) item.get("productName");
            String productImage = (String) item.get("productImage");
            BigDecimal price = new BigDecimal(item.get("price").toString());
            Integer quantity = Integer.valueOf(item.get("quantity").toString());

            BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(quantity));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(null); // 等下关联
            orderItem.setOrderNo(orderNo);
            orderItem.setProductId(productId);
            orderItem.setProductName(productName);
            orderItem.setProductImage(productImage);
            orderItem.setPrice(price);
            orderItem.setQuantity(quantity);
            orderItem.setTotalAmount(itemTotal);
            orderItems.add(orderItem);
        }

        // 4. 创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(SystemConstants.ORDER_STATUS_UNPAID);
        order.setRemark(remark);
        // 查询收货地址
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
        orderMapper.insert(order);

        // 5. 关联订单ID并保存订单明细
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 6. 清空购物车中已下单的商品
        cartFeignClient.clearCart();

        // 7. 发送Kafka消息通知支付服务
        Map<String, Object> kafkaMsg = new HashMap<>();
        kafkaMsg.put("orderId", order.getId());
        kafkaMsg.put("orderNo", orderNo);
        kafkaMsg.put("userId", userId);
        kafkaMsg.put("totalAmount", totalAmount);
        kafkaMsg.put("createTime", order.getCreateTime().toString());
        kafkaTemplate.send(SystemConstants.KAFKA_TOPIC_ORDER_CREATE, JSON.toJSONString(kafkaMsg));

        log.info("订单创建成功: orderNo={}, totalAmount={}", orderNo, totalAmount);
        return R.ok("下单成功", order);
    }

    @Override
    public R<Order> getOrderDetail(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        return R.ok(order);
    }

    @Override
    public R<List<Order>> getOrderList(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime);
        Page<Order> orderPage = new Page<>(page, size);
        orderMapper.selectPage(orderPage, wrapper);
        return R.ok(orderPage.getRecords());
    }

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
        return R.ok("订单已取消");
    }
}
