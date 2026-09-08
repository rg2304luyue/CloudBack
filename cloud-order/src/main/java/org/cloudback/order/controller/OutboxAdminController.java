package org.cloudback.order.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.common.service.OutboxService;
import org.cloudback.order.dto.FailedOutboxMessageView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxService outboxService;

    @GetMapping("/failed")
    public R<List<FailedOutboxMessageView>> listFailed(
            @RequestHeader(SystemConstants.USER_ROLE_HEADER) String role,
            @RequestParam(defaultValue = "50") int limit) {
        checkAdmin(role);
        List<FailedOutboxMessageView> messages = outboxService.listFailed(limit).stream()
                .map(FailedOutboxMessageView::from)
                .toList();
        long total = outboxService.countFailed();
        int responseTotal = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        return R.ok(messages, responseTotal);
    }

    @PostMapping("/{id}/retry")
    public R<String> retryFailed(@RequestHeader(SystemConstants.USER_ROLE_HEADER) String role,
                                 @PathVariable Long id) {
        checkAdmin(role);
        if (!outboxService.requeueFailed(id)) {
            return R.fail("消息不存在或当前不可重放");
        }
        return R.ok("消息已重新加入发送队列");
    }

    private void checkAdmin(String role) {
        if (!SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.ADMIN_ONLY);
        }
    }
}
