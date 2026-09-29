package org.cloudback.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnClass({RedisTemplate.class, RedissonClient.class})
public class CaffeineConfig {

    // ===== Caffeine 实例 =====
    // L1 统一 30s，短 TTL 让其他节点快速感知数据变更

    @Bean
    public Cache<String, Object> productDetailCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
    }

    @Bean
    public Cache<String, Object> hotProductsCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(5)
                .build();
    }

    @Bean
    public Cache<String, Object> productIdListCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(200)
                .build();
    }

    @Bean
    public Cache<String, Object> cartCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(500)
                .build();
    }

    // ===== TwoLevelCacheService 实例（绑定 Cache + Redis） =====

    @Bean
    public TwoLevelCacheService productDetailTwoLevel(
            Cache<String, Object> productDetailCache, RedisTemplate<String, Object> redis,
            ObjectProvider<RedissonClient> redissonClientProvider) {
        return new TwoLevelCacheService(productDetailCache, redis, redissonClientProvider);
    }

    @Bean
    public TwoLevelCacheService hotProductsTwoLevel(
            Cache<String, Object> hotProductsCache, RedisTemplate<String, Object> redis,
            ObjectProvider<RedissonClient> redissonClientProvider) {
        return new TwoLevelCacheService(hotProductsCache, redis, redissonClientProvider);
    }

    @Bean
    public TwoLevelCacheService productIdListTwoLevel(
            Cache<String, Object> productIdListCache, RedisTemplate<String, Object> redis,
            ObjectProvider<RedissonClient> redissonClientProvider) {
        return new TwoLevelCacheService(productIdListCache, redis, redissonClientProvider);
    }

    @Bean
    public TwoLevelCacheService cartTwoLevel(
            Cache<String, Object> cartCache, RedisTemplate<String, Object> redis,
            ObjectProvider<RedissonClient> redissonClientProvider) {
        return new TwoLevelCacheService(cartCache, redis, redissonClientProvider);
    }
}
