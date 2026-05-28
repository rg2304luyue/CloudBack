package org.cloudback.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.cloudback.product.model.entity.Product;

/**
 * 商品 Mapper，提供商品 CRUD 和分页查询操作。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /** 原子扣减库存，WHERE stock >= quantity 防止超卖，返回影响行数（0=库存不足） */
    @Update("UPDATE product SET stock = stock - #{quantity}, sales = sales + #{quantity} WHERE id = #{id} AND stock >= #{quantity} AND deleted = 0")
    int deductStockAtomically(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 原子回滚库存（取消订单/支付超时），同时减少销量 */
    @Update("UPDATE product SET stock = stock + #{quantity}, sales = sales - #{quantity} WHERE id = #{id} AND deleted = 0")
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
