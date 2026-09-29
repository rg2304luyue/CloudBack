package org.cloudback.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.entity.OutboxMessage;
import org.cloudback.common.mapper.OutboxMessageMapper;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OutboxService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_SENT = 1;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_SENDING = 3;
    private static final int MAX_RETRIES = 5;

    private final OutboxMessageMapper outboxMessageMapper;

    public void saveMessage(String topic, String messageKey, String payload) {
        OutboxMessage msg = new OutboxMessage();
        msg.setTopic(topic);
        msg.setMessageKey(messageKey);
        msg.setPayload(payload);
        msg.setStatus(STATUS_PENDING);
        msg.setRetryCount(0);
        msg.setMaxRetries(MAX_RETRIES);
        msg.setCreateTime(LocalDateTime.now());
        outboxMessageMapper.insert(msg);
    }

    public List<OutboxMessage> pollPending(String topic, int limit) {
        LambdaQueryWrapper<OutboxMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutboxMessage::getTopic, topic)
                .eq(OutboxMessage::getStatus, STATUS_PENDING)
                .and(w -> w.isNull(OutboxMessage::getNextRetryAt)
                        .or().le(OutboxMessage::getNextRetryAt, LocalDateTime.now()))
                .orderByAsc(OutboxMessage::getCreateTime)
                .last("LIMIT " + limit);
        return outboxMessageMapper.selectList(wrapper);
    }

    public boolean claimMessage(Long id) {
        OutboxMessage msg = new OutboxMessage();
        msg.setStatus(STATUS_SENDING);
        msg.setNextRetryAt(LocalDateTime.now().plusMinutes(2));
        int rows = outboxMessageMapper.update(msg, new LambdaUpdateWrapper<OutboxMessage>()
                .eq(OutboxMessage::getId, id)
                .eq(OutboxMessage::getStatus, STATUS_PENDING));
        return rows > 0;
    }

    public void recoverTimedOutSending(String topic) {
        OutboxMessage msg = new OutboxMessage();
        msg.setStatus(STATUS_PENDING);
        int rows = outboxMessageMapper.update(msg, new LambdaUpdateWrapper<OutboxMessage>()
                .eq(OutboxMessage::getTopic, topic)
                .eq(OutboxMessage::getStatus, STATUS_SENDING)
                .le(OutboxMessage::getNextRetryAt, LocalDateTime.now()));
        if (rows > 0) {
            log.warn("Recovered {} timed-out outbox messages for topic={}", rows, topic);
        }
    }

    public void markSent(Long id) {
        OutboxMessage msg = new OutboxMessage();
        msg.setStatus(STATUS_SENT);
        msg.setNextRetryAt(null);
        // 条件更新：仅允许从发送中(3)流转，影响 0 行说明已被 recover 或其他线程处理
        int rows = outboxMessageMapper.update(msg, new LambdaUpdateWrapper<OutboxMessage>()
                .eq(OutboxMessage::getId, id)
                .eq(OutboxMessage::getStatus, STATUS_SENDING));
        if (rows == 0) {
            log.warn("markSent 未生效（消息可能已被 recover）: id={}", id);
        }
    }

    public void markFailed(Long id, int retryCount) {
        // 指数退避：2^retryCount 秒（retryCount 为自增后的次数）
        long delaySeconds = (long) Math.pow(2, retryCount + 1);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        int rows = outboxMessageMapper.markFailedAtomic(id, nextRetryAt, MAX_RETRIES);
        if (rows == 0) {
            log.warn("markFailed 未生效（消息可能已被其他线程处理）: id={}", id);
        } else if (retryCount + 1 >= MAX_RETRIES) {
            log.error("Outbox message permanently failed: id={}", id);
        }
    }

    public List<OutboxMessage> listFailed(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return outboxMessageMapper.selectList(new LambdaQueryWrapper<OutboxMessage>()
                .eq(OutboxMessage::getStatus, STATUS_FAILED)
                .orderByAsc(OutboxMessage::getCreateTime)
                .last("LIMIT " + safeLimit));
    }

    public long countFailed() {
        return outboxMessageMapper.selectCount(new LambdaQueryWrapper<OutboxMessage>()
                .eq(OutboxMessage::getStatus, STATUS_FAILED));
    }

    public boolean requeueFailed(Long id) {
        int rows = outboxMessageMapper.update(null, new LambdaUpdateWrapper<OutboxMessage>()
                .eq(OutboxMessage::getId, id)
                .eq(OutboxMessage::getStatus, STATUS_FAILED)
                .set(OutboxMessage::getStatus, STATUS_PENDING)
                .set(OutboxMessage::getRetryCount, 0)
                .set(OutboxMessage::getNextRetryAt, null));
        return rows == 1;
    }
}
