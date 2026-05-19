package org.cloudback.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cloudback.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seller_application")
public class SellerApplication extends BaseEntity {

    private Long userId;
    /** PENDING / APPROVED / REJECTED */
    private String status;
}
