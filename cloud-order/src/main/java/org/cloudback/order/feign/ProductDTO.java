package org.cloudback.order.feign;

import lombok.Data;

/**
 * 商品信息快照 DTO，用于下单时补充缺失的商品名称和图片。
 *
 * @author CloudBack
 * @since 2025-05-19
 */
@Data
public class ProductDTO {
    private Long id;
    private String name;
    private String mainImage;
}
