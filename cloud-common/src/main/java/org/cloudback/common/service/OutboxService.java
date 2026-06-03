package org.cloudback.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.entity.OutboxMessage;
import org.cloudback.common.mapper.OutboxMessageMapper;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息发件箱服务，提供消息持久化和轮询功能。
 * 不在 common 层注册 Bean（避免非 MyBatis 模块启动失败），
 * 由 cloud-order / cloud-payment 各自的 @Bean 显式声明。
 *
 * @author CloudBack
 * @since 2026-06-03
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMessageMapper outboxMessageMapper;

    /** 持久化一条待发送消息（应在业务事务内调用） */
    public void saveMessage(String topic, String messageKey, String payload) {
        OutboxMessage msg = new OutboxMessage();
        msg.setTopic(topic);
        msg.setMessageKey(messageKey);
        msg.setPayload(payload);
        msg.setStatus(0);
        msg.setRetryCount(0);
        msg.setMaxRetries(5);
        msg.setCreateTime(LocalDateTime.now());
        outboxMessageMapper.insert(msg);
    }

    /** 查询待发送的消息（status=0 AND next_retry_at <= now 或 next_retry_at IS NULL），按创建时间排序，限制条数 */
    public List<OutboxMessage> pollPending(int limit) {
        LambdaQueryWrapper<OutboxMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OutboxMessage::getStatus, 0)
                .and(w -> w.isNull(OutboxMessage::getNextRetryAt)
                        .or().le(OutboxMessage::getNextRetryAt, LocalDateTime.now()))
                .orderByAsc(OutboxMessage::getCreateTime)
                .last("LIMIT " + limit);
        return outboxMessageMapper.selectList(wrapper);
    }

    /** 标记消息已发送 */
    public void markSent(Long id) {
        OutboxMessage msg = new OutboxMessage();
        msg.setId(id);
        msg.setStatus(1);
        outboxMessageMapper.updateById(msg);
    }

    /** 标记消息发送失败，计算指数退避重试时间 */
    public void markFailed(Long id, int retryCount) {
        OutboxMessage msg = new OutboxMessage();
        msg.setId(id);
        msg.setRetryCount(retryCount + 1);
        if (msg.getRetryCount() >= 5) {
            msg.setStatus(2); // 超过最大重试，标记失败
            log.error("Outbox 消息发送最终失败: id={}", id);
        } else {
            // 指数退避：2^retryCount 秒后重试
            long delaySeconds = (long) Math.pow(2, msg.getRetryCount());
            msg.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        }
        outboxMessageMapper.updateById(msg);
    }
}
