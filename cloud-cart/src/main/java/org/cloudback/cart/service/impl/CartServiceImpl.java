package org.cloudback.cart.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.cart.dto.CartItem;
import org.cloudback.cart.feign.ProductFeignClient;
import org.cloudback.cart.service.CartService;
import org.cloudback.common.config.TwoLevelCacheService;
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

/**
 * 购物车服务实现，所有数据存储在 Redis Hash 中。
 * Key: cloud:cart:{userId}  Field: productId  Value: CartItem JSON
 * 新增商品时通过 Feign 查询商品服务获取名称、价格等信息。
 * 购物车有效期 7 天，每次操作续期。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductFeignClient productFeignClient;
    private static final long CART_TTL_DAYS = 7;

    private final TwoLevelCacheService cartTwoLevel;


    /** 生成购物车 Redis Key */
    private String cartKey(Long userId) {
        return SystemConstants.CART_KEY_PREFIX + userId;
    }

    /** 添加商品到购物车，已存在则累加数量，通过 Feign 查询商品信息 */
    @Override
    public R<String> addItem(Long userId, Long productId, Integer quantity) {
        R<CartItem> result = productFeignClient.getProductDetail(productId);
        if (result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        CartItem productInfo = result.getData();

        String key = cartKey(userId);
        String field = String.valueOf(productId);

        Object existing = redisTemplate.opsForHash().get(key, field);
        if (existing != null) {
            CartItem cartItem = JSON.parseObject(existing.toString(), CartItem.class);
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
            cartItem.setChecked(true);
            cartItem.setName(productInfo.getName());
            cartItem.setMainImage(productInfo.getMainImage());
            cartItem.setPrice(productInfo.getPrice());
            redisTemplate.opsForHash().put(key, field, JSON.toJSONString(cartItem));
        } else {
            CartItem cartItem = new CartItem();
            cartItem.setProductId(productId);
            cartItem.setName(productInfo.getName());
            cartItem.setMainImage(productInfo.getMainImage());
            cartItem.setPrice(productInfo.getPrice());
            cartItem.setQuantity(quantity);
            cartItem.setChecked(true);
            redisTemplate.opsForHash().put(key, field, JSON.toJSONString(cartItem));
        }

        redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);
        evictCartCache(userId);
        return R.ok("添加购物车成功");
    }

    /** 更新商品数量 */
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
        evictCartCache(userId);
        return R.ok("更新数量成功");
    }

    /** 移除商品（HDEL） */
    @Override
    public R<String> removeItem(Long userId, Long productId) {
        redisTemplate.opsForHash().delete(cartKey(userId), String.valueOf(productId));
        evictCartCache(userId);
        return R.ok("移除成功");
    }

    /** 勾选/取消勾选商品 */
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
        evictCartCache(userId);
        return R.ok(checked ? "已勾选" : "已取消勾选");
    }

    /** 清空购物车（DEL 整个 Key） */
    @Override
    public R<String> clearCart(Long userId) {
        redisTemplate.delete(cartKey(userId));
        evictCartCache(userId);
        return R.ok("购物车已清空");
    }

    /** 获取全部购物车商品（HGETALL），按 productId 排序 */
    @Override
    public R<List<CartItem>> getCartList(Long userId) {
        String cacheKey = SystemConstants.REDIS_KEY_PREFIX + "cart:cache:" + userId;

        List<CartItem> items = cartTwoLevel.get(cacheKey, List.class, () -> {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey(userId));
            if (entries.isEmpty()) {
                return Collections.emptyList();
            }
            return entries.values().stream()
                    .map(v -> JSON.parseObject(v.toString(), CartItem.class))
                    .sorted(Comparator.comparing(CartItem::getProductId))
                    .collect(Collectors.toList());
        }, 300);

        return R.ok(items != null ? items : Collections.emptyList());
    }

    /** 获取已勾选商品（下单时用），过滤 checked=true */
    @Override
    public R<List<CartItem>> getCheckedItems(Long userId) {
        String cacheKey = SystemConstants.REDIS_KEY_PREFIX + "cart:cache:checked:" + userId;

        List<CartItem> items = cartTwoLevel.get(cacheKey, List.class, () -> {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey(userId));
            if (entries.isEmpty()) {
                return Collections.emptyList();
            }
            return entries.values().stream()
                    .map(v -> JSON.parseObject(v.toString(), CartItem.class))
                    .filter(CartItem::getChecked)
                    .collect(Collectors.toList());
        }, 300);

        return R.ok(items != null ? items : Collections.emptyList());
    }

    private void evictCartCache(Long userId) {
        cartTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "cart:cache:" + userId);
        cartTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "cart:cache:checked:" + userId);
    }
}
