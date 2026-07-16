package org.cloudback.order.feign;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductDTO {
    private Long id;
    private String name;
    private String mainImage;
    private BigDecimal price;
    private Integer status;
}
