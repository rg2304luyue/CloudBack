package org.cloudback.order.dto;

import org.cloudback.common.entity.OutboxMessage;

import java.time.LocalDateTime;

public record FailedOutboxMessageView(
        Long id,
        String topic,
        String messageKey,
        Integer status,
        Integer retryCount,
        Integer maxRetries,
        LocalDateTime nextRetryAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public static FailedOutboxMessageView from(OutboxMessage message) {
        return new FailedOutboxMessageView(
                message.getId(),
                message.getTopic(),
                message.getMessageKey(),
                message.getStatus(),
                message.getRetryCount(),
                message.getMaxRetries(),
                message.getNextRetryAt(),
                message.getCreateTime(),
                message.getUpdateTime());
    }
}
