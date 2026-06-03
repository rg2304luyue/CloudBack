package org.cloudback.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.common.entity.OutboxMessage;

/**
 * @author CloudBack
 * @since 2026-06-03
 */
@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {
}
