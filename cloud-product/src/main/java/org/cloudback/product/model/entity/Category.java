package org.cloudback.product.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cloudback.common.entity.BaseEntity;
import java.util.List;

/**
 * 商品分类实体，映射 category 表。
 * 支持树形结构：parentId 指向父分类，children 为非数据库字段。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("category")
public class Category extends BaseEntity {

    /** 父分类ID，0 表示顶级分类 */
    private Long parentId;
    /** 分类名称 */
    private String name;
    /** 分类图标 URL */
    private String icon;
    /** 排序值，越小越靠前 */
    private Integer sort;
    /** 子分类列表，仅用于前端展示，不映射数据库 */
    @TableField(exist = false)
    private List<Category> children;
}
