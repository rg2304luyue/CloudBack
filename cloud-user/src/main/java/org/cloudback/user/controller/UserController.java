package org.cloudback.user.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.entity.User;
import org.cloudback.common.result.R;
import org.cloudback.user.model.entity.Address;
import org.cloudback.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // ========== 用户信息 ==========

    @GetMapping("/info")
    public R<User> getUserInfo(@RequestHeader("X-User-Id") Long userId) {
        return userService.getUserInfo(userId);
    }

    @PutMapping("/info")
    public R<String> updateUserInfo(@RequestHeader("X-User-Id") Long userId,
                                  @RequestParam(required = false) String nickname,
                                  @RequestParam(required = false) String phone,
                                  @RequestParam(required = false) String email,
                                  @RequestParam(required = false) String avatar) {
        return userService.updateUserInfo(userId, nickname, phone, email, avatar);
    }

    // ========== 收货地址 ==========

    @GetMapping("/address")
    public R<List<Address>> getAddressList(@RequestHeader("X-User-Id") Long userId) {
        return userService.getAddressList(userId);
    }

    @GetMapping("/address/{addressId}")
    public R<Address> getAddressById(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable Long addressId) {
        return userService.getAddressById(userId, addressId);
    }

    @PostMapping("/address")
    public R<String> addAddress(@RequestHeader("X-User-Id") Long userId,
                              @RequestBody Address address) {
        return userService.addAddress(userId, address);
    }

    @PutMapping("/address")
    public R<String> updateAddress(@RequestHeader("X-User-Id") Long userId,
                                 @RequestBody Address address) {
        return userService.updateAddress(userId, address);
    }

    @DeleteMapping("/address/{addressId}")
    public R<String> deleteAddress(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable Long addressId) {
        return userService.deleteAddress(userId, addressId);
    }
}
