package org.cloudback.cart.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品服务批量查询接口返回的商品信息（价格/状态/库存实时数据源）。
 */
@Data
public class ProductInfo {

    private Long id;
    private String name;
    private BigDecimal price;
    /** 状态: 0-下架, 1-上架 */
    private Integer status;
    private Integer stock;
    private String mainImage;
    private Long sellerId;
}
