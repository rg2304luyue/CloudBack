package org.cloudback.user.service;

import org.cloudback.common.entity.User;
import org.cloudback.common.result.R;
import org.cloudback.user.model.entity.Address;
import java.util.List;

/**
 * 用户服务接口，提供用户信息管理和收货地址管理功能。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public interface UserService {

    /** 获取用户信息（已脱敏，不返回密码） */
    R<User> getUserInfo(Long userId);

    /** 更新用户信息：昵称、手机、邮箱、头像 */
    R<String> updateUserInfo(Long userId, String nickname, String phone, String email, String avatar);

    /** 获取用户的收货地址列表 */
    R<List<Address>> getAddressList(Long userId);

    /** 根据地址ID获取地址详情 */
    R<Address> getAddressById(Long userId, Long addressId);

    /** 添加收货地址 */
    R<String> addAddress(Long userId, Address address);

    /** 修改收货地址 */
    R<String> updateAddress(Long userId, Address address);

    /** 删除收货地址 */
    R<String> deleteAddress(Long userId, Long addressId);
}
