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

    private final OutboxMessageMapper outboxMessageMapper;

    public void saveMessage(String topic, String messageKey, String payload) {
        OutboxMessage msg = new OutboxMessage();
        msg.setTopic(topic);
        msg.setMessageKey(messageKey);
        msg.setPayload(payload);
        msg.setStatus(STATUS_PENDING);
        msg.setRetryCount(0);
        msg.setMaxRetries(5);
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
        msg.setId(id);
        msg.setStatus(STATUS_SENT);
        msg.setNextRetryAt(null);
        outboxMessageMapper.updateById(msg);
    }

    public void markFailed(Long id, int retryCount) {
        OutboxMessage msg = new OutboxMessage();
        msg.setId(id);
        msg.setRetryCount(retryCount + 1);
        if (msg.getRetryCount() >= 5) {
            msg.setStatus(STATUS_FAILED);
            log.error("Outbox message permanently failed: id={}", id);
        } else {
            msg.setStatus(STATUS_PENDING);
            long delaySeconds = (long) Math.pow(2, msg.getRetryCount());
            msg.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        }
        outboxMessageMapper.updateById(msg);
    }
}
