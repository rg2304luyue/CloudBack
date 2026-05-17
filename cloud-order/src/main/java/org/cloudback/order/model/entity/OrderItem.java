package org.cloudback.order.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cloudback.common.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * 订单明细实体，映射 order_item 表。
 * 记录订单中每个商品的价格快照（下单时的价格）。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseEntity {

    /** 所属订单 ID */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 商品 ID */
    private Long productId;
    /** 商品名称（快照） */
    private String productName;
    /** 商品图片（快照） */
    private String productImage;
    /** 下单时单价 */
    private BigDecimal price;
    /** 购买数量 */
    private Integer quantity;
    /** 小计金额 */
    private BigDecimal totalAmount;
}
