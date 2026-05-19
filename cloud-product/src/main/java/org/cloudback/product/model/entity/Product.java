package org.cloudback.product.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cloudback.common.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * 商品实体，映射 product 表。
 * 价格使用 BigDecimal 保证精度，images 存储为 JSON 字符串。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    /** 所属分类 ID */
    private Long categoryId;
    /** 商品名称 */
    private String name;
    /** 商品描述 */
    private String description;
    /** 价格 */
    private BigDecimal price;
    /** 库存数量 */
    private Integer stock;
    /** 累计销量 */
    private Integer sales;
    /** 商品主图 URL */
    private String mainImage;
    /** 商品图片列表，JSON 字符串 */
    private String images;
    /** 状态: 0-下架, 1-上架 */
    private Integer status;
    /** 卖家用户 ID */
    private Long sellerId;
}
