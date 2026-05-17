package org.cloudback.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.order.model.entity.OrderItem;

/**
 * 订单明细 Mapper，提供订单明细 CRUD 操作。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
