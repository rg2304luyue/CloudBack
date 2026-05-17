package org.cloudback.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.product.model.entity.Category;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
