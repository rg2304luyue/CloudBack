package org.cloudback.cart.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.cart.dto.CartItem;
import org.cloudback.cart.dto.ProductInfo;
import org.cloudback.cart.feign.ProductFeignClient;
import org.cloudback.cart.service.CartService;
import org.cloudback.common.config.TwoLevelCacheService;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final TwoLevelCacheService cartTwoLevel;
    private final RedissonClient redissonClient;
    private static final long CART_TTL_DAYS = 7;
    private static final int CART_QUANTITY_MAX = 999;

    /** 生成购物车 Redis Key */
    private String cartKey(Long userId) {
        return SystemConstants.CART_KEY_PREFIX + userId;
    }

    /** 添加商品到购物车，已存在则累加数量，通过 Feign 查询商品信息（分布式锁保护并发） */
    @Override
    public R<String> addItem(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("商品数量必须大于0");
        }
        if (quantity > CART_QUANTITY_MAX) {
            throw new BusinessException("商品数量不能超过" + CART_QUANTITY_MAX);
        }
        R<CartItem> result = productFeignClient.getProductDetail(productId);
        if (result.getCode() != 200 || result.getData() == null) {
            if (result.getCode() == ResultCode.SERVICE_UNAVAILABLE.getCode()) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE);
            }
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        CartItem productInfo = result.getData();

        String key = cartKey(userId);
        String field = String.valueOf(productId);

        RLock lock = redissonClient.getLock(key + ":lock");
        try {
            lock.lock(5, TimeUnit.SECONDS);

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
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return R.ok("添加购物车成功");
    }

    /** 更新商品数量（分布式锁保护并发） */
    @Override
    public R<String> updateQuantity(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("商品数量必须大于0");
        }
        if (quantity > CART_QUANTITY_MAX) {
            throw new BusinessException("商品数量不能超过" + CART_QUANTITY_MAX);
        }

        String key = cartKey(userId);
        String field = String.valueOf(productId);

        RLock lock = redissonClient.getLock(key + ":lock");
        try {
            lock.lock(5, TimeUnit.SECONDS);

            Object existing = redisTemplate.opsForHash().get(key, field);
            if (existing == null) {
                throw new BusinessException("购物车中不存在该商品");
            }

            CartItem cartItem = JSON.parseObject(existing.toString(), CartItem.class);
            cartItem.setQuantity(quantity);
            redisTemplate.opsForHash().put(key, field, JSON.toJSONString(cartItem));
            redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);
            evictCartCache(userId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return R.ok("更新数量成功");
    }

    /** 移除商品（HDEL，分布式锁保护并发） */
    @Override
    public R<String> removeItem(Long userId, Long productId) {
        String key = cartKey(userId);
        RLock lock = redissonClient.getLock(key + ":lock");
        try {
            lock.lock(5, TimeUnit.SECONDS);
            redisTemplate.opsForHash().delete(key, String.valueOf(productId));
            evictCartCache(userId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return R.ok("移除成功");
    }

    /** 勾选/取消勾选商品（分布式锁保护并发） */
    @Override
    public R<String> checkItem(Long userId, Long productId, Boolean checked) {
        String key = cartKey(userId);
        String field = String.valueOf(productId);

        RLock lock = redissonClient.getLock(key + ":lock");
        try {
            lock.lock(5, TimeUnit.SECONDS);

            Object existing = redisTemplate.opsForHash().get(key, field);
            if (existing == null) {
                throw new BusinessException("购物车中不存在该商品");
            }

            CartItem cartItem = JSON.parseObject(existing.toString(), CartItem.class);
            cartItem.setChecked(Boolean.TRUE.equals(checked));
            redisTemplate.opsForHash().put(key, field, JSON.toJSONString(cartItem));
            evictCartCache(userId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return R.ok(Boolean.TRUE.equals(checked) ? "已勾选" : "已取消勾选");
    }

    /** 批量勾选/取消勾选所有商品（分布式锁保护并发） */
    @Override
    public R<String> checkAllItems(Long userId, Boolean checked) {
        String key = cartKey(userId);

        RLock lock = redissonClient.getLock(key + ":lock");
        try {
            lock.lock(5, TimeUnit.SECONDS);

            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) {
                return R.ok("购物车为空");
            }

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                CartItem cartItem = JSON.parseObject(entry.getValue().toString(), CartItem.class);
                cartItem.setChecked(Boolean.TRUE.equals(checked));
                redisTemplate.opsForHash().put(key, entry.getKey(), JSON.toJSONString(cartItem));
            }

            evictCartCache(userId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return R.ok(Boolean.TRUE.equals(checked) ? "已全选" : "已取消全选");
    }

    /** 清空购物车（DEL 整个 Key，分布式锁保护并发） */
    @Override
    public R<String> clearCart(Long userId) {
        String key = cartKey(userId);
        RLock lock = redissonClient.getLock(key + ":lock");
        try {
            lock.lock(5, TimeUnit.SECONDS);
            redisTemplate.delete(key);
            evictCartCache(userId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return R.ok("购物车已清空");
    }

    /** 获取全部购物车商品（HGETALL），按 productId 排序；返回前用商品服务实时数据覆盖价格/状态/库存 */
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

        if (items == null || items.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        return R.ok(refreshWithRealtimeProducts(items));
    }

    /**
     * 用商品服务实时数据瞬态覆盖返回对象的价格/状态/库存（不落库改快照）。
     * 商品服务不可用时降级返回原快照；商品不存在/已删除时标记 status=0（前端置灰）。
     */
    private List<CartItem> refreshWithRealtimeProducts(List<CartItem> items) {
        List<Long> ids = items.stream()
                .map(CartItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, ProductInfo> productMap = null;
        if (!ids.isEmpty()) {
            try {
                R<List<ProductInfo>> result = productFeignClient.getProductsByIds(ids);
                if (result.getCode() == 200 && result.getData() != null) {
                    productMap = result.getData().stream()
                            .filter(p -> p != null && p.getId() != null)
                            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
                } else {
                    log.warn("批量查询商品信息失败，降级使用购物车快照: code={}", result.getCode());
                }
            } catch (Exception e) {
                log.warn("批量查询商品信息异常，降级使用购物车快照: error={}", e.getMessage());
            }
        }

        // 拷贝后再覆盖，避免污染 L1/L2 缓存中的共享对象
        List<CartItem> result = new ArrayList<>(items.size());
        for (CartItem item : items) {
            CartItem copy = copyItem(item);
            if (productMap != null) {
                ProductInfo p = productMap.get(item.getProductId());
                if (p == null) {
                    // 商品不存在（已删除），标记下架，前端置灰
                    copy.setStatus(0);
                } else {
                    copy.setPrice(p.getPrice());
                    copy.setStatus(p.getStatus());
                    copy.setStock(p.getStock());
                }
            }
            result.add(copy);
        }
        return result;
    }

    private CartItem copyItem(CartItem item) {
        CartItem copy = new CartItem();
        copy.setProductId(item.getProductId());
        copy.setName(item.getName());
        copy.setMainImage(item.getMainImage());
        copy.setPrice(item.getPrice());
        copy.setQuantity(item.getQuantity());
        copy.setChecked(item.getChecked());
        copy.setStatus(item.getStatus());
        copy.setStock(item.getStock());
        return copy;
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
                    .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                    .collect(Collectors.toList());
        }, 300);

        return R.ok(items != null ? items : Collections.emptyList());
    }

    private void evictCartCache(Long userId) {
        cartTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "cart:cache:" + userId);
        cartTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "cart:cache:checked:" + userId);
    }
}
