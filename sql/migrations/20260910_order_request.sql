-- Manual additive migration. Stop all checkout writers before execution.
-- Requires 20260907_inventory_operation.sql to have been applied already.
-- Does not backfill old tokens or alter existing orders/inventory.
USE cloud_mall;

CREATE TABLE order_request (
    token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    active_user_id BIGINT NULL,
    order_no VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot LONGTEXT NULL,
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (token),
    UNIQUE KEY uk_order_request_active_user (active_user_id),
    UNIQUE KEY uk_order_request_order_no (order_no),
    KEY idx_order_request_recovery (status, updated_at),
    KEY idx_order_request_user_created (user_id, created_at),
    CONSTRAINT chk_order_request_status CHECK (
        status IN ('READY','PROCESSING','SUCCEEDED','COMPENSATING','FAILED','EXPIRED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Durable checkout request and recovery state';

SHOW CREATE TABLE order_request;
