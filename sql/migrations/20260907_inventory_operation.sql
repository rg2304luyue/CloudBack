-- MANUAL migration. Stop all writers and reconcile/drain old compensation messages first.
-- Read README.md before execution. Do not run the stale init.sql.
USE cloud_mall;

CREATE TABLE inventory_operation (
    order_no VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    state VARCHAR(16) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_no, product_id),
    CONSTRAINT chk_inventory_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_state CHECK (state IN ('EMPTY', 'RESERVED', 'RESTORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Existing non-cancelled orders already deducted stock. Never deduct it again here.
-- Cancelled orders become terminal tombstones, not new inventory increments.
-- Include deleted/cancelled orders as tombstones to block delayed historical requests.
INSERT INTO inventory_operation (order_no, product_id, quantity, state)
SELECT o.order_no, i.product_id, SUM(i.quantity),
       CASE WHEN o.status <> 4 AND o.deleted = 0 THEN 'RESERVED' ELSE 'RESTORED' END
FROM order_info o
JOIN order_item i ON i.order_id = o.id
GROUP BY o.id, o.order_no, o.status, o.deleted, i.product_id;

SELECT state, COUNT(*) AS operation_count FROM inventory_operation GROUP BY state;
