package org.cloudback.cart.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartItem {
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal price;
    private Integer quantity;
    private Boolean checked;
}
