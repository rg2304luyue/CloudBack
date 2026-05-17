package org.cloudback.user.service;

import org.cloudback.common.entity.User;
import org.cloudback.common.result.R;
import org.cloudback.user.model.entity.Address;
import java.util.List;

public interface UserService {
    // ---- 用户信息 ----
    R<User> getUserInfo(Long userId);

    R<String> updateUserInfo(Long userId, String nickname, String phone, String email, String avatar);

    // ---- 收货地址 ----
    R<List<Address>> getAddressList(Long userId);

    R<Address> getAddressById(Long userId, Long addressId);

    R<String> addAddress(Long userId, Address address);

    R<String> updateAddress(Long userId, Address address);

    R<String> deleteAddress(Long userId, Long addressId);
}
