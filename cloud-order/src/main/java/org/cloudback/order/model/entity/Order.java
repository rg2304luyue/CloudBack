package org.cloudback.order.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cloudback.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，映射 order_info 表。
 * orderNo 使用雪花算法生成唯一订单号。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_info")
public class Order extends BaseEntity {

    /** 订单号，唯一 */
    private String orderNo;
    /** 下单用户 ID */
    private Long userId;
    /** 订单总金额 */
    private BigDecimal totalAmount;
    /** 状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消 */
    private Integer status;
    /** 收货人 */
    private String receiverName;
    /** 联系电话 */
    private String receiverPhone;
    /** 收货地址全称 */
    private String receiverAddress;
    /** 备注 */
    private String remark;
    /** 支付时间 */
    private LocalDateTime payTime;
}
