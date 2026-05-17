package org.cloudback.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cloudback.common.entity.User;

/**
 * 用户 Mapper，继承 MyBatis-Plus BaseMapper 提供基础 CRUD。
 * 由 auth 和 user 服务共享使用。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
