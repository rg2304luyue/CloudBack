package org.cloudback.order.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.order.service.OrderService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 订单超时自动取消：双层机制
 * 1. 高频（1秒）：从 Redis ZSet 中取出过期订单取消
 * 2. 低频（5分钟）：DB 兜底扫描，防止 Redis 数据丢失
 * 使用 Redis SETNX 分布式锁防止多实例重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private static final String REDIS_LOCK_KEY = "cloud:order:timeout:lock";
    private static final String DB_LOCK_KEY = "cloud:order:scan:lock";

    private final OrderService orderService;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 高频扫描（每 1 秒）：从 Redis ZSet rangeByScore 取过期订单 → ZREM 移除 → 逐个取消（每次最多 20 条） */
    @Scheduled(fixedRate = 1000)
    public void cancelByRedis() {
        // 分布式锁：SETNX，过期 5 秒防止死锁
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(REDIS_LOCK_KEY, "1", 5, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            return;
        }

        try {
            String key = "cloud:order:timeout";
            long now = System.currentTimeMillis();

            Set<Object> expiredOrders = redisTemplate.opsForZSet()
                    .rangeByScore(key, 0, now, 0, 20);
            if (expiredOrders == null || expiredOrders.isEmpty()) {
                return;
            }

            redisTemplate.opsForZSet().remove(key, expiredOrders.toArray());

            log.info("Redis ZSet 扫描到 {} 个超时订单", expiredOrders.size());
            for (Object obj : expiredOrders) {
                String orderNo = (String) obj;
                try {
                    orderService.cancelOrderByNo(orderNo);
                    log.info("自动取消超时订单: orderNo={}", orderNo);
                } catch (Exception e) {
                    log.error("Redis取消超时订单失败, orderNo={}", orderNo, e);
                }
            }
        } finally {
            redisTemplate.delete(REDIS_LOCK_KEY);
        }
    }

    /** 低频兜底（每 5 分钟）：扫描 DB 中超时未支付订单并取消，防止 Redis ZSet 数据丢失 */
    @Scheduled(fixedRate = 300000)
    public void cancelExpiredOrders() {
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(DB_LOCK_KEY, "1", 60, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            return;
        }

        try {
            int count = orderService.scanExpiredOrders();
            if (count > 0) {
                log.info("DB 兜底扫描取消 {} 个超时订单", count);
            }
        } catch (Exception e) {
            log.error("DB 兜底扫描异常", e);
        } finally {
            redisTemplate.delete(DB_LOCK_KEY);
        }
    }
}
