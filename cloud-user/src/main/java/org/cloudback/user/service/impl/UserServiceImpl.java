package org.cloudback.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import cn.hutool.crypto.digest.BCrypt;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.entity.User;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.mapper.UserMapper;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.user.mapper.AddressMapper;
import org.cloudback.user.mapper.SellerApplicationMapper;
import org.cloudback.user.model.entity.Address;
import org.cloudback.user.model.entity.SellerApplication;
import org.cloudback.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务实现，处理用户信息查询/修改和收货地址管理。
 * 所有地址操作均校验 userId，防止越权。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final AddressMapper addressMapper;
    private final SellerApplicationMapper applicationMapper;

    /** 取消该用户的所有默认地址 */
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

    @Override
    public R<List<User>> getUserList(String role) {
        if (!SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.ADMIN_ONLY);
        }
        List<User> users = userMapper.selectList(null);
        users.forEach(u -> u.setPassword(null));
        return R.ok(users);
    }

    @Override
    public R<String> resetPassword(String role, Long targetUserId, String newPassword) {
        if (!SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.ADMIN_ONLY);
        }
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        User updateUser = new User();
        updateUser.setId(targetUserId);
        updateUser.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userMapper.updateById(updateUser);
        return R.ok("密码重置成功");
    }

    @Override
    public R<String> applySeller(Long userId) {
        if (!isBuyer(userId)) return R.fail("您已是卖家或管理员，无需申请");

        Long exist = applicationMapper.selectCount(
                new LambdaQueryWrapper<SellerApplication>()
                        .eq(SellerApplication::getUserId, userId)
                        .eq(SellerApplication::getStatus, "PENDING"));
        if (exist > 0) return R.fail("您已提交过申请，请等待审批");

        SellerApplication app = new SellerApplication();
        app.setUserId(userId);
        app.setStatus("PENDING");
        applicationMapper.insert(app);
        return R.ok("申请已提交，等待管理员审批");
    }

    @Override
    public R<List<SellerApplication>> getApplications(String role) {
        if (!SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.ADMIN_ONLY);
        }
        List<SellerApplication> list = applicationMapper.selectList(
                new LambdaQueryWrapper<SellerApplication>()
                        .eq(SellerApplication::getStatus, "PENDING")
                        .orderByAsc(SellerApplication::getCreateTime));
        return R.ok(list);
    }

    @Override
    public R<String> processApplication(String role, Long applicationId, boolean approved) {
        if (!SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.ADMIN_ONLY);
        }
        SellerApplication app = applicationMapper.selectById(applicationId);
        if (app == null || !"PENDING".equals(app.getStatus())) {
            return R.fail("申请不存在或已处理");
        }

        if (approved) {
            app.setStatus("APPROVED");
            User updateUser = new User();
            updateUser.setId(app.getUserId());
            updateUser.setRole(SystemConstants.ROLE_SELLER);
            userMapper.updateById(updateUser);
        } else {
            app.setStatus("REJECTED");
        }
        applicationMapper.updateById(app);
        return R.ok(approved ? "已通过申请" : "已拒绝申请");
    }

    private boolean isBuyer(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && SystemConstants.ROLE_BUYER.equals(user.getRole());
    }
}
