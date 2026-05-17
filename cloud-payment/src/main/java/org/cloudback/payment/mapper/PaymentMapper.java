package org.cloudback.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.payment.model.entity.Payment;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
