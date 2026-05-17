package org.cloudback.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.product.model.entity.Product;

/**
 * 商品 Mapper，提供商品 CRUD 和分页查询操作。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
