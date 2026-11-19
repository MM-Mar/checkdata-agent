-- =============================================================================
-- WARNING: 此文件为项目重构前的旧版备份（原 dodo-agentx 时代）
-- 仅供参考对照，生产环境请勿使用。当前有效脚本请查看 sql/init.sql
-- =============================================================================
-- =============================================================================
-- dodo_agentx 完整建库脚本（MySQL 8.0+）
-- 整合来源：
--   1) Sakila 影视演示库原始 DDL（21 张表）
--   2) AgentX 运行时 5 张表（session/file/memory/skill/trace）
--   3) 权限系统表（sys_dept / sys_role / sys_user / sys_user_role / sys_user_dept）
--   4) user_profile、orders / order_detail 演示业务表
--   5) 对 Sakila 业务表的改造（rental/payment 加 user_id / dept_id）
--
-- 使用方法：
--   CREATE DATABASE dodo_agentx DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   USE dodo_agentx;
--   SOURCE dodo_agentx_full.sql;
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================

-- Part 1. Sakila 业务表（16 张 + staff/store 占位）

-- =============================================================================


DROP TABLE IF EXISTS `country`;
CREATE TABLE `country`  (
  `country_id` smallint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '国家主键ID',
  `country` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '国名',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`country_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 110 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '国家';

DROP TABLE IF EXISTS `city`;
CREATE TABLE `city`  (
  `city_id` smallint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '城市主键ID',
  `city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '城市名',
  `country_id` smallint UNSIGNED NOT NULL COMMENT '所属国家ID',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`city_id`) USING BTREE,
  INDEX `idx_fk_country_id`(`country_id` ASC) USING BTREE,
  CONSTRAINT `fk_city_country` FOREIGN KEY (`country_id`) REFERENCES `country` (`country_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 601 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '城市（属于某个国家）';

DROP TABLE IF EXISTS `address`;
CREATE TABLE `address`  (
  `address_id` smallint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '地址主键ID',
  `address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '详细地址（街道门牌）',
  `address2` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备用地址行（可空）',
  `district` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '区域或省或州',
  `city_id` smallint UNSIGNED NOT NULL COMMENT '所属城市ID',
  `postal_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮政编码',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '联系电话',
  `location` geometry NOT NULL COMMENT '经纬度坐标（GEOMETRY 类型）',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`address_id`) USING BTREE,
  INDEX `idx_fk_city_id`(`city_id` ASC) USING BTREE,
  SPATIAL INDEX `idx_location`(`location`),
  CONSTRAINT `fk_address_city` FOREIGN KEY (`city_id`) REFERENCES `city` (`city_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 606 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '地址（客户、门店、员工的物理地址）';

DROP TABLE IF EXISTS `category`;
CREATE TABLE `category`  (
  `category_id` tinyint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '类别主键ID',
  `name` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类别名称（英文）',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`category_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '影片类别（如 Action、Comedy、Horror）';

DROP TABLE IF EXISTS `language`;
CREATE TABLE `language`  (
  `language_id` tinyint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '语言主键ID',
  `name` char(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '语言名称',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`language_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '语言（影片的语言，如 English、Mandarin）';

DROP TABLE IF EXISTS `film`;
CREATE TABLE `film`  (
  `film_id` smallint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '影片主键ID',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '影片标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '影片简介（长文本）',
  `release_year` year NULL DEFAULT NULL COMMENT '发行的年份',
  `language_id` tinyint UNSIGNED NOT NULL COMMENT '影片语言ID',
  `original_language_id` tinyint UNSIGNED NULL DEFAULT NULL COMMENT '原始语言ID（可空）',
  `rental_duration` tinyint UNSIGNED NOT NULL DEFAULT 3 COMMENT '最长租赁天数（默认3天）',
  `rental_rate` decimal(4, 2) NOT NULL DEFAULT 4.99 COMMENT '单次租赁价（美元，默认4.99）',
  `length` smallint UNSIGNED NULL DEFAULT NULL COMMENT '影片时长（分钟）',
  `replacement_cost` decimal(5, 2) NOT NULL DEFAULT 19.99 COMMENT '丢失或损坏时的赔偿价（默认19.99）',
  `rating` enum('G','PG','PG-13','R','NC-17') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'G' COMMENT '分级（G、PG、PG-13、R、NC-17；年龄限制）',
  `special_features` set('Trailers','Commentaries','Deleted Scenes','Behind the Scenes') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '特殊功能（预告片或评论音轨或删减片段）',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`film_id`) USING BTREE,
  INDEX `idx_title`(`title` ASC) USING BTREE,
  INDEX `idx_fk_language_id`(`language_id` ASC) USING BTREE,
  INDEX `idx_fk_original_language_id`(`original_language_id` ASC) USING BTREE,
  CONSTRAINT `fk_film_language` FOREIGN KEY (`language_id`) REFERENCES `language` (`language_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_film_language_original` FOREIGN KEY (`original_language_id`) REFERENCES `language` (`language_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1001 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '影片（租赁商品主数据，sakila 默认约 1000 部影片）';

DROP TABLE IF EXISTS `film_text`;
CREATE TABLE `film_text`  (
  `film_id` smallint UNSIGNED NOT NULL COMMENT '影片ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '影片标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '影片简介',
  PRIMARY KEY (`film_id`) USING BTREE,
  FULLTEXT INDEX `idx_title_description`(`title`, `description`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '影片文本索引表（film 表的 title 与 description 的镜像，用于全文检索；由触发器自动维护）';

DROP TABLE IF EXISTS `film_category`;
CREATE TABLE `film_category`  (
  `film_id` smallint UNSIGNED NOT NULL COMMENT '影片ID',
  `category_id` tinyint UNSIGNED NOT NULL COMMENT '类别ID',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`film_id`, `category_id`) USING BTREE,
  INDEX `fk_film_category_category`(`category_id` ASC) USING BTREE,
  CONSTRAINT `fk_film_category_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`category_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_film_category_film` FOREIGN KEY (`film_id`) REFERENCES `film` (`film_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '影片与类别的关联表（多对多；一部影片可属于多个类别）';

DROP TABLE IF EXISTS `film_actor`;
CREATE TABLE `film_actor`  (
  `actor_id` smallint UNSIGNED NOT NULL COMMENT '演员ID',
  `film_id` smallint UNSIGNED NOT NULL COMMENT '影片ID',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`actor_id`, `film_id`) USING BTREE,
  INDEX `idx_fk_film_id`(`film_id` ASC) USING BTREE,
  CONSTRAINT `fk_film_actor_actor` FOREIGN KEY (`actor_id`) REFERENCES `actor` (`actor_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_film_actor_film` FOREIGN KEY (`film_id`) REFERENCES `film` (`film_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '影片与演员的关联表（多对多；一部影片有多个演员，一个演员出演多部影片）';

DROP TABLE IF EXISTS `actor`;
CREATE TABLE `actor`  (
  `actor_id` smallint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '演员主键ID',
  `first_name` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '演员名',
  `last_name` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '演员姓',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间（DB自动维护）',
  PRIMARY KEY (`actor_id`) USING BTREE,
  INDEX `idx_actor_last_name`(`last_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 201 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '演员（影视演员主数据）';

DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer`  (
  `customer_id` smallint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '客户主键ID',
  `store_id` tinyint UNSIGNED NOT NULL COMMENT '客户注册的门店ID',
  `first_name` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户名',
  `last_name` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户姓',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户邮箱（可空）',
  `address_id` smallint UNSIGNED NOT NULL COMMENT '客户地址ID',
  `active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否活跃标记（1=有效 0=停用；这是账户状态字段，与「近30天有rental记录」的活跃行为含义不同，写活跃客户SQL时请用lookupGlossary确认口径）',
  `create_date` datetime NOT NULL COMMENT '客户注册时间',
  `last_update` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`customer_id`) USING BTREE,
  INDEX `idx_fk_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_fk_address_id`(`address_id` ASC) USING BTREE,
  INDEX `idx_last_name`(`last_name` ASC) USING BTREE,
  CONSTRAINT `fk_customer_address` FOREIGN KEY (`address_id`) REFERENCES `address` (`address_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_customer_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`store_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 600 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '客户（影视租赁业务的消费者主数据）';

DROP TABLE IF EXISTS `inventory`;
CREATE TABLE `inventory`  (
  `inventory_id` mediumint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存主键ID',
  `film_id` smallint UNSIGNED NOT NULL COMMENT '该拷贝对应的影片ID',
  `store_id` tinyint UNSIGNED NOT NULL COMMENT '该拷贝所在的门店ID',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`inventory_id`) USING BTREE,
  INDEX `idx_fk_film_id`(`film_id` ASC) USING BTREE,
  INDEX `idx_store_id_film_id`(`store_id` ASC, `film_id` ASC) USING BTREE,
  CONSTRAINT `fk_inventory_film` FOREIGN KEY (`film_id`) REFERENCES `film` (`film_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_inventory_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`store_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 4582 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '库存拷贝（一部影片在每个门店有多个物理拷贝，每条记录代表一个可被租赁的拷贝）';

DROP TABLE IF EXISTS `rental`;
CREATE TABLE `rental`  (
  `rental_id` int NOT NULL AUTO_INCREMENT COMMENT '租赁主键ID',
  `rental_date` datetime NOT NULL COMMENT '客户租走的时间',
  `inventory_id` mediumint UNSIGNED NOT NULL COMMENT '被租的库存拷贝ID',
  `customer_id` smallint UNSIGNED NOT NULL COMMENT '租赁的客户ID',
  `return_date` datetime NULL DEFAULT NULL COMMENT '实际归还时间（NULL 表示尚未归还）',
  `staff_id` tinyint UNSIGNED NOT NULL COMMENT '处理本次租赁的员工ID',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`rental_id`) USING BTREE,
  UNIQUE INDEX `rental_date`(`rental_date` ASC, `inventory_id` ASC, `customer_id` ASC) USING BTREE,
  INDEX `idx_fk_inventory_id`(`inventory_id` ASC) USING BTREE,
  INDEX `idx_fk_customer_id`(`customer_id` ASC) USING BTREE,
  INDEX `idx_fk_staff_id`(`staff_id` ASC) USING BTREE,
  CONSTRAINT `fk_rental_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`customer_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_rental_inventory` FOREIGN KEY (`inventory_id`) REFERENCES `inventory` (`inventory_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_rental_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`staff_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 16050 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '租赁记录（每次客户租走一个拷贝就是一条记录；归还时填 return_date）';

DROP TABLE IF EXISTS `payment`;
CREATE TABLE `payment`  (
  `payment_id` smallint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '付款主键ID',
  `customer_id` smallint UNSIGNED NOT NULL COMMENT '付款的客户ID',
  `staff_id` tinyint UNSIGNED NOT NULL COMMENT '收款的员工ID',
  `rental_id` int NULL DEFAULT NULL COMMENT '对应的租赁ID（NULL 表示未关联到具体rental）',
  `amount` decimal(5, 2) NOT NULL COMMENT '付款金额（美元含税）；金额类指标，可SUM求和或AVG求均',
  `payment_date` datetime NOT NULL COMMENT '付款时间；按月或周或日统计收入的关键时间字段',
  `last_update` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`payment_id`) USING BTREE,
  INDEX `idx_fk_staff_id`(`staff_id` ASC) USING BTREE,
  INDEX `idx_fk_customer_id`(`customer_id` ASC) USING BTREE,
  INDEX `fk_payment_rental`(`rental_id` ASC) USING BTREE,
  CONSTRAINT `fk_payment_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`customer_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_payment_rental` FOREIGN KEY (`rental_id`) REFERENCES `rental` (`rental_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_payment_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`staff_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 16050 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '付款记录（客户为单次租赁支付的金额；一笔 rental 可能对应多笔 payment，如分期付款）';

DROP TABLE IF EXISTS `staff`;
CREATE TABLE `staff`  (
  `staff_id` tinyint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '员工主键ID',
  `first_name` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名',
  `last_name` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '姓',
  `address_id` smallint UNSIGNED NOT NULL COMMENT '员工地址ID',
  `picture` blob NULL COMMENT '员工照片（二进制）',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '员工邮箱',
  `store_id` tinyint UNSIGNED NOT NULL COMMENT '所属门店ID',
  `active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否在职',
  `username` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录用户名',
  `password` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL COMMENT '登录密码（加密）',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`staff_id`) USING BTREE,
  INDEX `idx_fk_store_id`(`store_id` ASC) USING BTREE,
  INDEX `idx_fk_address_id`(`address_id` ASC) USING BTREE,
  CONSTRAINT `fk_staff_address` FOREIGN KEY (`address_id`) REFERENCES `address` (`address_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_staff_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`store_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '员工（门店店员或经理，处理 rental 和 payment 的人）';

DROP TABLE IF EXISTS `store`;
CREATE TABLE `store`  (
  `store_id` tinyint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '门店主键ID',
  `manager_staff_id` tinyint UNSIGNED NOT NULL COMMENT '门店经理的员工ID（唯一）',
  `address_id` smallint UNSIGNED NOT NULL COMMENT '门店地址ID',
  `last_update` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本行最后更新时间',
  PRIMARY KEY (`store_id`) USING BTREE,
  UNIQUE INDEX `idx_unique_manager`(`manager_staff_id` ASC) USING BTREE,
  INDEX `idx_fk_address_id`(`address_id` ASC) USING BTREE,
  CONSTRAINT `fk_store_address` FOREIGN KEY (`address_id`) REFERENCES `address` (`address_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_store_staff` FOREIGN KEY (`manager_staff_id`) REFERENCES `staff` (`staff_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic COMMENT = '门店（租赁业务的物理门店，sakila 默认只有 1 到 2 家）';

-- =============================================================================

-- Part 2. AgentX 运行时表（5 张）

-- =============================================================================


DROP TABLE IF EXISTS `agentx_session`;
CREATE TABLE `agentx_session`  (
  `id` bigint NOT NULL COMMENT '主键ID',
  `conversation_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID',
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户ID',
  `question` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户提问',
  `answer` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'Agent回答',
  `think` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '模型思考内容',
  `timeline` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '时间线JSON（合并后的思考/正文/工具调用序列）',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agentx_session_conv`(`conversation_id` ASC) USING BTREE,
  INDEX `idx_agentx_session_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AgentX会话记录表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `agentx_file`;
CREATE TABLE `agentx_file`  (
  `id` bigint NOT NULL,
  `file_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `file_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `file_size` bigint NULL DEFAULT NULL,
  `minio_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `extracted_text` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `embed` tinyint NOT NULL DEFAULT 0,
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `session_id` bigint NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_file_id`(`file_id` ASC) USING BTREE,
  INDEX `idx_agentx_file_session`(`session_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `agentx_memory`;
CREATE TABLE `agentx_memory`  (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户ID',
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '记忆类型',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '记忆内容',
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '记忆描述',
  `metadata_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '元数据JSON',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agentx_memory_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_agentx_memory_user_type`(`user_id` ASC, `type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AgentX记忆存储表' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `agentx_skill`;
CREATE TABLE `agentx_skill`  (
  `id` bigint NOT NULL,
  `name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `skill_path` varchar(3000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `description` varchar(3000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
  `enabled` tinyint NOT NULL DEFAULT 1,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_agentx_skill_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `agentx_trace`;
CREATE TABLE `agentx_trace`  (
  `id` bigint NOT NULL COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '会话记录ID（agentx_session.id）',
  `conversation_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID',
  `round` int NOT NULL COMMENT '本轮ReAct循环轮次',
  `input_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '输入内容（prompt/消息序列JSON）',
  `output_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '输出内容（模型回答/工具结果）',
  `think` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '模型思考内容',
  `prompt_tokens` int NULL DEFAULT 0 COMMENT '提示token数',
  `completion_tokens` int NULL DEFAULT 0 COMMENT '补全token数',
  `duration_ms` bigint NULL DEFAULT 0 COMMENT '本轮耗时（毫秒）',
  `success` int NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败',
  `error_message` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '错误信息',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agentx_trace_session`(`session_id` ASC) USING BTREE,
  INDEX `idx_agentx_trace_conv`(`conversation_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AgentX追踪审计表' ROW_FORMAT = Dynamic;

-- =============================================================================

-- Part 3. 权限系统表（6 张）+ user_profile + 演示业务表

-- =============================================================================


DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`
(
    `id`         bigint       NOT NULL COMMENT '主键ID',
    `name`       varchar(100) NOT NULL COMMENT '部门名称',
    `parent_id`  bigint       NOT NULL DEFAULT 0 COMMENT '父部门ID（顶级为0）',
    `ancestors`  varchar(500) NOT NULL DEFAULT '0' COMMENT '祖先路径（逗号分隔ID链）',
    `sort`       int          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`     varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    `created_at` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX        `idx_sys_dept_parent_id`(`parent_id` ASC) USING BTREE
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表（树形）';

-- 角色表
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`
(
    `id`         bigint      NOT NULL COMMENT '主键ID',
    `code`       varchar(64) NOT NULL COMMENT '角色代码（如 admin）',
    `name`       varchar(64) NOT NULL COMMENT '角色名称（如 系统管理员）',
    `data_scope` varchar(32) NOT NULL DEFAULT 'DEPT' COMMENT '数据范围：ALL/DEPT_AND_SUB/DEPT/SELF',
    `sort`       int         NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`     varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    `created_at` timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_sys_role_code`(`code` ASC) USING BTREE
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 用户表（密码明文存储；部门走 sys_user_dept 多对多）
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`
(
    `id`         bigint       NOT NULL COMMENT '主键ID',
    `username`   varchar(64)  NOT NULL COMMENT '登录用户名',
    `password`   varchar(128) NOT NULL COMMENT '登录密码（明文存储）',
    `nickname`   varchar(64)           DEFAULT NULL COMMENT '昵称',
    `status`     varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    `created_at` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_sys_user_username`(`username` ASC) USING BTREE
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户-角色关联
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`
(
    `user_id`    bigint    NOT NULL COMMENT '用户ID',
    `role_id`    bigint    NOT NULL COMMENT '角色ID',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `role_id`) USING BTREE,
    INDEX        `idx_sys_user_role_role_id`(`role_id` ASC) USING BTREE
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- 用户-部门关联（多对多，支持跨部门兼职；数据权限 DEPT/DEPT_AND_SUB 走该表）
DROP TABLE IF EXISTS `sys_user_dept`;
CREATE TABLE `sys_user_dept`
(
    `user_id`    bigint    NOT NULL COMMENT '用户ID',
    `dept_id`    bigint    NOT NULL COMMENT '部门ID',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `dept_id`) USING BTREE,
    INDEX        `idx_sys_user_dept_dept_id`(`dept_id` ASC) USING BTREE
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-部门关联表（多对多）';

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE IF NOT EXISTS `user_profile` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL COMMENT '关联 sys_user.id（SELF 过滤列）',
    `real_name`    VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    `id_card`      CHAR(18)     NOT NULL COMMENT '身份证号',
    `home_address` VARCHAR(200)          COMMENT '家庭住址',
    `age`          INT                    COMMENT '年龄',
    `education`    VARCHAR(20)           COMMENT '学历',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户个人信息（演示 SELF 数据权限）';


DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`     bigint       NOT NULL COMMENT '订单ID',
    `product_name` varchar(255) NOT NULL COMMENT '商品名称',
    `quantity`     int          NOT NULL COMMENT '数量',
    `unit_price`   decimal(18,2) NOT NULL COMMENT '单价',
    `subtotal`     decimal(18,2) NOT NULL COMMENT '小计',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_od_order_id` (`order_id`) USING BTREE
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`      varchar(64)   NOT NULL COMMENT '订单编号',
    `user_id`       bigint        NOT NULL COMMENT '下单用户ID',
    `total_amount`  decimal(18,2) NOT NULL COMMENT '总金额',
    `status`        varchar(20)   NOT NULL COMMENT '订单状态：CREATED/PAID/CANCELLED',
    `dept_id`       bigint                 DEFAULT NULL COMMENT '归属部门（数据权限改写用）',
    `created_at`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_orders_order_no` (`order_no`) USING BTREE,
    INDEX `idx_orders_user_id` (`user_id`) USING BTREE,
    INDEX `idx_orders_dept_id` (`dept_id`) USING BTREE,
    INDEX `idx_orders_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

SET FOREIGN_KEY_CHECKS = 1;


-- =============================================================================

-- Part 4. 删除 Sakila 自带视图

-- =============================================================================


DROP VIEW IF EXISTS
    `actor_info`,
    `customer_list`,
    `film_list`,
    `nicer_but_slower_film_list`,
    `sales_by_film_category`,
    `sales_by_store`,
    `staff_list`;


-- =============================================================================

-- =============================================================================
-- Part 5. 业务改造：删除外键 & 移除 staff/store & 改 rental/payment
-- =============================================================================

-- 先删所有引用 staff / store 的外键（后面要 DROP 这两张表）
ALTER TABLE `customer`  DROP FOREIGN KEY `fk_customer_store`;
ALTER TABLE `inventory` DROP FOREIGN KEY `fk_inventory_store`;
ALTER TABLE `staff`     DROP FOREIGN KEY `fk_staff_store`;
ALTER TABLE `staff`     DROP FOREIGN KEY `fk_staff_address`;
ALTER TABLE `store`     DROP FOREIGN KEY `fk_store_staff`;
ALTER TABLE `store`     DROP FOREIGN KEY `fk_store_address`;
ALTER TABLE `payment`   DROP FOREIGN KEY `fk_payment_staff`;
ALTER TABLE `rental`    DROP FOREIGN KEY `fk_rental_staff`;

-- 删除 staff / store 表
DROP TABLE IF EXISTS `staff`;
DROP TABLE IF EXISTS `store`;

-- rental: staff_id -> user_id + dept_id
ALTER TABLE `rental`
    DROP INDEX `idx_fk_staff_id`,
    CHANGE COLUMN `staff_id` `user_id` bigint NOT NULL COMMENT '处理本次租赁的员工ID（sys_user.id）',
    ADD COLUMN `dept_id` bigint NOT NULL DEFAULT 1 COMMENT '业绩归属部门（数据权限改写用）',
    ADD INDEX `idx_rental_user_id` (`user_id`),
    ADD INDEX `idx_rental_dept_id` (`dept_id`);

-- payment: staff_id -> user_id + dept_id
ALTER TABLE `payment`
    DROP INDEX `idx_fk_staff_id`,
    CHANGE COLUMN `staff_id` `user_id` bigint NOT NULL COMMENT '收款员工ID（sys_user.id）',
    ADD COLUMN `dept_id` bigint NOT NULL DEFAULT 1 COMMENT '业绩归属部门（数据权限改写用）',
    ADD INDEX `idx_payment_user_id` (`user_id`),
    ADD INDEX `idx_payment_dept_id` (`dept_id`);

-- customer / inventory: 删除 store_id 列
ALTER TABLE `customer`  DROP COLUMN `store_id`;
ALTER TABLE `inventory` DROP COLUMN `store_id`;
-- Part 6. 种子数据：部门树 / 角色 / 用户 / 关联

-- =============================================================================


INSERT INTO `sys_dept` (`id`, `name`, `parent_id`, `ancestors`, `sort`, `status`)
VALUES
(100, '豆豆科技集团', 0, '0', 1, 'ACTIVE'),
(101, '总部', 100, '0,100', 1, 'ACTIVE'),
(201, '华东大区', 100, '0,100', 2, 'ACTIVE'),
(301, '华南大区', 100, '0,100', 3, 'ACTIVE'),
(110, '行政中心', 101, '0,100,101', 1, 'ACTIVE'),
(120, '技术中心', 101, '0,100,101', 2, 'ACTIVE'),
(111, '行政部', 110, '0,100,101,110', 1, 'ACTIVE'),
(112, '人事部', 110, '0,100,101,110', 2, 'ACTIVE'),
(113, '财务部', 110, '0,100,101,110', 3, 'ACTIVE'),
(121, '研发部', 120, '0,100,101,120', 1, 'ACTIVE'),
(122, '测试部', 120, '0,100,101,120', 2, 'ACTIVE'),
(123, '运维部', 120, '0,100,101,120', 3, 'ACTIVE'),
(210, '上海分公司', 201, '0,100,201', 1, 'ACTIVE'),
(220, '杭州分公司', 201, '0,100,201', 2, 'ACTIVE'),
(230, '南京分公司', 201, '0,100,201', 3, 'ACTIVE'),
(211, '上海销售部', 210, '0,100,201,210', 1, 'ACTIVE'),
(212, '上海售后部', 210, '0,100,201,210', 2, 'ACTIVE'),
(221, '杭州销售部', 220, '0,100,201,220', 1, 'ACTIVE'),
(222, '杭州市场部', 220, '0,100,201,220', 2, 'ACTIVE'),
(231, '南京销售部', 230, '0,100,201,230', 1, 'ACTIVE'),
(232, '南京客服部', 230, '0,100,201,230', 2, 'ACTIVE'),
(310, '广州分公司', 301, '0,100,301', 1, 'ACTIVE'),
(320, '深圳分公司', 301, '0,100,301', 2, 'ACTIVE'),
(311, '广州销售部', 310, '0,100,301,310', 1, 'ACTIVE'),
(312, '广州市场部', 310, '0,100,301,310', 2, 'ACTIVE'),
(313, '广州售后部', 310, '0,100,301,310', 3, 'ACTIVE'),
(321, '深圳研发部', 320, '0,100,301,320', 1, 'ACTIVE'),
(322, '深圳产品部', 320, '0,100,301,320', 2, 'ACTIVE')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `sys_role` (`id`, `code`, `name`, `data_scope`, `sort`, `status`)
VALUES
(1, 'admin',    '系统管理员', 'ALL',         1, 'ACTIVE'),
(2, 'manager',  '部门主管',   'DEPT_AND_SUB', 2, 'ACTIVE'),
(3, 'analyst',  '数据分析员', 'DEPT',         3, 'ACTIVE'),
(4, 'employee', '普通员工',   'SELF',         4, 'ACTIVE')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `data_scope` = VALUES(`data_scope`);

INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `status`)
VALUES
(1,  'admin',         '123456', '超级管理员', 'ACTIVE'),
(10, 'mgr_test',      '123456', '华东主管',   'ACTIVE'),
(11, 'analyst_test',  '123456', '上海分析员', 'ACTIVE'),
(20, 'sh_sales',      '123456', '上海销售员', 'ACTIVE'),
(21, 'hz_sales',      '123456', '杭州销售员', 'ACTIVE'),
(22, 'nj_sales',      '123456', '南京销售员', 'ACTIVE'),
(23, 'gz_sales',      '123456', '广州销售员', 'ACTIVE'),
(24, 'hz_analyst',    '123456', '杭州分析员', 'ACTIVE'),
(25, 'nj_analyst',    '123456', '南京分析员', 'ACTIVE'),
(26, 'gz_analyst',    '123456', '广州分析员', 'ACTIVE'),
(27, 'cross_analyst', '123456', '跨区分析员', 'ACTIVE')
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`), `nickname` = VALUES(`nickname`);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
VALUES
(1, 1), (10, 2), (11, 3),
(20, 4), (21, 4), (22, 4), (23, 4),
(24, 3), (25, 3), (26, 3), (27, 3)
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

INSERT INTO `sys_user_dept` (`user_id`, `dept_id`)
VALUES
(1,  100),
(10, 201),
(11, 211),
(20, 211), (21, 221), (22, 231), (23, 311),
(24, 221), (25, 231), (26, 311),
(27, 211), (27, 221)
ON DUPLICATE KEY UPDATE `dept_id` = VALUES(`dept_id`);


-- =============================================================================

-- Part 7. 填充 rental/payment 的 user_id 和 dept_id

-- =============================================================================


UPDATE `rental` SET `user_id` = `customer_id` MOD 4 + 20;
UPDATE `payment` SET `user_id` = `customer_id` MOD 4 + 20;

UPDATE `rental` r
    JOIN `sys_user_dept` ud ON r.`user_id` = ud.`user_id`
SET r.`dept_id` = ud.`dept_id`;

UPDATE `payment` p
    JOIN `sys_user_dept` ud ON p.`user_id` = ud.`user_id`
SET p.`dept_id` = ud.`dept_id`;


-- =============================================================================

-- Part 8. 演示数据：orders / order_detail

-- =============================================================================


INSERT INTO `orders` (`order_no`, `user_id`, `total_amount`, `status`, `dept_id`) VALUES
('PO202609230001', 20, 1299.00,  'PAID',     211),
('PO202609230002', 21, 3580.50,  'PAID',     221),
('PO202609230003', 22, 880.00,   'CREATED',  231),
('PO202609230004', 23, 5230.00,  'PAID',     311),
('PO202609230005', 20, 2100.00,  'CANCELLED',211),
('PO202609230006', 21, 4560.80,  'PAID',     221),
('PO202609230007', 23, 1560.00,  'CREATED',  311),
('PO202609230008', 22, 3200.00,  'PAID',     231),
('PO202609230009', 20, 980.00,   'PAID',     211),
('PO202609230010', 23, 7890.00,  'PAID',     311),
('PO202609230011', 21, 660.00,   'PAID',     221),
('PO202609230012', 22, 13400.00, 'PAID',     231),
('PO202609230013', 20, 3150.00,  'CREATED',  211),
('PO202609230014', 23, 820.00,   'PAID',     311),
('PO202609230015', 21, 2500.00,  'PAID',     221),
('PO202609230016', 22, 4150.00,  'CANCELLED',231),
('PO202609230017', 20, 9980.00,  'PAID',     211),
('PO202609230018', 23, 210.00,   'CREATED',  311),
('PO202609230019', 21, 5780.00,  'PAID',     221),
('PO202609230020', 22, 6200.00,  'PAID',     231);

INSERT INTO `order_detail` (`order_id`, `product_name`, `quantity`, `unit_price`, `subtotal`) VALUES
(1,  '办公椅 A',      2,  450.00,   900.00),
(1,  '会议室白板',    1,  399.00,   399.00),
(2,  '投影仪',        1,  3200.00,  3200.00),
(2,  '投影幕布',      1,  380.50,   380.50),
(3,  '无线鼠标',      4,  220.00,   880.00),
(4,  '笔记本电脑 X1', 2,  2650.00,  5300.00),
(5,  '文件柜',        1,  2100.00,  2100.00),
(6,  '服务器机架',    1,  4200.00,  4200.00),
(6,  '交换机 24口',   1,  360.80,   360.80),
(7,  '打印机耗材套装', 2,  780.00,   1560.00),
(8,  '会议桌 3m',     1,  3200.00,  3200.00),
(9,  '显示器支架',    2,  490.00,   980.00),
(10, '服务器 PowerEdge', 1, 7500.00,  7500.00),
(10, 'UPS 1000VA',    1,  390.00,   390.00),
(11, '无线键盘',      3,  220.00,   660.00),
(12, '存储 NAS 4盘位', 2,  5800.00,  11600.00),
(12, '硬盘 16TB',     2,  900.00,   1800.00),
(13, '一体机 AIO-27',  1,  3150.00,  3150.00),
(14, '鼠标垫 加大',   4,  205.00,   820.00),
(15, '空气净化器',    1,  2500.00,  2500.00),
(16, '打印机',        1,  4150.00,  4150.00),
(17, '网络机柜 42U',  1,  8500.00,  8500.00),
(17, 'PDU 插座 8口',  3,  493.33,   1480.00),
(18, '中性笔 一盒',   1,  210.00,   210.00),
(19, '显卡 RTX 4090', 1,  5780.00,  5780.00),
(20, 'MacBook Pro 16', 2,  3100.00,  6200.00);


SET FOREIGN_KEY_CHECKS = 1;
