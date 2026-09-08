package org.cloudback.product.mapper;

import org.apache.ibatis.annotations.*;
import org.cloudback.product.model.entity.InventoryOperation;

@Mapper
public interface InventoryOperationMapper {
    @Insert("INSERT INTO inventory_operation(order_no, product_id, quantity, state) " +
            "VALUES(#{orderNo}, #{productId}, #{quantity}, 'EMPTY') " +
            "ON DUPLICATE KEY UPDATE order_no = order_no")
    int ensureExists(@Param("orderNo") String orderNo, @Param("productId") Long productId,
                     @Param("quantity") Integer quantity);

    @Select("SELECT order_no, product_id, quantity, state FROM inventory_operation " +
            "WHERE order_no = #{orderNo} AND product_id = #{productId} FOR UPDATE")
    InventoryOperation lock(@Param("orderNo") String orderNo, @Param("productId") Long productId);

    @Update("UPDATE inventory_operation SET state = #{state}, update_time = CURRENT_TIMESTAMP " +
            "WHERE order_no = #{orderNo} AND product_id = #{productId}")
    int setState(@Param("orderNo") String orderNo, @Param("productId") Long productId,
                 @Param("state") String state);
}
