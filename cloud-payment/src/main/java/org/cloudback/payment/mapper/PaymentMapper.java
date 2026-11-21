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

    /**
     * 反向对账抢占：已支付(1) 以及可能已扣款的已失败(2)/已关闭(3)，复用 lastSyncTime 节流。
     * 2/3 这两种状态是「订单取消后用户才在收银台付款」的落点，必须纳入抢占，
     * 否则它们会被调度反复选中却永远抢不到，这笔钱就没有任何兜底。
     */
    @Update("UPDATE payment SET last_sync_time = #{claimedAt} WHERE id = #{id} AND status IN (1, 2, 3) AND (last_sync_time IS NULL OR last_sync_time <= #{cutoff})")
    int claimPaidSync(@Param("id") Long id,
                      @Param("cutoff") LocalDateTime cutoff,
                      @Param("claimedAt") LocalDateTime claimedAt);
}
