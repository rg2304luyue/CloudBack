package org.cloudback.user.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.entity.User;
import org.cloudback.common.result.R;
import org.cloudback.user.dto.ProcessApplicationRequest;
import org.cloudback.user.dto.ResetPasswordRequest;
import org.cloudback.user.model.entity.SellerApplication;
import org.cloudback.user.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping("/users")
    public R<List<User>> getUserList(@RequestHeader("X-User-Role") String role) {
        return userService.getUserList(role);
    }

    @PatchMapping("/users/{id}/password")
    public R<String> resetPassword(@RequestHeader("X-User-Role") String role,
                                   @PathVariable Long id,
                                   @RequestBody ResetPasswordRequest request) {
        return userService.resetPassword(role, id, request.newPassword());
    }

    @GetMapping("/applications")
    public R<List<SellerApplication>> getApplications(@RequestHeader("X-User-Role") String role) {
        return userService.getApplications(role);
    }

    @PatchMapping("/applications/{id}")
    public R<String> processApplication(@RequestHeader("X-User-Role") String role,
                                        @PathVariable Long id,
                                        @RequestBody ProcessApplicationRequest request) {
        return userService.processApplication(role, id, request.approved());
    }
}
