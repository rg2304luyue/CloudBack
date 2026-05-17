package org.cloudback.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.user.model.entity.Address;

@Mapper
public interface AddressMapper extends BaseMapper<Address> {
}
