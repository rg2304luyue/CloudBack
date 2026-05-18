-- ==========================================
-- CloudBack 电商系统初始化 SQL
-- 在宿主机 MySQL 上手动执行：
--   mysql -u root -p < init.sql
-- 或逐段复制到 MySQL 客户端中执行
-- ==========================================

-- Nacos 配置数据库
CREATE DATABASE IF NOT EXISTS `nacos_config` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 业务数据库
CREATE DATABASE IF NOT EXISTS `cloud_mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `cloud_mall`;

-- ==================== 用户表 ====================
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`      VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname`      VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `status`        TINYINT      DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ==================== 收货地址表 ====================
CREATE TABLE IF NOT EXISTS `address` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `receiver_name` VARCHAR(50)  NOT NULL COMMENT '收货人',
    `phone`         VARCHAR(20)  NOT NULL COMMENT '联系电话',
    `province`      VARCHAR(50)  NOT NULL COMMENT '省份',
    `city`          VARCHAR(50)  NOT NULL COMMENT '城市',
    `district`      VARCHAR(50)  NOT NULL COMMENT '区/县',
    `detail`        VARCHAR(255) NOT NULL COMMENT '详细地址',
    `is_default`    TINYINT      DEFAULT 0 COMMENT '是否默认: 0-否 1-是',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';

-- ==================== 商品分类表 ====================
CREATE TABLE IF NOT EXISTS `category` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `parent_id`     BIGINT       DEFAULT 0 COMMENT '父分类ID, 0为顶级',
    `name`          VARCHAR(50)  NOT NULL COMMENT '分类名称',
    `icon`          VARCHAR(255) DEFAULT NULL COMMENT '图标URL',
    `sort`          INT          DEFAULT 0 COMMENT '排序',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- ==================== 商品表 ====================
CREATE TABLE IF NOT EXISTS `product` (
    `id`            BIGINT        NOT NULL COMMENT '主键',
    `category_id`   BIGINT        NOT NULL COMMENT '分类ID',
    `name`          VARCHAR(100)  NOT NULL COMMENT '商品名称',
    `description`   TEXT          DEFAULT NULL COMMENT '商品描述',
    `price`         DECIMAL(10,2) NOT NULL COMMENT '价格',
    `stock`         INT           NOT NULL DEFAULT 0 COMMENT '库存',
    `sales`         INT           DEFAULT 0 COMMENT '销量',
    `main_image`    VARCHAR(255)  DEFAULT NULL COMMENT '主图URL',
    `images`        JSON          DEFAULT NULL COMMENT '图片列表(JSON数组)',
    `status`        TINYINT       DEFAULT 1 COMMENT '状态: 0-下架 1-上架',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ==================== 订单表 ====================
CREATE TABLE IF NOT EXISTS `order_info` (
    `id`              BIGINT        NOT NULL COMMENT '主键',
    `order_no`        VARCHAR(32)   NOT NULL COMMENT '订单号',
    `user_id`         BIGINT        NOT NULL COMMENT '用户ID',
    `total_amount`    DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    `status`          TINYINT       DEFAULT 0 COMMENT '状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    `receiver_name`   VARCHAR(50)   DEFAULT NULL COMMENT '收货人',
    `receiver_phone`  VARCHAR(20)   DEFAULT NULL COMMENT '联系电话',
    `receiver_address` VARCHAR(255) DEFAULT NULL COMMENT '收货地址',
    `remark`          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `pay_time`        DATETIME      DEFAULT NULL COMMENT '支付时间',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ==================== 订单明细表 ====================
CREATE TABLE IF NOT EXISTS `order_item` (
    `id`            BIGINT        NOT NULL COMMENT '主键',
    `order_id`      BIGINT        NOT NULL COMMENT '订单ID',
    `order_no`      VARCHAR(32)   NOT NULL COMMENT '订单号',
    `product_id`    BIGINT        NOT NULL COMMENT '商品ID',
    `product_name`  VARCHAR(100)  NOT NULL COMMENT '商品名称',
    `product_image` VARCHAR(255)  DEFAULT NULL COMMENT '商品图片',
    `price`         DECIMAL(10,2) NOT NULL COMMENT '商品单价',
    `quantity`      INT           NOT NULL COMMENT '数量',
    `total_amount`  DECIMAL(10,2) NOT NULL COMMENT '小计金额',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- ==================== 支付记录表 ====================
CREATE TABLE IF NOT EXISTS `payment` (
    `id`            BIGINT        NOT NULL COMMENT '主键',
    `order_no`      VARCHAR(32)   NOT NULL COMMENT '订单号',
    `user_id`       BIGINT        NOT NULL COMMENT '用户ID',
    `amount`        DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    `pay_method`    VARCHAR(20)   DEFAULT 'WECHAT' COMMENT '支付方式: WECHAT/ALIPAY',
    `status`        TINYINT       DEFAULT 0 COMMENT '状态: 0-待支付 1-支付成功 2-支付失败',
    `trade_no`      VARCHAR(64)   DEFAULT NULL COMMENT '第三方交易号',
    `pay_time`      DATETIME      DEFAULT NULL COMMENT '支付时间',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';
