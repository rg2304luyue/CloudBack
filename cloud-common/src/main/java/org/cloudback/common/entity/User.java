package org.cloudback.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体，映射 user 表。
 * 密码使用 BCrypt 加密存储，查询时需注意脱敏。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 用户名，唯一 */
    private String username;
    /** 密码，BCrypt 密文 */
    private String password;
    /** 昵称 */
    private String nickname;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 头像 URL */
    private String avatar;
    /** 状态: 0-禁用, 1-正常 */
    private Integer status;
    /** 角色: BUYER-买家, SELLER-卖家, ADMIN-管理员 */
    private String role;
}
