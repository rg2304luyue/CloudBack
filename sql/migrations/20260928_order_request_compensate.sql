-- Manual additive migration. Stop all checkout writers before execution.
-- Requires 20260910_order_request.sql to have been applied already.
-- Adds a bound on stock-compensation retries so a permanently failing restore
-- cannot pin a user's active checkout slot forever.
USE cloud_mall;

-- 幂等写法：MySQL 不支持 ADD COLUMN IF NOT EXISTS，用动态 SQL 保证重复执行不报错
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_request'
      AND COLUMN_NAME = 'compensate_count'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE order_request ADD COLUMN compensate_count INT NOT NULL DEFAULT 0 COMMENT ''库存补偿已尝试次数（上限10次）''',
    'SELECT ''compensate_count already exists, skipped''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SHOW CREATE TABLE order_request;
