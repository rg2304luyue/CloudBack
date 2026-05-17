package org.cloudback.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.order.model.entity.Order;

/**
 * 订单 Mapper，提供订单 CRUD 和分页查询操作。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
