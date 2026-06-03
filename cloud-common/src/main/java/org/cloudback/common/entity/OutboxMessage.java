package org.cloudback.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息发件箱实体，用于保证 Kafka 消息可靠投递。
 * 消息先写入 outbox_message 表（与业务在同一事务），后台调度器异步发送。
 *
 * @author CloudBack
 * @since 2026-06-03
 */
@Data
@TableName("outbox_message")
public class OutboxMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** Kafka Topic */
    private String topic;
    /** 消息 Key（如 orderNo） */
    private String messageKey;
    /** 消息体 JSON */
    private String payload;
    /** 状态: 0-待发送 1-已发送 2-失败 */
    private Integer status;
    /** 已重试次数 */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetries;
    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
