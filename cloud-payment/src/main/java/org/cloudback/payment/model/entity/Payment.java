package org.cloudback.payment.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cloudback.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体，映射 payment 表。
 * tradeNo 存储第三方交易号（支付宝/微信），对接真实支付后填充。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment")
public class Payment extends BaseEntity {

    /** 订单号 */
    private String orderNo;
    /** 用户 ID */
    private Long userId;
    /** 支付金额 */
    private BigDecimal amount;
    /** 支付方式: ALIPAY / WECHAT */
    private String payMethod;
    /** 支付状态: 0-待支付, 1-支付成功, 2-支付失败 */
    private Integer status;
    /** 第三方交易号 */
    private String tradeNo;
    /** 实际支付时间 */
    private LocalDateTime payTime;
    /** 上次同步支付宝状态时间（限制查询频率） */
    private LocalDateTime lastSyncTime;
}
