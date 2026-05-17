package org.cloudback.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.common.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
