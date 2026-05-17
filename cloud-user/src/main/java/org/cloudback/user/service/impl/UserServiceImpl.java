package org.cloudback.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.cloudback.common.entity.User;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.mapper.UserMapper;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.user.mapper.AddressMapper;
import org.cloudback.user.model.entity.Address;
import org.cloudback.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final AddressMapper addressMapper;

    private void cancelDefaultAddress(Long userId) {
        Address update = new Address();
        update.setIsDefault(0);
        addressMapper.update(update,
                new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId).eq(Address::getIsDefault, 1));
    }

    @Override
    public R<User> getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 不返回密码
        user.setPassword(null);
        return R.ok(user);
    }

    @Override
    public R<String> updateUserInfo(Long userId, String nickname, String phone, String email, String avatar) {
        User user = new User();
        user.setId(userId);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setEmail(email);
        user.setAvatar(avatar);
        userMapper.updateById(user);
        return R.ok("更新成功");
    }

    @Override
    public R<List<Address>> getAddressList(Long userId) {
        List<Address> list = addressMapper.selectList(
                new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId)
        );
        return R.ok(list);
    }

    @Override
    public R<Address> getAddressById(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "地址不存在");
        }
        return R.ok(address);
    }

    @Override
    public R<String> addAddress(Long userId, Address address) {
        address.setUserId(userId);
        // 如果设为默认地址，先取消其他默认
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            cancelDefaultAddress(userId);
        }
        addressMapper.insert(address);
        return R.ok("添加成功");
    }

    @Override
    public R<String> updateAddress(Long userId, Address address) {
        Address dbAddress = addressMapper.selectById(address.getId());
        if (dbAddress == null || !dbAddress.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "地址不存在");
        }
        // 如果设为默认，先取消其他默认
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            cancelDefaultAddress(userId);
        }
        address.setUserId(userId);
        addressMapper.updateById(address);
        return R.ok("更新成功");
    }

    @Override
    public R<String> deleteAddress(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "地址不存在");
        }
        addressMapper.deleteById(addressId);
        return R.ok("删除成功");
    }
}
