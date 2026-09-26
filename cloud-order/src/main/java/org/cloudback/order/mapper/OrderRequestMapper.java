package org.cloudback.order.mapper;

import org.apache.ibatis.annotations.*;
import org.cloudback.order.model.entity.OrderRequest;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderRequestMapper {
    @Select("SELECT * FROM order_request WHERE token=#{token}")
    OrderRequest find(String token);

    @Select("SELECT * FROM order_request WHERE token=#{token} FOR UPDATE SKIP LOCKED")
    OrderRequest lock(String token);

    @Select("SELECT * FROM order_request WHERE active_user_id=#{userId}")
    OrderRequest active(Long userId);

    @Insert("INSERT INTO order_request(token,user_id,active_user_id,order_no,status,created_at,updated_at,expires_at) " +
            "VALUES(#{token},#{userId},#{activeUserId},#{orderNo},#{status},#{createdAt},#{updatedAt},#{expiresAt})")
    int insert(OrderRequest request);

    @Update("UPDATE order_request SET active_user_id=#{activeUserId},status=#{status},snapshot=#{snapshot}," +
            "failure_reason=#{failureReason},updated_at=#{updatedAt} WHERE token=#{token}")
    int update(OrderRequest request);

    @Select("SELECT token FROM order_request WHERE (status='PROCESSING' AND updated_at < #{processingBefore}) " +
            "OR (status='COMPENSATING' AND updated_at < #{compensatingBefore}) ORDER BY updated_at LIMIT 20")
    List<String> recoverable(@Param("processingBefore") LocalDateTime processingBefore,
                             @Param("compensatingBefore") LocalDateTime compensatingBefore);
}
