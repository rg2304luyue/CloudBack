package org.cloudback.order.feign;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartItemDTO {
    private Long productId;
    private String name;
    private String mainImage;
    private BigDecimal price;
    private Integer quantity;
    private Boolean checked;
}
