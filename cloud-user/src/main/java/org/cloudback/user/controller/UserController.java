package org.cloudback.user.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.entity.User;
import org.cloudback.common.result.R;
import org.cloudback.user.model.entity.Address;
import org.cloudback.user.model.entity.SellerApplication;
import org.cloudback.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户服务控制器，提供用户信息查询/修改和收货地址管理接口。
 * 用户身份通过 Gateway 注入的 X-User-Id 请求头获取。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取当前用户信息（已脱敏） */
    @GetMapping("/info")
    public R<User> getUserInfo(@RequestHeader("X-User-Id") Long userId) {
        return userService.getUserInfo(userId);
    }

    /** 更新当前用户信息 */
    @PutMapping("/info")
    public R<String> updateUserInfo(@RequestHeader("X-User-Id") Long userId,
                                    @RequestParam(required = false) String nickname,
                                    @RequestParam(required = false) String phone,
                                    @RequestParam(required = false) String email,
                                    @RequestParam(required = false) String avatar) {
        return userService.updateUserInfo(userId, nickname, phone, email, avatar);
    }

    /** 获取收货地址列表 */
    @GetMapping("/address")
    public R<List<Address>> getAddressList(@RequestHeader("X-User-Id") Long userId) {
        return userService.getAddressList(userId);
    }

    /** 获取单个收货地址详情 */
    @GetMapping("/address/{addressId}")
    public R<Address> getAddressById(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable Long addressId) {
        return userService.getAddressById(userId, addressId);
    }

    /** 添加收货地址 */
    @PostMapping("/address")
    public R<String> addAddress(@RequestHeader("X-User-Id") Long userId,
                                @RequestBody Address address) {
        return userService.addAddress(userId, address);
    }

    /** 修改收货地址 */
    @PutMapping("/address")
    public R<String> updateAddress(@RequestHeader("X-User-Id") Long userId,
                                   @RequestBody Address address) {
        return userService.updateAddress(userId, address);
    }

    /** 删除收货地址 */
    @DeleteMapping("/address/{addressId}")
    public R<String> deleteAddress(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable Long addressId) {
        return userService.deleteAddress(userId, addressId);
    }

    /** 管理员：获取所有用户列表 */
    @GetMapping("/admin/list")
    public R<List<User>> getUserList(@RequestHeader("X-User-Role") String role) {
        return userService.getUserList(role);
    }

    /** 买家申请成为卖家 */
    @PostMapping("/apply-seller")
    public R<String> applySeller(@RequestHeader("X-User-Id") Long userId) {
        return userService.applySeller(userId);
    }

    /** 管理员：查看待审批的卖家申请 */
    @GetMapping("/admin/applications")
    public R<List<SellerApplication>> getApplications(@RequestHeader("X-User-Role") String role) {
        return userService.getApplications(role);
    }

    /** 管理员：审批卖家申请 */
    @PutMapping("/admin/applications/{id}")
    public R<String> processApplication(@RequestHeader("X-User-Role") String role,
                                        @PathVariable Long id,
                                        @RequestParam boolean approved) {
        return userService.processApplication(role, id, approved);
    }

    /** 管理员：重置用户密码 */
    @PutMapping("/admin/reset-password")
    public R<String> resetPassword(@RequestHeader("X-User-Role") String role,
                                   @RequestParam Long targetUserId,
                                   @RequestParam String newPassword) {
        return userService.resetPassword(role, targetUserId, newPassword);
    }
}
