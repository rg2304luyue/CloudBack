-- ============================================================================
-- CloudMall 数据库初始化脚本（结构与数据 · 全量克隆版）
-- ============================================================================
-- 来源：虚拟机 192.168.91.130 上 MySQL 8.0.46 的 cloud_mall / nacos_config 两库，
--       用 mysqldump（--single-transaction --complete-insert --skip-comments）原样导出，
--       导出时间 2026-10-24。内容为仓库开发时正在使用的 mock 数据，未做任何脱敏与删改。
--
-- 包含：
--   [1] nacos_config —— Nacos 持久化库：10 张表结构 + admin 账号（roles/users 各 1 行，配置项 0 条）
--   [2] cloud_mall   —— 业务库：11 张表结构 + 全部数据
--       user 6 / address 6 / category 31 / product 49 / order_info 18 / order_item 33 /
--       payment 17 / outbox_message 15 / seller_application 2 / order_request 1 / inventory_operation 35
--
-- 注意事项（务必先读）：
--   1) 本脚本每张表前都有 DROP TABLE IF EXISTS —— 在**已有数据**的库上执行会清空并重建这些表。
--      只想要空库时，请先注释掉所有 DROP TABLE 与 INSERT 段，或改用 sql/ 下的建表脚本。
--   2) 需要 MySQL 8.0+（CHECK 约束、utf8mb4_0900_ai_ci 排序规则、JSON 列）。
--   3) nacos_config 段会重建 Nacos 自己的表。若你的 Nacos 已在运行、且库里有配置或额外账号，
--      请在执行前**删掉/注释掉 [1] 整段**（Nacos 2.x 启动时也会自动建表）。
--   4) 用户密码是 BCrypt 哈希，不可逆；需要登录请用仓库文档里记录的测试账号口令，或自行重置。
--   5) product.main_image / images 里存的是 http://<minio-endpoint>/... 绝对地址，
--      迁到别的机器后图片链接会失效，需要在 MinIO 重新上传或改地址。
--
-- 用法：mysql -h <host> -P 3306 -u root -p < init.sql
-- ============================================================================

SET NAMES utf8mb4;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
SET @OLD_TIME_ZONE=@@TIME_ZONE;

-- ============================================================================
-- [1] nacos_config —— Nacos 持久化库（表结构 + admin 账号）
--     不需要 Nacos 数据时，删除本段到 "[2]" 之前的全部内容
-- ============================================================================
CREATE DATABASE IF NOT EXISTS `nacos_config` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `nacos_config`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `config_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'group_id',
  `content` longtext COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
  `md5` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COLLATE utf8mb3_bin COMMENT 'source user',
  `src_ip` varchar(50) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'source ip',
  `app_name` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'app_name',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT '租户字段',
  `c_desc` varchar(256) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'configuration description',
  `c_use` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'configuration usage',
  `effect` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL COMMENT '配置生效的描述',
  `type` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL COMMENT '配置的类型',
  `c_schema` text COLLATE utf8mb3_bin COMMENT '配置的模式',
  `encrypted_data_key` varchar(1024) COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT '密钥',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='config_info';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `config_info` WRITE;
/*!40000 ALTER TABLE `config_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `config_info` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `config_info_gray`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_info_gray` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) NOT NULL COMMENT 'group_id',
  `content` longtext NOT NULL COMMENT 'content',
  `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
  `src_user` text COMMENT 'src_user',
  `src_ip` varchar(100) DEFAULT NULL COMMENT 'src_ip',
  `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_create',
  `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_modified',
  `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
  `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
  `gray_name` varchar(128) NOT NULL COMMENT 'gray_name',
  `gray_rule` text NOT NULL COMMENT 'gray_rule',
  `encrypted_data_key` varchar(256) NOT NULL DEFAULT '' COMMENT 'encrypted_data_key',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfogray_datagrouptenantgray` (`data_id`,`group_id`,`tenant_id`,`gray_name`),
  KEY `idx_dataid_gmt_modified` (`data_id`,`gmt_modified`),
  KEY `idx_gmt_modified` (`gmt_modified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='config_info_gray';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `config_info_gray` WRITE;
/*!40000 ALTER TABLE `config_info_gray` DISABLE KEYS */;
/*!40000 ALTER TABLE `config_info_gray` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `config_tags_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config_tags_relation` (
  `id` bigint NOT NULL COMMENT 'id',
  `tag_name` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'tag_name',
  `tag_type` varchar(64) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'tag_type',
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT 'tenant_id',
  `nid` bigint NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增长标识',
  PRIMARY KEY (`nid`),
  UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='config_tag_relation';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `config_tags_relation` WRITE;
/*!40000 ALTER TABLE `config_tags_relation` DISABLE KEYS */;
/*!40000 ALTER TABLE `config_tags_relation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `group_capacity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_capacity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT 'Group ID，空字符表示整个集群',
  `quota` int unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
  `usage` int unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
  `max_size` int unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
  `max_aggr_count` int unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数，，0表示使用默认值',
  `max_aggr_size` int unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
  `max_history_count` int unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='集群、各Group容量信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `group_capacity` WRITE;
/*!40000 ALTER TABLE `group_capacity` DISABLE KEYS */;
/*!40000 ALTER TABLE `group_capacity` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `his_config_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `his_config_info` (
  `id` bigint unsigned NOT NULL COMMENT 'id',
  `nid` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增标识',
  `data_id` varchar(255) COLLATE utf8mb3_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'group_id',
  `app_name` varchar(128) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'app_name',
  `content` longtext COLLATE utf8mb3_bin NOT NULL COMMENT 'content',
  `md5` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COLLATE utf8mb3_bin COMMENT 'source user',
  `src_ip` varchar(50) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'source ip',
  `op_type` char(10) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'operation type',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT '租户字段',
  `encrypted_data_key` varchar(1024) COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT '密钥',
  `publish_type` varchar(50) COLLATE utf8mb3_bin DEFAULT 'formal' COMMENT 'publish type gray or formal',
  `gray_name` varchar(50) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'gray name',
  `ext_info` longtext COLLATE utf8mb3_bin COMMENT 'ext info',
  PRIMARY KEY (`nid`),
  KEY `idx_gmt_create` (`gmt_create`),
  KEY `idx_gmt_modified` (`gmt_modified`),
  KEY `idx_did` (`data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='多租户改造';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `his_config_info` WRITE;
/*!40000 ALTER TABLE `his_config_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `his_config_info` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `role` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'role',
  `resource` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'resource',
  `action` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'action',
  UNIQUE KEY `uk_role_permission` (`role`,`resource`,`action`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `permissions` WRITE;
/*!40000 ALTER TABLE `permissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `permissions` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'username',
  `role` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'role',
  UNIQUE KEY `idx_user_role` (`username`,`role`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` (`username`, `role`) VALUES ('nacos','ROLE_ADMIN');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `tenant_capacity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_capacity` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin NOT NULL DEFAULT '' COMMENT 'Tenant ID',
  `quota` int unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
  `usage` int unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
  `max_size` int unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
  `max_aggr_count` int unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数',
  `max_aggr_size` int unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
  `max_history_count` int unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='租户容量信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `tenant_capacity` WRITE;
/*!40000 ALTER TABLE `tenant_capacity` DISABLE KEYS */;
/*!40000 ALTER TABLE `tenant_capacity` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `tenant_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `kp` varchar(128) COLLATE utf8mb3_bin NOT NULL COMMENT 'kp',
  `tenant_id` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT 'tenant_id',
  `tenant_name` varchar(128) COLLATE utf8mb3_bin DEFAULT '' COMMENT 'tenant_name',
  `tenant_desc` varchar(256) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'tenant_desc',
  `create_source` varchar(32) COLLATE utf8mb3_bin DEFAULT NULL COMMENT 'create_source',
  `gmt_create` bigint NOT NULL COMMENT '创建时间',
  `gmt_modified` bigint NOT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`,`tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='tenant_info';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `tenant_info` WRITE;
/*!40000 ALTER TABLE `tenant_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `tenant_info` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'username',
  `password` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'password',
  `enabled` tinyint(1) NOT NULL COMMENT 'enabled',
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` (`username`, `password`, `enabled`) VALUES ('nacos','$2a$10$T62r5fNmg2t3na.LrEeW2Oo2okfINuuY0ZsTl2XDG.UcoXOT5HAma',1);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ============================================================================
-- [2] cloud_mall —— 业务库（表结构 + 全部数据）
-- ============================================================================
CREATE DATABASE IF NOT EXISTS `cloud_mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `cloud_mall`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `address` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `receiver_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收货人',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '联系电话',
  `province` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '省份',
  `city` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '城市',
  `district` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '区/县',
  `detail` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '详细地址',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认: 0-否 1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `address` WRITE;
/*!40000 ALTER TABLE `address` DISABLE KEYS */;
INSERT INTO `address` (`id`, `user_id`, `receiver_name`, `phone`, `province`, `city`, `district`, `detail`, `is_default`, `create_time`, `update_time`, `deleted`) VALUES (2056618437820649473,2056617382194016258,'huqi','19318563161','湖南省','长沙市','岳麓区','中南大学岳麓山校区12舍北',0,'2026-05-19 14:10:20','2026-09-29 11:58:47',0),(2059833452330303490,2056617382194016258,'huqi','19318563161','湖南省','长沙市','天心区','中南大学铁道校区',1,'2026-05-28 11:05:40','2026-05-29 19:13:48',0),(2059991014512238594,2056341213168037890,'luyue','16608188853','湖南省','长沙市','岳麓区','中南大学岳麓山校区12舍',0,'2026-05-28 21:31:45','2026-06-03 15:05:00',0),(2062068010797989890,2056341213168037890,'陆岳','13786139078','福建省','厦门市','思明区','厦门大学思明校区',1,'2026-06-03 15:05:00','2026-06-03 15:05:00',0),(2062417085249118210,2056372672016793601,'adminlu','16608188853','四川省','成都市','武侯区','四川大学',1,'2026-06-04 14:12:06','2026-06-04 14:12:06',0),(2097303005882961921,880009082036,'CXLIVE0908_2036','13000000000','TEST','TEST','TEST','Dedicated integration test, no delivery',0,'2026-09-08 20:36:17','2026-09-08 20:36:17',0);
/*!40000 ALTER TABLE `address` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint NOT NULL COMMENT '主键',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID, 0为顶级',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `icon` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标URL',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `category` WRITE;
/*!40000 ALTER TABLE `category` DISABLE KEYS */;
INSERT INTO `category` (`id`, `parent_id`, `name`, `icon`, `sort`, `create_time`, `update_time`, `deleted`) VALUES (2056350000000000001,0,'手机数码',NULL,1,'2026-05-19 17:20:03','2026-05-19 17:20:03',0),(2056350000000000002,0,'电脑办公',NULL,2,'2026-05-19 17:20:03','2026-05-19 17:20:03',0),(2056350000000000003,0,'家用电器',NULL,3,'2026-05-19 17:20:03','2026-05-19 17:20:03',0),(2056350000000000004,0,'服饰鞋包',NULL,4,'2026-05-19 17:20:03','2026-05-19 17:20:03',0),(2056350000000000005,0,'食品生鲜',NULL,5,'2026-05-19 17:20:03','2026-05-19 17:20:03',0),(2056350000000000006,0,'图书文娱',NULL,6,'2026-05-19 17:20:03','2026-05-19 17:20:03',0),(2056350000000000007,0,'运动户外',NULL,7,'2026-05-19 17:20:03','2026-05-19 17:20:03',0),(2056350000000000008,0,'家居家具',NULL,8,'2026-05-19 17:20:03','2026-05-19 17:20:03',0),(2056350000000000101,2056350000000000001,'智能手机',NULL,1,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000102,2056350000000000001,'平板电脑',NULL,2,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000103,2056350000000000001,'智能穿戴',NULL,3,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000104,2056350000000000001,'耳机音箱',NULL,4,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000201,2056350000000000002,'笔记本电脑',NULL,1,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000202,2056350000000000002,'外设配件',NULL,2,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000203,2056350000000000002,'存储设备',NULL,3,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000301,2056350000000000003,'厨房电器',NULL,1,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000302,2056350000000000003,'生活电器',NULL,2,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000303,2056350000000000003,'个护健康',NULL,3,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000401,2056350000000000004,'男装',NULL,1,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000402,2056350000000000004,'女装',NULL,2,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000403,2056350000000000004,'鞋靴',NULL,3,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000404,2056350000000000004,'箱包',NULL,4,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000501,2056350000000000005,'休闲零食',NULL,1,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000502,2056350000000000005,'生鲜水果',NULL,2,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000503,2056350000000000005,'饮料冲调',NULL,3,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000601,2056350000000000006,'畅销图书',NULL,1,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000602,2056350000000000006,'办公文具',NULL,2,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000701,2056350000000000007,'运动装备',NULL,1,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000702,2056350000000000007,'户外用品',NULL,2,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000801,2056350000000000008,'客厅家具',NULL,1,'2026-05-19 17:20:29','2026-05-19 17:20:29',0),(2056350000000000802,2056350000000000008,'卧室家具',NULL,2,'2026-05-19 17:20:29','2026-05-19 17:20:29',0);
/*!40000 ALTER TABLE `category` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `inventory_operation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_operation` (
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `state` varchar(16) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_no`,`product_id`),
  CONSTRAINT `chk_inventory_quantity` CHECK ((`quantity` > 0)),
  CONSTRAINT `chk_inventory_state` CHECK ((`state` in (_utf8mb4'EMPTY',_utf8mb4'RESERVED',_utf8mb4'RESTORED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `inventory_operation` WRITE;
/*!40000 ALTER TABLE `inventory_operation` DISABLE KEYS */;
INSERT INTO `inventory_operation` (`order_no`, `product_id`, `quantity`, `state`, `create_time`, `update_time`) VALUES ('2056641087188074496',1001,1,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2056913630662856704',2056360000000000037,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2056919701662052352',2056360000000000038,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2056923056245411840',2056360000000000033,2,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2059898535796531200',2056360000000000028,1,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2059898535796531200',2056360000000000031,1,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2060318251191877632',2056360000000000026,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2060318251191877632',2056360000000000028,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2060318251191877632',2056360000000000033,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061287955964342272',2056360000000000030,2,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061287955964342272',2056360000000000032,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061290074167533568',2056360000000000030,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061290074167533568',2056360000000000037,2,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061339951794679808',2056360000000000029,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061339951794679808',2056360000000000030,2,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061342315427635200',2056360000000000029,2,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061342315427635200',2056360000000000030,1,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061441978877071360',2056360000000000033,5,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2061441978877071360',2056360000000000037,2,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2062068424960356352',2056360000000000028,1,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2062068424960356352',2056360000000000032,1,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2062068424960356352',2056360000000000038,2,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2062417242124472320',2056360000000000031,2,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2062417242124472320',2056360000000000032,1,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2062436986072436736',2056360000000000032,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2077563988736110592',2056360000000000026,3,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2077563988736110592',2056360000000000034,1,'RESTORED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2077575377152892928',2056360000000000026,2,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2077575377152892928',2056360000000000032,2,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2077575377152892928',2056360000000000037,1,'RESERVED','2026-09-08 14:12:42','2026-09-08 14:12:42'),('2097303280488804352',2097303000501592065,2,'RESTORED','2026-09-08 20:37:22','2026-09-08 20:37:25'),('2104783022234218496',2056360000000000030,2,'RESTORED','2026-09-29 11:59:23','2026-09-29 12:29:28'),('2104783022234218496',2056360000000000039,1,'RESTORED','2026-09-29 11:59:23','2026-09-29 12:29:28'),('CXLIVE0908_CONCURRENT',2097303000501592065,2,'RESTORED','2026-09-08 20:36:52','2026-09-08 20:36:54'),('CXLIVE0908_EARLY',2097303000501592065,4,'RESTORED','2026-09-08 20:36:54','2026-09-08 20:36:54');
/*!40000 ALTER TABLE `inventory_operation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `order_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_info` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
  `receiver_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货人',
  `receiver_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `receiver_address` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货地址',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `order_info` WRITE;
/*!40000 ALTER TABLE `order_info` DISABLE KEYS */;
INSERT INTO `order_info` (`id`, `order_no`, `user_id`, `total_amount`, `status`, `receiver_name`, `receiver_phone`, `receiver_address`, `remark`, `pay_time`, `create_time`, `update_time`, `deleted`) VALUES (2056641088324730882,'2056641087188074496',2056617382194016258,8999.00,1,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','送货上门','2026-05-19 15:40:35','2026-05-19 15:40:21','2026-05-19 15:40:34',0),(2056913631250059265,'2056913630662856704',2056617382194016258,99.00,4,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','送货上门',NULL,'2026-05-20 09:43:20','2026-05-20 09:43:20',0),(2056919701846601729,'2056919701662052352',2056617382194016258,68.00,4,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','请送货上门',NULL,'2026-05-20 10:07:27','2026-05-20 10:07:27',0),(2056923056769699842,'2056923056245411840',2056617382194016258,59.80,3,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','送货上门','2026-05-20 10:22:21','2026-05-20 10:20:47','2026-05-20 10:22:20',0),(2059898538086621185,'2059898535796531200',2056617382194016258,688.00,3,'huqi','19318563161','湖南省长沙市天心区中南大学铁道校区','请送货上门','2026-05-28 15:25:37','2026-05-28 15:24:17','2026-05-28 15:25:38',0),(2060318252236259330,'2060318251191877632',2056617382194016258,897.90,4,'huqi','19318563161','湖南省长沙市天心区中南大学铁道校区','送货上门',NULL,'2026-05-29 19:12:05','2026-05-29 19:12:05',0),(2061287957809836034,'2061287955964342272',2056617382194016258,577.00,4,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','记得送货上门！！！',NULL,'2026-06-01 11:25:21','2026-06-01 11:25:21',0),(2061290075308384258,'2061290074167533568',2056617382194016258,457.00,4,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','一定要记得送货上门！！！',NULL,'2026-06-01 11:33:46','2026-06-01 11:33:46',0),(2061339952205721601,'2061339951794679808',2056617382194016258,817.00,4,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','一定要记得送货上门！！！',NULL,'2026-06-01 14:51:57','2026-06-01 14:51:57',0),(2061342315842871298,'2061342315427635200',2056617382194016258,857.00,3,'huqi','19318563161','湖南省长沙市天心区中南大学铁道校区','记得要送货上门！！！','2026-06-01 15:03:08','2026-06-01 15:01:21','2026-06-01 15:03:07',0),(2061442024041336834,'2061441978877071360',2056617382194016258,347.50,3,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','一定要记得送货上门！！！不要送到菜鸟驿站！！！','2026-06-01 21:38:25','2026-06-01 21:37:33','2026-06-01 21:38:25',0),(2062068425350426625,'2062068424960356352',2056617382194016258,794.00,3,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','手工牛肉干不要有牛肉，跑鞋不要鞋','2026-06-03 15:08:52','2026-06-03 15:06:39','2026-06-03 15:08:52',0),(2062417242959138817,'2062417242124472320',2056372672016793601,237.00,2,'adminlu','16608188853','四川省成都市武侯区四川大学','记得送到菜鸟驿站或近邻宝','2026-06-04 14:14:52','2026-06-04 14:12:43','2026-07-16 09:27:50',0),(2062436987737575425,'2062436986072436736',2056617382194016258,59.00,4,'huqi','19318563161','湖南省长沙市天心区中南大学铁道校区','送到菜鸟驿站',NULL,'2026-06-04 15:31:11','2026-06-04 15:31:11',0),(2077563989348986882,'2077563988736110592',2056617382194016258,906.00,4,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','记得送货上门!!!',NULL,'2026-07-16 09:20:29','2026-07-16 10:04:08',0),(2077575377660403714,'2077575377152892928',2056617382194016258,755.00,3,'huqi','19318563161','湖南省长沙市岳麓区中南大学岳麓山校区12舍北','记得要送货上门，或者是放到菜鸟驿站','2026-07-16 10:06:41','2026-07-16 10:05:44','2026-07-16 10:28:27',0),(2097303281549324290,'2097303280488804352',880009082036,2.00,4,'CXLIVE0908_2036','13000000000','TESTTESTTESTDedicated integration test, no delivery','CXLIVE0908_2036 integration test - no payment',NULL,'2026-09-08 20:37:23','2026-09-08 20:37:23',0),(2104783067102871554,'2104783022234218496',2056617382194016258,533.90,4,'huqi','19318563161','湖南省长沙市天心区中南大学铁道校区','放在菜鸟驿站',NULL,'2026-09-29 11:59:23','2026-09-29 12:29:24',0);
/*!40000 ALTER TABLE `order_info` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `product_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名称',
  `product_image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商品图片',
  `price` decimal(10,2) NOT NULL COMMENT '商品单价',
  `quantity` int NOT NULL COMMENT '数量',
  `total_amount` decimal(10,2) NOT NULL COMMENT '小计金额',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `order_item` WRITE;
/*!40000 ALTER TABLE `order_item` DISABLE KEYS */;
INSERT INTO `order_item` (`id`, `order_id`, `order_no`, `product_id`, `product_name`, `product_image`, `price`, `quantity`, `total_amount`, `create_time`, `update_time`, `deleted`) VALUES (2056641088605749249,2056641088324730882,'2056641087188074496',1001,'iPhone 16 Pro Max','https://img.alicdn.com/imgextra/i3/2602899056/O1CN012AwYr52Glhu5P9W3d_!!2602899056.jpg_q50.jpg_.webp',8999.00,1,8999.00,'2026-05-19 15:40:21','2026-05-19 15:40:21',0),(2056913631468163073,2056913631250059265,'2056913630662856704',2056360000000000037,'《深入理解Java虚拟机》第3版','http://192.168.91.130:9000/cloudmall-products/product/7d531c43-3b4c-40fd-b8dc-6afd1446e023.jpg',99.00,1,99.00,'2026-05-20 09:43:20','2026-05-20 09:43:20',0),(2056919701846601730,2056919701846601729,'2056919701662052352',2056360000000000038,'《三体》全集 刘慈欣','http://192.168.91.130:9000/cloudmall-products/product/c28e2a58-2576-44da-9e24-6ba4df9d44a4.webp',68.00,1,68.00,'2026-05-20 10:07:27','2026-05-20 10:07:27',0),(2056923056786477058,2056923056769699842,'2056923056245411840',2056360000000000033,'四川耙耙柑 5斤',NULL,29.90,2,59.80,'2026-05-20 10:20:47','2026-05-20 10:20:47',0),(2059898538422165505,2059898538086621185,'2059898535796531200',2056360000000000031,'每日坚果 750g','http://192.168.91.130:9000/cloudmall-products/product/f9249623-d81c-421d-b7a5-a529b162cd10.webp',89.00,1,89.00,'2026-05-28 15:24:17','2026-05-28 15:24:17',0),(2059898538619297793,2059898538086621185,'2059898535796531200',2056360000000000028,'云速 UltraBoost 跑鞋',NULL,599.00,1,599.00,'2026-05-28 15:24:17','2026-05-28 15:24:17',0),(2060318294959439874,2060318252236259330,'2060318251191877632',2056360000000000028,'云速 UltraBoost 跑鞋','http://192.168.91.130:9000/cloudmall-products/product/259b2008-3bfe-4b07-9eb7-352d4f46842c.webp',599.00,1,599.00,'2026-05-29 19:12:15','2026-05-29 19:12:15',0),(2060318294959439875,2060318252236259330,'2060318251191877632',2056360000000000026,'法式碎花连衣裙',NULL,269.00,1,269.00,'2026-05-29 19:12:15','2026-05-29 19:12:15',0),(2060318295026548738,2060318252236259330,'2060318251191877632',2056360000000000033,'四川耙耙柑 5斤','http://192.168.91.130:9000/cloudmall-products/product/ad5e32df-0f3d-412e-bca5-a1d307c3f945.webp',29.90,1,29.90,'2026-05-29 19:12:15','2026-05-29 19:12:15',0),(2061287957948248065,2061287957809836034,'2061287955964342272',2056360000000000030,'商务双肩包 大容量','http://192.168.91.130:9000/cloudmall-products/product/451ea082-ebae-412f-8dfb-3d50c2212032.webp',259.00,2,518.00,'2026-06-01 11:25:21','2026-06-01 11:25:21',0),(2061287958011162626,2061287957809836034,'2061287955964342272',2056360000000000032,'手工牛肉干 麻辣 250g',NULL,59.00,1,59.00,'2026-06-01 11:25:21','2026-06-01 11:25:21',0),(2061290075337744385,2061290075308384258,'2061290074167533568',2056360000000000030,'商务双肩包 大容量','http://192.168.91.130:9000/cloudmall-products/product/451ea082-ebae-412f-8dfb-3d50c2212032.webp',259.00,1,259.00,'2026-06-01 11:33:46','2026-06-01 11:33:46',0),(2061290075404853249,2061290075308384258,'2061290074167533568',2056360000000000037,'《深入理解Java虚拟机》第3版','http://192.168.91.130:9000/cloudmall-products/product/7d531c43-3b4c-40fd-b8dc-6afd1446e023.jpg',99.00,2,198.00,'2026-06-01 11:33:46','2026-06-01 11:33:46',0),(2061339952205721602,2061339952205721601,'2061339951794679808',2056360000000000030,'商务双肩包 大容量','http://192.168.91.130:9000/cloudmall-products/product/451ea082-ebae-412f-8dfb-3d50c2212032.webp',259.00,2,518.00,'2026-06-01 14:51:57','2026-06-01 14:51:57',0),(2061339952251858945,2061339952205721601,'2061339951794679808',2056360000000000029,'经典百搭小白鞋',NULL,299.00,1,299.00,'2026-06-01 14:51:57','2026-06-01 14:51:57',0),(2061342315876425729,2061342315842871298,'2061342315427635200',2056360000000000030,'商务双肩包 大容量','http://192.168.91.130:9000/cloudmall-products/product/451ea082-ebae-412f-8dfb-3d50c2212032.webp',259.00,1,259.00,'2026-06-01 15:01:21','2026-06-01 15:01:21',0),(2061342315876425730,2061342315842871298,'2061342315427635200',2056360000000000029,'经典百搭小白鞋',NULL,299.00,2,598.00,'2026-06-01 15:01:21','2026-06-01 15:01:21',0),(2061442024167165953,2061442024041336834,'2061441978877071360',2056360000000000033,'四川耙耙柑 5斤','http://192.168.91.130:9000/cloudmall-products/product/ad5e32df-0f3d-412e-bca5-a1d307c3f945.webp',29.90,5,149.50,'2026-06-01 21:37:33','2026-06-01 21:37:33',0),(2061442024297189378,2061442024041336834,'2061441978877071360',2056360000000000037,'《深入理解Java虚拟机》第3版','http://192.168.91.130:9000/cloudmall-products/product/7d531c43-3b4c-40fd-b8dc-6afd1446e023.jpg',99.00,2,198.00,'2026-06-01 21:37:33','2026-06-01 21:37:33',0),(2062068425350426626,2062068425350426625,'2062068424960356352',2056360000000000032,'手工牛肉干 麻辣 250g',NULL,59.00,1,59.00,'2026-06-03 15:06:39','2026-06-03 15:06:39',0),(2062068425350426627,2062068425350426625,'2062068424960356352',2056360000000000038,'《三体》全集 刘慈欣','http://192.168.91.130:9000/cloudmall-products/product/c28e2a58-2576-44da-9e24-6ba4df9d44a4.webp',68.00,2,136.00,'2026-06-03 15:06:39','2026-06-03 15:06:39',0),(2062068425388175361,2062068425350426625,'2062068424960356352',2056360000000000028,'云速 UltraBoost 跑鞋','http://192.168.91.130:9000/cloudmall-products/product/259b2008-3bfe-4b07-9eb7-352d4f46842c.webp',599.00,1,599.00,'2026-06-03 15:06:39','2026-06-03 15:06:39',0),(2062417242992693250,2062417242959138817,'2062417242124472320',2056360000000000032,'手工牛肉干 麻辣 250g','http://192.168.91.130:9000/cloudmall-products/product/9f78e392-efd2-400b-80fb-4d6ac84d2799.webp',59.00,1,59.00,'2026-06-04 14:12:43','2026-06-04 14:12:43',0),(2062417243059802113,2062417242959138817,'2062417242124472320',2056360000000000031,'每日坚果 750g','http://192.168.91.130:9000/cloudmall-products/product/f9249623-d81c-421d-b7a5-a529b162cd10.webp',89.00,2,178.00,'2026-06-04 14:12:43','2026-06-04 14:12:43',0),(2062436987867598849,2062436987737575425,'2062436986072436736',2056360000000000032,'手工牛肉干 麻辣 250g','http://192.168.91.130:9000/cloudmall-products/product/9f78e392-efd2-400b-80fb-4d6ac84d2799.webp',59.00,1,59.00,'2026-06-04 15:31:11','2026-06-04 15:31:11',0),(2077563989348986883,2077563989348986882,'2077563988736110592',2056360000000000034,'智利车厘子 JJ级 2斤',NULL,99.00,1,99.00,'2026-07-16 09:20:29','2026-07-16 09:20:29',0),(2077563989416095746,2077563989348986882,'2077563988736110592',2056360000000000026,'法式碎花连衣裙','http://192.168.91.130:9000/cloudmall-products/product/9498470a-d42c-429a-8762-fd3ad72fff8a.webp',269.00,3,807.00,'2026-07-16 09:20:29','2026-07-16 09:20:29',0),(2077575377731706882,2077575377660403714,'2077575377152892928',2056360000000000037,'《深入理解Java虚拟机》第3版','http://192.168.91.130:9000/cloudmall-products/product/7d531c43-3b4c-40fd-b8dc-6afd1446e023.jpg',99.00,1,99.00,'2026-07-16 10:05:44','2026-07-16 10:05:44',0),(2077575377731706883,2077575377660403714,'2077575377152892928',2056360000000000026,'法式碎花连衣裙','http://192.168.91.130:9000/cloudmall-products/product/9498470a-d42c-429a-8762-fd3ad72fff8a.webp',269.00,2,538.00,'2026-07-16 10:05:44','2026-07-16 10:05:44',0),(2077575377731706884,2077575377660403714,'2077575377152892928',2056360000000000032,'手工牛肉干 麻辣 250g','http://192.168.91.130:9000/cloudmall-products/product/9f78e392-efd2-400b-80fb-4d6ac84d2799.webp',59.00,2,118.00,'2026-07-16 10:05:44','2026-07-16 10:05:44',0),(2097303281549324291,2097303281549324290,'2097303280488804352',2097303000501592065,'CXLIVE0908_2036','',1.00,2,2.00,'2026-09-08 20:37:23','2026-09-08 20:37:23',0),(2104783067157397505,2104783067102871554,'2104783022234218496',2056360000000000030,'商务双肩包 大容量','http://192.168.91.130:9000/cloudmall-products/product/451ea082-ebae-412f-8dfb-3d50c2212032.webp',259.00,2,518.00,'2026-09-29 11:59:23','2026-09-29 11:59:23',0),(2104783067224506370,2104783067102871554,'2104783022234218496',2056360000000000039,'子弹头中性笔 20支',NULL,15.90,1,15.90,'2026-09-29 11:59:23','2026-09-29 11:59:23',0);
/*!40000 ALTER TABLE `order_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `order_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_request` (
  `token` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `user_id` bigint NOT NULL,
  `active_user_id` bigint DEFAULT NULL,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `snapshot` longtext COLLATE utf8mb4_unicode_ci,
  `failure_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `compensate_count` int NOT NULL DEFAULT '0' COMMENT '库存补偿已尝试次数（上限10次）',
  PRIMARY KEY (`token`),
  UNIQUE KEY `uk_order_request_order_no` (`order_no`),
  UNIQUE KEY `uk_order_request_active_user` (`active_user_id`),
  KEY `idx_order_request_recovery` (`status`,`updated_at`),
  KEY `idx_order_request_user_created` (`user_id`,`created_at`),
  CONSTRAINT `chk_order_request_status` CHECK ((`status` in (_ascii'READY',_ascii'PROCESSING',_ascii'SUCCEEDED',_ascii'COMPENSATING',_ascii'FAILED',_ascii'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Durable checkout request and recovery state';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `order_request` WRITE;
/*!40000 ALTER TABLE `order_request` DISABLE KEYS */;
INSERT INTO `order_request` (`token`, `user_id`, `active_user_id`, `order_no`, `status`, `snapshot`, `failure_reason`, `created_at`, `updated_at`, `expires_at`, `compensate_count`) VALUES ('2561339c-381d-45b1-bd26-030c2d90f3a1',2056617382194016258,NULL,'2104783022234218496','SUCCEEDED','{\"orderItems\":[{\"orderNo\":\"2104783022234218496\",\"price\":259.00,\"productId\":2056360000000000030,\"productImage\":\"http://192.168.91.130:9000/cloudmall-products/product/451ea082-ebae-412f-8dfb-3d50c2212032.webp\",\"productName\":\"商务双肩包 大容量\",\"quantity\":2,\"totalAmount\":518.00},{\"orderNo\":\"2104783022234218496\",\"price\":15.90,\"productId\":2056360000000000039,\"productName\":\"子弹头中性笔 20支\",\"quantity\":1,\"totalAmount\":15.90}],\"orderNo\":\"2104783022234218496\",\"receiverAddress\":\"湖南省长沙市天心区中南大学铁道校区\",\"receiverName\":\"huqi\",\"receiverPhone\":\"19318563161\",\"remark\":\"放在菜鸟驿站\",\"status\":0,\"totalAmount\":533.90,\"userId\":2056617382194016258}',NULL,'2026-09-29 11:59:12.135800','2026-09-29 11:59:22.945397','2026-09-29 12:29:12.135800',0);
/*!40000 ALTER TABLE `order_request` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `outbox_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbox_message` (
  `id` bigint NOT NULL COMMENT '主键',
  `topic` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Kafka Topic',
  `message_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '消息Key',
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息体 JSON',
  `status` tinyint DEFAULT '0' COMMENT '0-待发送 1-已发送 2-失败',
  `retry_count` int DEFAULT '0' COMMENT '已重试次数',
  `max_retries` int DEFAULT '5' COMMENT '最大重试次数',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_retry` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息发件箱表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `outbox_message` WRITE;
/*!40000 ALTER TABLE `outbox_message` DISABLE KEYS */;
INSERT INTO `outbox_message` (`id`, `topic`, `message_key`, `payload`, `status`, `retry_count`, `max_retries`, `next_retry_at`, `create_time`, `update_time`) VALUES (2062068425455284225,'order-create','2062068424960356352','{\"totalAmount\":794.00,\"orderNo\":\"2062068424960356352\",\"orderId\":2062068425350426625,\"createTime\":\"2026-06-03T15:06:38.639131300\",\"userId\":2056617382194016258}',1,0,5,NULL,'2026-06-03 15:06:39','2026-06-03 15:06:42'),(2062068964897308673,'payment-result','2062068424960356352','{\"orderNo\":\"2062068424960356352\",\"status\":\"SUCCESS\"}',1,0,5,NULL,'2026-06-03 15:08:47','2026-06-03 15:08:52'),(2062417243328237569,'order-create','2062417242124472320','{\"totalAmount\":237.00,\"orderNo\":\"2062417242124472320\",\"orderId\":2062417242959138817,\"createTime\":\"2026-06-04T14:12:43.241021300\",\"userId\":2056372672016793601}',1,0,5,NULL,'2026-06-04 14:12:43','2026-06-04 14:12:45'),(2062417773366640641,'payment-result','2062417242124472320','{\"orderNo\":\"2062417242124472320\",\"status\":\"SUCCESS\"}',1,0,5,NULL,'2026-06-04 14:14:50','2026-06-04 14:14:52'),(2062436988270252033,'order-create','2062436986072436736','{\"totalAmount\":59.00,\"orderNo\":\"2062436986072436736\",\"orderId\":2062436987737575425,\"createTime\":\"2026-06-04T15:31:10.762677\",\"userId\":2056617382194016258}',1,0,5,NULL,'2026-06-04 15:31:11','2026-06-04 15:31:11'),(2077563989483204609,'order-create','2077563988736110592','{\"totalAmount\":906.00,\"orderNo\":\"2077563988736110592\",\"orderId\":2077563989348986882,\"createTime\":\"2026-07-16T09:20:28.990894400\",\"userId\":2056617382194016258}',1,0,5,'2026-07-16 09:22:29','2026-07-16 09:20:29','2026-07-16 09:20:29'),(2077575377798815746,'order-create','2077575377152892928','{\"totalAmount\":755.00,\"orderNo\":\"2077575377152892928\",\"orderId\":2077575377660403714,\"createTime\":\"2026-07-16T10:05:44.177315200\",\"userId\":2056617382194016258}',1,0,5,'2026-07-16 10:07:47','2026-07-16 10:05:44','2026-07-16 10:05:47'),(2077575602537992194,'payment-result','2077575377152892928','{\"orderNo\":\"2077575377152892928\",\"status\":\"SUCCESS\",\"tradeNo\":\"2026071622001465040509430384\"}',1,0,5,'2026-07-16 10:08:41','2026-07-16 10:06:38','2026-07-16 10:06:40'),(2097303281733873666,'order-create','2097303280488804352','{\"totalAmount\":2.00,\"orderNo\":\"2097303280488804352\",\"orderId\":2097303281549324290,\"createTime\":\"2026-09-08T20:37:22.991220\",\"userId\":880009082036}',1,0,5,'2026-09-08 20:39:25','2026-09-08 20:37:23','2026-09-08 20:37:25'),(2097303284921544705,'stock-restore','2097303280488804352:2097303000501592065','{\"orderNo\":\"2097303280488804352\",\"orderId\":2097303281549324290,\"productId\":2097303000501592065,\"quantity\":2,\"idempotencyKey\":\"2097303280488804352:2097303000501592065\"}',1,0,5,'2026-09-08 20:39:26','2026-09-08 20:37:24','2026-09-08 20:37:25'),(2104783067224506371,'order-create','2104783022234218496','{\"totalAmount\":533.90,\"orderNo\":\"2104783022234218496\",\"orderId\":2104783067102871554,\"createTime\":\"2026-09-29T11:59:22.835672600\",\"userId\":2056617382194016258}',1,0,5,'2026-09-29 12:01:28','2026-09-29 11:59:23','2026-09-29 11:59:28'),(2104783067291615234,'cart-clear','2104783022234218496','{\"userId\":2056617382194016258,\"productIds\":[2056360000000000030,2056360000000000039]}',1,0,5,'2026-09-29 12:01:28','2026-09-29 11:59:23','2026-09-29 11:59:28'),(2104790620591390722,'stock-restore','2104783022234218496:2056360000000000030','{\"orderNo\":\"2104783022234218496\",\"orderId\":2104783067102871554,\"productId\":2056360000000000030,\"quantity\":2,\"idempotencyKey\":\"2104783022234218496:2056360000000000030\"}',1,0,5,'2026-09-29 12:31:28','2026-09-29 12:29:24','2026-09-29 12:29:28'),(2104790620654305282,'stock-restore','2104783022234218496:2056360000000000039','{\"orderNo\":\"2104783022234218496\",\"orderId\":2104783067102871554,\"productId\":2056360000000000039,\"quantity\":1,\"idempotencyKey\":\"2104783022234218496:2056360000000000039\"}',1,0,5,'2026-09-29 12:31:28','2026-09-29 12:29:24','2026-09-29 12:29:28'),(2104790620654305283,'payment-close','2104783022234218496','{\"orderNo\":\"2104783022234218496\"}',1,0,5,'2026-09-29 12:31:28','2026-09-29 12:29:24','2026-09-29 12:29:28');
/*!40000 ALTER TABLE `outbox_message` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `pay_method` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'WECHAT' COMMENT '支付方式: WECHAT/ALIPAY',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-待支付 1-支付成功 2-支付失败',
  `trade_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方交易号',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `last_sync_time` datetime DEFAULT NULL COMMENT '上次同步支付宝时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `payment` WRITE;
/*!40000 ALTER TABLE `payment` DISABLE KEYS */;
INSERT INTO `payment` (`id`, `order_no`, `user_id`, `amount`, `pay_method`, `status`, `trade_no`, `pay_time`, `create_time`, `update_time`, `deleted`, `last_sync_time`) VALUES (2056641144805224450,'2056641087188074496',2056617382194016258,8999.00,'ALIPAY',1,'2056641144750698496','2026-05-19 15:40:34','2026-05-19 15:40:34','2026-09-29 12:30:18',0,'2026-09-29 12:30:18'),(2056913678658281473,'2056913630662856704',2056617382194016258,99.00,'ALIPAY',0,NULL,NULL,'2026-05-20 09:43:31','2026-09-29 12:32:50',0,'2026-09-29 12:32:50'),(2056919704312844290,'2056919701662052352',2056617382194016258,68.00,'ALIPAY',0,NULL,NULL,'2026-05-20 10:07:28','2026-09-29 12:32:51',0,'2026-09-29 12:32:51'),(2056923102772822018,'2056923056245411840',2056617382194016258,59.80,'ALIPAY',1,'2026052022001465040509143780','2026-05-20 10:22:21','2026-05-20 10:20:58','2026-09-29 12:30:18',0,'2026-09-29 12:30:18'),(2059898594432868353,'2059898535796531200',2056617382194016258,688.00,'ALIPAY',1,'2026052822001465040509187724','2026-05-28 15:25:37','2026-05-28 15:24:31','2026-09-29 12:30:18',0,'2026-09-29 12:30:18'),(2060318339997884417,'2060318251191877632',2056617382194016258,897.90,'ALIPAY',0,NULL,NULL,'2026-05-29 19:12:26','2026-09-29 12:32:51',0,'2026-09-29 12:32:51'),(2061288009957629954,'2061287955964342272',2056617382194016258,577.00,'ALIPAY',0,NULL,NULL,'2026-06-01 11:25:33','2026-09-29 12:32:52',0,'2026-09-29 12:32:52'),(2061290077753581570,'2061290074167533568',2056617382194016258,457.00,'ALIPAY',0,NULL,NULL,'2026-06-01 11:33:46','2026-09-29 12:32:52',0,'2026-09-29 12:32:52'),(2061339997353181186,'2061339951794679808',2056617382194016258,817.00,'ALIPAY',0,NULL,NULL,'2026-06-01 14:52:08','2026-09-29 12:32:52',0,'2026-09-29 12:32:52'),(2061342360378003457,'2061342315427635200',2056617382194016258,857.00,'ALIPAY',1,'2026060122001465040509197364','2026-06-01 15:03:07','2026-06-01 15:01:31','2026-09-29 12:30:18',0,'2026-09-29 12:30:18'),(2061442069910208514,'2061441978877071360',2056617382194016258,347.50,'ALIPAY',1,'2026060122001465040509203828','2026-06-01 21:38:25','2026-06-01 21:37:44','2026-09-29 12:30:18',0,'2026-09-29 12:30:18'),(2062068442337361922,'2062068424960356352',2056617382194016258,794.00,'ALIPAY',1,'2026060322001465040509212831','2026-06-03 15:08:47','2026-06-03 15:06:43','2026-09-29 12:30:18',0,'2026-09-29 12:30:18'),(2062417255286210561,'2062417242124472320',2056372672016793601,237.00,'ALIPAY',1,'2026060422001465040509217333','2026-06-04 14:14:50','2026-06-04 14:12:46','2026-09-29 12:30:18',0,'2026-09-29 12:30:18'),(2062436994490368001,'2062436986072436736',2056617382194016258,59.00,'ALIPAY',0,NULL,NULL,'2026-06-04 15:31:12','2026-09-29 12:32:51',0,'2026-09-29 12:32:51'),(2077563992054333442,'2077563988736110592',2056617382194016258,906.00,'ALIPAY',0,NULL,NULL,'2026-07-16 09:20:30','2026-09-29 12:32:51',0,'2026-09-29 12:32:51'),(2077575393296748545,'2077575377152892928',2056617382194016258,755.00,'ALIPAY',1,'2026071622001465040509430384','2026-07-16 10:06:38','2026-07-16 10:05:48','2026-09-29 12:30:18',0,'2026-09-29 12:30:18'),(2104783088321937410,'2104783022234218496',2056617382194016258,533.90,'ALIPAY',3,NULL,NULL,'2026-09-29 11:59:28','2026-09-29 12:29:28',0,'2026-09-29 12:29:08');
/*!40000 ALTER TABLE `payment` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `id` bigint NOT NULL COMMENT '主键',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '商品描述',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存',
  `sales` int DEFAULT '0' COMMENT '销量',
  `main_image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主图URL',
  `images` json DEFAULT NULL COMMENT '图片列表(JSON数组)',
  `status` tinyint DEFAULT '1' COMMENT '状态: 0-下架 1-上架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `seller_id` bigint DEFAULT NULL COMMENT '卖家用户ID',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_seller_id` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` (`id`, `category_id`, `name`, `description`, `price`, `stock`, `sales`, `main_image`, `images`, `status`, `create_time`, `update_time`, `deleted`, `seller_id`) VALUES (2056360000000000001,2056350000000000101,'云速 X1 5G手机','6.7英寸OLED | 骁龙8Gen3 | 12GB+256GB | 120W快充',4299.00,200,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000002,2056350000000000101,'云速 X1 Pro 旗舰版','6.8英寸2K曲面 | 骁龙8Gen3 | 16GB+512GB | 200W快充',5999.00,150,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000003,2056350000000000101,'云速 Note 50','6.6英寸LCD | 天玑8300 | 8GB+128GB | 6000mAh',1999.00,500,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000004,2056350000000000101,'云速 A60 入门机','6.5英寸屏 | 天玑6100+ | 6GB+128GB | 长辈模式',899.00,800,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000005,2056350000000000102,'云速 Pad Pro 12.4','12.4英寸2.8K OLED | 骁龙8Gen2 | 10000mAh | 手写笔套装',3499.00,120,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000006,2056350000000000102,'云速 Pad SE 10.4','10.4英寸2K屏 | 骁龙680 | 8000mAh | 学生护眼',1499.00,300,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000007,2056350000000000103,'云速 Watch GT 智能手表','1.43英寸AMOLED | eSIM通话 | 心率血氧 | 14天续航',1299.00,250,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000008,2056350000000000103,'云速 Band 8 智能手环','1.62英寸AMOLED | 100+运动 | 血氧 | 磁吸快充',299.00,600,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000009,2056350000000000104,'云速 Buds Pro 降噪耳机','主动降噪 | LDAC | 40小时续航 | IP55防水',699.00,400,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000010,2056350000000000104,'云速 SoundBox 蓝牙音箱','30W大功率 | 360°环绕 | IPX7防水 | 20小时',399.00,350,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000011,2056350000000000201,'云速 Book Pro 16','16英寸3K | Ultra 7 | 32GB+1TB | 1.3kg',6499.00,80,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000012,2056350000000000201,'云速 Book 14','14英寸2K | i5-13500H | 16GB+512GB | 全金属',3999.00,150,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000013,2056350000000000202,'云速 MechKey K1 机械键盘','87键 | 热插拔 | RGB背光 | 三模连接',299.00,500,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000014,2056350000000000202,'云速 Ergo 人体工学鼠标','垂直握持 | 4000DPI | 2.4G+蓝牙 | 充电款',149.00,600,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000015,2056350000000000203,'云速 SSD 1TB 移动固态','读取2000MB/s | Type-C | 名片大小 | 防震',599.00,300,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000016,2056350000000000203,'云速 U盘 256GB','USB3.2 | 读取400MB/s | 金属 | 钥匙扣',89.00,1000,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000017,2056350000000000301,'云速 IH电饭煲 4L','IH电磁加热 | 24h预约 | 不粘内胆 | 内置菜谱',399.00,200,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000018,2056350000000000301,'云速空气炸锅 5.5L','360°热风 | 8大菜单 | 不粘 | 可视窗口',299.00,250,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000019,2056350000000000301,'云速破壁机 1200W','8叶刀头 | 免滤 | 12h预约 | 自动清洗',499.00,180,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000020,2056350000000000302,'云速无线吸尘器 T10','180AW吸力 | 60分钟 | 5重过滤 | 磁吸充电',1299.00,100,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000021,2056350000000000302,'云速扫地机器人 R1','激光导航 | 扫拖一体 | 5000Pa | 自动回充',1999.00,80,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000022,2056350000000000303,'云速电动牙刷 S1','声波震动 | 5种模式 | IPX7 | 90天续航',199.00,400,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000023,2056350000000000303,'云速负离子吹风机','2000W | 负离子护发 | 恒温 | 磁吸风嘴',249.00,300,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000024,2056350000000000401,'商务免烫衬衫','新疆长绒棉 | 免烫 | 修身 | 多色可选',199.00,500,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000025,2056350000000000401,'夏季冰丝防晒衣','UPF50+ | 冰丝面料 | 超薄透气 | 连帽',149.00,600,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000026,2056350000000000402,'法式碎花连衣裙','雪纺 | 收腰 | 复古碎花 | 春夏新款',269.00,348,2,'http://192.168.91.130:9000/cloudmall-products/product/9498470a-d42c-429a-8762-fd3ad72fff8a.webp',NULL,1,'2026-05-19 17:20:30','2026-07-16 10:05:44',0,2056341213168037890),(2056360000000000027,2056350000000000402,'高腰阔腿裤女','垂感 | 松紧腰 | 显瘦 | 百搭通勤',189.00,450,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000028,2056350000000000403,'云速 UltraBoost 跑鞋','全掌碳板 | 超临界发泡 | 透气飞织 | 竞速',599.00,298,2,'http://192.168.91.130:9000/cloudmall-products/product/259b2008-3bfe-4b07-9eb7-352d4f46842c.webp',NULL,1,'2026-05-19 17:20:30','2026-06-03 15:06:39',0,2056341213168037890),(2056360000000000029,2056350000000000403,'经典百搭小白鞋','头层牛皮 | 橡胶大底 | 舒适内里 | 四季',299.00,498,2,'http://192.168.91.130:9000/cloudmall-products/product/386e2dbb-2565-44b9-b0d9-40ed738dec05.webp',NULL,1,'2026-05-19 17:20:30','2026-06-03 15:03:23',0,2056341213168037890),(2056360000000000030,2056350000000000404,'商务双肩包 大容量','防泼水 | 电脑仓 | USB充电口 | 人体工学',259.00,349,1,'http://192.168.91.130:9000/cloudmall-products/product/451ea082-ebae-412f-8dfb-3d50c2212032.webp',NULL,1,'2026-05-19 17:20:30','2026-09-29 12:29:28',0,2056341213168037890),(2056360000000000031,2056350000000000501,'每日坚果 750g','6种坚果果干 | 独立小包 | 无添加',89.00,797,3,'http://192.168.91.130:9000/cloudmall-products/product/f9249623-d81c-421d-b7a5-a529b162cd10.webp',NULL,1,'2026-05-19 17:20:30','2026-06-04 14:12:43',0,2056341213168037890),(2056360000000000032,2056350000000000501,'手工牛肉干 麻辣 250g','内蒙古风干 | 独立包装 | 麻辣鲜香',59.00,596,4,'http://192.168.91.130:9000/cloudmall-products/product/9f78e392-efd2-400b-80fb-4d6ac84d2799.webp',NULL,1,'2026-05-19 17:20:30','2026-07-16 10:05:44',0,2056341213168037890),(2056360000000000033,2056350000000000502,'四川耙耙柑 5斤','产地直发 | 酸甜多汁 | 坏果包赔',29.90,993,7,'http://192.168.91.130:9000/cloudmall-products/product/ad5e32df-0f3d-412e-bca5-a1d307c3f945.webp',NULL,1,'2026-05-19 17:20:30','2026-06-01 21:37:32',0,2056341213168037890),(2056360000000000034,2056350000000000502,'智利车厘子 JJ级 2斤','空运直达 | 28mm+ | 脆甜 | 顺丰冷链',99.00,500,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-07-16 10:04:08',0,2056341213168037890),(2056360000000000035,2056350000000000503,'精品挂耳咖啡 30袋','阿拉比卡 | 中度烘焙 | 氮气锁鲜 | 三种风味',79.00,500,0,'http://192.168.91.130:9000/cloudmall-products/product/c79488a0-e28e-421a-95b2-b452d12e80ed.jpg',NULL,1,'2026-05-19 17:20:30','2026-09-29 12:03:16',0,2056341213168037890),(2056360000000000036,2056350000000000503,'云南普洱生茶 357g','勐海古树 | 石磨压制 | 回甘生津',168.00,300,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000037,2056350000000000601,'《深入理解Java虚拟机》第3版','周志明 | JVM调优 | 面试必备 | 豆瓣9.1',99.00,396,4,'http://192.168.91.130:9000/cloudmall-products/product/7d531c43-3b4c-40fd-b8dc-6afd1446e023.jpg',NULL,1,'2026-05-19 17:20:30','2026-07-16 10:05:44',0,2056341213168037890),(2056360000000000038,2056350000000000601,'《三体》全集 刘慈欣','雨果奖 | 科幻巅峰 | 精装3册',68.00,597,3,'http://192.168.91.130:9000/cloudmall-products/product/c28e2a58-2576-44da-9e24-6ba4df9d44a4.webp',NULL,1,'2026-05-19 17:20:30','2026-06-03 15:06:39',0,2056341213168037890),(2056360000000000039,2056350000000000602,'子弹头中性笔 20支','0.5mm | 速干不晕染 | 顺滑书写',15.90,2000,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-09-29 12:29:28',0,2056341213168037890),(2056360000000000040,2056350000000000602,'方格笔记本 A5 6本装','80g加厚 | 180°平摊 | 可撕页',29.90,800,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000041,2056350000000000701,'云速瑜伽垫 加厚6mm','TPE环保 | 双面防滑 | 体位线 | 送绑带',89.00,500,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000042,2056350000000000701,'可调节哑铃 20kg套装','快速调节 | 包胶静音 | 防滑握把',299.00,200,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000043,2056350000000000702,'户外帐篷 3-4人','双层防雨 | 自动速开 | UPF50+ | 送配件',399.00,150,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000044,2056350000000000702,'钛合金便携炉头','钛合金 | 折叠 | 仅重45g | 登山露营',199.00,250,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000045,2056350000000000801,'北欧简约茶几','实木贴皮 | 120x60 | 双层储物 | 环保',699.00,80,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000046,2056350000000000801,'现代简约电视柜','2米长 | 钢化玻璃 | 大容量 | 走线孔',899.00,60,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000047,2056350000000000802,'天然乳胶床垫 1.8m','泰国进口 | 7区支撑 | 透气防螨 | 5cm',1299.00,100,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2056360000000000048,2056350000000000802,'简约实木衣帽架','橡木 | 树形设计 | 落地 | 免工具',169.00,300,0,NULL,NULL,1,'2026-05-19 17:20:30','2026-05-19 17:20:30',0,2056341213168037890),(2097303000501592065,2056350000000000001,'CXLIVE0908_2036','Completed integration test, do not purchase',1.00,100,0,'','[]',0,'2026-09-08 20:36:16','2026-09-08 20:38:12',0,880009082036);
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `seller_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seller_application` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '申请人ID',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卖家申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `seller_application` WRITE;
/*!40000 ALTER TABLE `seller_application` DISABLE KEYS */;
INSERT INTO `seller_application` (`id`, `user_id`, `status`, `create_time`, `update_time`, `deleted`) VALUES (2056373655165743106,2056341213168037890,'APPROVED','2026-05-18 21:57:40','2026-05-18 21:57:40',0),(2059895699855613954,2059833713811611650,'APPROVED','2026-05-28 15:13:01','2026-05-28 15:13:01',0);
/*!40000 ALTER TABLE `seller_application` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL COMMENT '主键',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码(BCrypt加密)',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `status` tinyint DEFAULT '1' COMMENT '状态: 0-禁用 1-正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0-未删除 1-已删除',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUYER' COMMENT '角色',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `phone`, `email`, `avatar`, `status`, `create_time`, `update_time`, `deleted`, `role`) VALUES (880009082036,'CXLIVE0908_2036','DISABLED_TEST_ACCOUNT','CXLIVE0908_2036',NULL,NULL,NULL,0,'2026-09-08 20:36:15','2026-09-08 20:36:15',0,'BUYER'),(1779112285593,'admin','$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi','管理员',NULL,NULL,NULL,1,'2026-05-18 21:51:25','2026-05-18 21:51:25',0,'ADMIN'),(2056341213168037890,'luyue','$2a$10$hSthWIMIyix0AsG7cFCxvelHv9PgJR9xXM6BPux42ZeSlrV4rqXCa','luyue','16608188853','2779902707@qq.com','http://192.168.91.130:9000/cloudmall-products/avatar/35c9975e-f90f-48e3-b3a3-2a29d5aec377.jpg',1,'2026-05-18 19:48:45','2026-05-26 15:50:03',0,'SELLER'),(2056372672016793601,'adminlu','$2a$10$NygUp7SMfmO/J0WlLyOGkeJbw339b9aig2bbkrLa5d7N67JAFrUPm','adminlu',NULL,NULL,NULL,1,'2026-05-18 21:53:45','2026-05-18 21:56:17',0,'ADMIN'),(2056617382194016258,'huqi','$2a$10$SphQmt1qHAT5J3LZsTyMcOOr7yyY4rJQtZFvz2Exkr02odm9UVfw6','huxiaoqi','19318563161','13786139078@163.com','http://192.168.91.130:9000/cloudmall-products/avatar/2620c894-86dc-410b-8e27-735c92956b66.jpg',1,'2026-05-19 14:06:09','2026-06-01 21:26:20',0,'BUYER'),(2059833713811611650,'linwenhao','$2a$10$iwoqt1g0m.Ul1qCUDIGr9e5ilzvFKC5RiSH/hMGt2FaPLUu5UU8vy','linwenhao',NULL,NULL,NULL,1,'2026-05-28 11:06:42','2026-05-28 15:13:15',0,'SELLER');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

SET TIME_ZONE=@OLD_TIME_ZONE;
SET SQL_MODE=@OLD_SQL_MODE;
