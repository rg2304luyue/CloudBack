package org.cloudback.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cloudback.common.entity.BaseEntity;

/**
 * 收货地址实体，映射 address 表。
 * 每个用户可有多个地址，其中最多一个默认地址。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("address")
public class Address extends BaseEntity {

    /** 所属用户ID */
    private Long userId;
    /** 收货人姓名 */
    private String receiverName;
    /** 联系电话 */
    private String phone;
    /** 省份 */
    private String province;
    /** 城市 */
    private String city;
    /** 区/县 */
    private String district;
    /** 详细地址 */
    private String detail;
    /** 是否默认地址: 0-否, 1-是 */
    private Integer isDefault;
}
