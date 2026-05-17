package org.cloudback.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.product.model.entity.Category;

/**
 * 分类 Mapper，提供分类 CRUD 操作。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
