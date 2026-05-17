package org.cloudback.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.payment.model.entity.Payment;

/**
 * 支付 Mapper，提供支付记录 CRUD 操作。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
