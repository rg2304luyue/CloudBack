package org.cloudback.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.cloudback.payment.model.entity.Payment;

import java.time.LocalDateTime;

/**
 * 支付 Mapper，提供支付记录 CRUD 操作。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Update("UPDATE payment SET last_sync_time = #{claimedAt} WHERE id = #{id} AND status = 0 AND (last_sync_time IS NULL OR last_sync_time <= #{cutoff})")
    int claimSync(@Param("id") Long id,
                  @Param("cutoff") LocalDateTime cutoff,
                  @Param("claimedAt") LocalDateTime claimedAt);
}
