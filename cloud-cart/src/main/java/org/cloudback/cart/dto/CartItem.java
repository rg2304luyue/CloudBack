package org.cloudback.cart.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartItem {

    private Long productId;
    private String name;
    private String mainImage;
    private BigDecimal price;
    private Integer quantity;
    private Boolean checked;
    /** 商品实时状态（0-下架/不存在, 1-上架），查询时瞬态覆盖，不持久化快照 */
    private Integer status;
    /** 商品实时库存，查询时瞬态覆盖，不持久化快照 */
    private Integer stock;
}
