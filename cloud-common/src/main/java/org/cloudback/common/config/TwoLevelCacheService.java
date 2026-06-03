package org.cloudback.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class TwoLevelCacheService {

    private final Cache<String, Object> caffeine;
    private final RedisTemplate<String, Object> redis;

    /** L1(Caffeine) → L2(Redis) → DB */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type, Supplier<T> dbLoader, long redisTtlSeconds) {
        // 1. Caffeine
        Object l1 = caffeine.getIfPresent(key);
        if (l1 != null) {
            log.debug("L1 hit: {}", key);
            return (T) l1;
        }
        // 2. Redis（异常时降级到 DB，不影响服务可用性）
        Object l2 = null;
        try {
            l2 = redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis L2 读取失败，降级到 DB: key={}, error={}", key, e.getMessage());
        }
        if (l2 != null) {
            log.debug("L2 hit: {}", key);
            caffeine.put(key, l2);
            return (T) l2;
        }
        // 3. DB
        log.debug("Cache miss, load from DB: {}", key);
        T result = dbLoader.get();
        if (result != null) {
            caffeine.put(key, result);
            try {
                redis.opsForValue().set(key, result, redisTtlSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis L2 写入失败，数据仅缓存于 L1: key={}, error={}", key, e.getMessage());
            }
        }
        return result;
    }

    /** 更新DB后调用：先删Redis(L2)，再删本地Caffeine(L1)，保证一致性 */
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis L2 删除失败，仅清除 L1 缓存: key={}, error={}", key, e.getMessage());
        }
        caffeine.invalidate(key);
        log.debug("Cache evicted (L2→L1): {}", key);
    }
}
