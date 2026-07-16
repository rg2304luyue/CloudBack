package org.cloudback.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.cloudback.product.model.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE product SET stock = stock - #{quantity}, sales = sales + #{quantity} " +
            "WHERE id = #{id} AND status = 1 AND stock >= #{quantity} AND deleted = 0")
    int deductStockAtomically(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Update("UPDATE product SET stock = stock + #{quantity}, sales = sales - #{quantity} " +
            "WHERE id = #{id} AND sales >= #{quantity} AND deleted = 0")
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
