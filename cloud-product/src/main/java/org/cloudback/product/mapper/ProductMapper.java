package org.cloudback.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.product.model.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
