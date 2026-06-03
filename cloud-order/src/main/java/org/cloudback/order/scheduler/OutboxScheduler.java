package org.cloudback.order.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.entity.OutboxMessage;
import org.cloudback.common.service.OutboxService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 消息定时发送器（订单模块）。
 * 每 5 秒扫描 outbox_message 表，发送 PENDING 消息到 Kafka。
 *
 * @author CloudBack
 * @since 2026-06-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_KEY = "cloud:outbox:order:lock";
    private static final int BATCH_SIZE = 20;

    @Scheduled(fixedRate = 5000)
    public void sendPendingMessages() {
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", 10, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            return;
        }

        try {
            List<OutboxMessage> pending = outboxService.pollPending(BATCH_SIZE);
            for (OutboxMessage msg : pending) {
                try {
                    kafkaTemplate.send(msg.getTopic(), msg.getMessageKey(), msg.getPayload()).get(3, TimeUnit.SECONDS);
                    outboxService.markSent(msg.getId());
                    log.debug("Outbox 消息发送成功: id={}, topic={}, key={}", msg.getId(), msg.getTopic(), msg.getMessageKey());
                } catch (Exception e) {
                    log.error("Outbox 消息发送失败: id={}, topic={}, key={}, error={}",
                            msg.getId(), msg.getTopic(), msg.getMessageKey(), e.getMessage());
                    outboxService.markFailed(msg.getId(), msg.getRetryCount());
                }
            }
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }
}
