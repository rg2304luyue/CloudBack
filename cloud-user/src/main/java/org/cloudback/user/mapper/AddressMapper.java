package org.cloudback.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.user.model.entity.Address;

/**
 * 收货地址 Mapper，提供地址 CRUD 操作。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface AddressMapper extends BaseMapper<Address> {
}
