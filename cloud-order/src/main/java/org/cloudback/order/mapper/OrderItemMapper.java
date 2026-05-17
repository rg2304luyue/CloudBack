package org.cloudback.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.order.model.entity.OrderItem;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
