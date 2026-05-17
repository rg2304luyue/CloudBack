package org.cloudback.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.order.model.entity.Order;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
