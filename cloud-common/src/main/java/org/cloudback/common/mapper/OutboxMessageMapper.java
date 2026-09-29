package org.cloudback.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.cloudback.common.entity.OutboxMessage;

import java.time.LocalDateTime;

/**
 * @author CloudBack
 * @since 2026-06-03
 */
@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {

    /**
     * 原子化失败标记：retry_count 自增、按最大重试次数决定终态，避免并发下读-改-写丢失更新。
     * 仅允许从待发送(0)/发送中(3)状态流转，影响 0 行说明消息已被其他线程处理。
     */
    @Update("UPDATE outbox_message SET retry_count = retry_count + 1, "
            + "status = CASE WHEN retry_count + 1 >= #{maxRetries} THEN 2 ELSE 0 END, "
            + "next_retry_at = CASE WHEN retry_count + 1 >= #{maxRetries} THEN NULL ELSE #{nextRetryAt} END "
            + "WHERE id = #{id} AND status IN (0, 3)")
    int markFailedAtomic(@Param("id") Long id,
                         @Param("nextRetryAt") LocalDateTime nextRetryAt,
                         @Param("maxRetries") int maxRetries);
}
