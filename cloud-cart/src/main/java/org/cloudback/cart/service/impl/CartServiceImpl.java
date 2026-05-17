package org.cloudback.cart.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.cart.dto.CartItem;
import org.cloudback.cart.feign.ProductFeignClient;
import org.cloudback.cart.service.CartService;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductFeignClient productFeignClient;
    private static final long CART_TTL_DAYS = 7;

    private String cartKey(Long userId) {
        return SystemConstants.CART_KEY_PREFIX + userId;
    }

    @Override
    public R<String> addItem(Long userId, Long productId, Integer quantity) {
        // 查询商品信息（调用product服务）
        R<CartItem> result = productFeignClient.getProductDetail(productId);
        if (result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }

        String key = cartKey(userId);
        String field = String.valueOf(productId);

        // 检查购物车是否已存在该商品
        Object existing = redisTemplate.opsForHash().get(key, field);
        if (existing != null) {
            // 已存在，累加数量
            CartItem cartItem = JSON.parseObject(existing.toString(), CartItem.class);
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
            cartItem.setChecked(true);
            redisTemplate.opsForHash().put(key, field, JSON.toJSONString(cartItem));
        } else {
            // 新商品，创建CartItem
            CartItem cartItem = new CartItem();
            cartItem.setProductId(productId);
            cartItem.setProductName(result.getData().getProductName());
            cartItem.setProductImage(result.getData().getProductImage());
            cartItem.setPrice(result.getData().getPrice());
            cartItem.setQuantity(quantity);
            cartItem.setChecked(true); // 默认勾选
            redisTemplate.opsForHash().put(key, field, JSON.toJSONString(cartItem));
        }

        redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);
        return R.ok("添加购物车成功");
    }

    @Override
    public R<String> updateQuantity(Long userId, Long productId, Integer quantity) {
        String key = cartKey(userId);
        String field = String.valueOf(productId);

        Object existing = redisTemplate.opsForHash().get(key, field);
        if (existing == null) {
            throw new BusinessException("购物车中不存在该商品");
        }

        CartItem cartItem = JSON.parseObject(existing.toString(), CartItem.class);
        cartItem.setQuantity(quantity);
        redisTemplate.opsForHash().put(key, field, JSON.toJSONString(cartItem));
        redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);
        return R.ok("更新数量成功");
    }

    @Override
    public R<String> removeItem(Long userId, Long productId) {
        redisTemplate.opsForHash().delete(cartKey(userId), String.valueOf(productId));
        return R.ok("移除成功");
    }

    @Override
    public R<String> checkItem(Long userId, Long productId, Boolean checked) {
        String key = cartKey(userId);
        String field = String.valueOf(productId);

        Object existing = redisTemplate.opsForHash().get(key, field);
        if (existing == null) {
            throw new BusinessException("购物车中不存在该商品");
        }

        CartItem cartItem = JSON.parseObject(existing.toString(), CartItem.class);
        cartItem.setChecked(checked);
        redisTemplate.opsForHash().put(key, field, JSON.toJSONString(cartItem));
        return R.ok(checked ? "已勾选" : "已取消勾选");
    }

    @Override
    public R<String> clearCart(Long userId) {
        redisTemplate.delete(cartKey(userId));
        return R.ok("购物车已清空");
    }

    @Override
    public R<List<CartItem>> getCartList(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey(userId));
        if (entries.isEmpty()) {
            return R.ok(Collections.emptyList());
        }

        List<CartItem> items = entries.values().stream()
                .map(v -> JSON.parseObject(v.toString(), CartItem.class))
                .sorted(Comparator.comparing(CartItem::getProductId))
                .collect(Collectors.toList());

        return R.ok(items);
    }

    @Override
    public R<List<CartItem>> getCheckedItems(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey(userId));
        if (entries.isEmpty()) {
            return R.ok(Collections.emptyList());
        }

        List<CartItem> items = entries.values().stream()
                .map(v -> JSON.parseObject(v.toString(), CartItem.class))
                .filter(CartItem::getChecked)
                .collect(Collectors.toList());

        return R.ok(items);
    }
}
