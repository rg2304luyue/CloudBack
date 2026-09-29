package org.cloudback.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class TwoLevelCacheService {

    /** 空值哨兵：缓存 DB 不存在的 key，防穿透 */
    private static final String NULL_SENTINEL = "__NULL__";
    /** 空值哨兵在 Redis 中的 TTL（秒），短 TTL 便于数据录入后快速生效 */
    private static final long NULL_SENTINEL_TTL_SECONDS = 60L;

    private final Cache<String, Object> caffeine;
    private final RedisTemplate<String, Object> redis;
    private final ObjectProvider<RedissonClient> redissonClientProvider;

    public TwoLevelCacheService(Cache<String, Object> caffeine,
                                RedisTemplate<String, Object> redis,
                                ObjectProvider<RedissonClient> redissonClientProvider) {
        this.caffeine = caffeine;
        this.redis = redis;
        this.redissonClientProvider = redissonClientProvider;
    }

    /** L1(Caffeine) → L2(Redis) → DB */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type, Supplier<T> dbLoader, long redisTtlSeconds) {
        // 1. Caffeine
        Object l1 = caffeine.getIfPresent(key);
        if (l1 != null) {
            log.debug("L1 hit: {}", key);
            return NULL_SENTINEL.equals(l1) ? null : (T) l1;
        }
        // 2. Redis（异常时降级到 DB，不影响服务可用性）
        Object l2 = getFromL2(key);
        if (l2 != null) {
            log.debug("L2 hit: {}", key);
            caffeine.put(key, l2);
            return NULL_SENTINEL.equals(l2) ? null : (T) l2;
        }
        // 3. DB（互斥回源，防击穿）
        return loadFromDbWithLock(key, dbLoader, redisTtlSeconds);
    }

    @SuppressWarnings("unchecked")
    private <T> T loadFromDbWithLock(String key, Supplier<T> dbLoader, long redisTtlSeconds) {
        RLock lock = null;
        boolean locked = false;
        RedissonClient redissonClient = redissonClientProvider == null
                ? null : redissonClientProvider.getIfAvailable();
        if (redissonClient != null) {
            lock = redissonClient.getLock(SystemConstants.CACHE_LOCK_KEY + key);
            try {
                locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("获取缓存回源锁被中断: key={}", key);
            } catch (Exception e) {
                log.warn("获取缓存回源锁失败，直接回源: key={}, error={}", key, e.getMessage());
            }
        }
        try {
            // 拿到锁后双重检查 L2，可能已被持锁线程回填
            if (locked) {
                Object l2 = getFromL2(key);
                if (l2 != null) {
                    log.debug("L2 hit after lock: {}", key);
                    caffeine.put(key, l2);
                    return NULL_SENTINEL.equals(l2) ? null : (T) l2;
                }
            }
            log.debug("Cache miss, load from DB: {}", key);
            T result = dbLoader.get();
            if (result != null) {
                caffeine.put(key, result);
                setToL2(key, result, redisTtlSeconds);
            } else {
                // 空值缓存，防穿透
                caffeine.put(key, NULL_SENTINEL);
                setToL2(key, NULL_SENTINEL, NULL_SENTINEL_TTL_SECONDS);
            }
            return result;
        } finally {
            if (locked && lock != null) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("释放缓存回源锁失败: key={}, error={}", key, e.getMessage());
                }
            }
        }
    }

    private Object getFromL2(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis L2 读取失败，降级到 DB: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    private void setToL2(String key, Object value, long ttlSeconds) {
        try {
            redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis L2 写入失败，数据仅缓存于 L1: key={}, error={}", key, e.getMessage());
        }
    }

    /** 更新DB后调用：先删Redis(L2)，再删本地Caffeine(L1)，并向其他实例广播 L1 失效 */
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis L2 删除失败，仅清除 L1 缓存: key={}, error={}", key, e.getMessage());
        }
        caffeine.invalidate(key);
        try {
            redis.convertAndSend(SystemConstants.CACHE_EVICT_CHANNEL, key);
        } catch (Exception e) {
            log.warn("缓存失效广播失败（其他实例 L1 将依赖 TTL 过期）: key={}, error={}", key, e.getMessage());
        }
        log.debug("Cache evicted (L2→L1+broadcast): {}", key);
    }

    /**
     * 按模式批量失效：删除匹配的 L2 key、本地 L1，并逐个广播。
     * 使用 SCAN 游标遍历而非 KEYS——KEYS 会阻塞 Redis 单线程，在本项目的高频写路径（下单扣库存）上不可接受。
     */
    public void evictByPattern(String pattern) {
        List<String> matched = new ArrayList<>();
        try {
            // RedisTemplate<String,Object>.scan 直接返回已反序列化的 key（泛型为 K）
            Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build());
            try (cursor) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    if (key != null) {
                        matched.add(key);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Redis SCAN 失败: pattern={}, error={}", pattern, e.getMessage());
            return;
        }
        if (matched.isEmpty()) {
            return;
        }
        for (String key : matched) {
            evict(key);
        }
    }
}
