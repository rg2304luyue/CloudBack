package org.cloudback.order.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** Durable checkout identity; never delete a consumed request to allow a retry. */
@Data
public class OrderRequest {
    private String token;
    private Long userId;
    private Long activeUserId;
    private String orderNo;
    private String status;
    private String snapshot;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
}
