package org.cloudback.cart.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 购物车项 DTO，存储在 Redis Hash 中，不对应数据库表。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
public class CartItem {

    /** 商品 ID */
    private Long productId;
    /** 商品名称 */
    private String productName;
    /** 商品图片 URL */
    private String productImage;
    /** 商品单价 */
    private BigDecimal price;
    /** 购买数量 */
    private Integer quantity;
    /** 是否勾选，用于下单时过滤 */
    private Boolean checked;
}
