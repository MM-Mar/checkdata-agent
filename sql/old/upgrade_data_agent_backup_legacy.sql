-- =============================================================================
-- WARNING: 此文件为项目重构前的旧版备份（原 dodo-agentx 时代）
-- 仅供参考对照，生产环境请勿使用。当前有效脚本请查看 sql/init.sql
-- =============================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =============================================================================
-- 权限系统建表（5 张）
--   用户与部门是多对多（sys_user_dept），支持跨部门兼职，数据权限走该关联表。
-- =============================================================================

-- 部门表（树形，用 ancestors 存祖先路径）
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

-- =============================================================================
-- 种子数据：部门树（27 个部门，4 级）
-- =============================================================================
-- 豆豆科技集团(100)
--   ├── 总部(101)
--   │   ├── 行政中心(110) → 行政部(111) / 人事部(112) / 财务部(113)
--   │   └── 技术中心(120) → 研发部(121) / 测试部(122) / 运维部(123)
--   ├── 华东大区(201)
--   │   ├── 上海分公司(210) → 销售部(211) / 售后部(212)
--   │   ├── 杭州分公司(220) → 销售部(221) / 市场部(222)
--   │   └── 南京分公司(230) → 销售部(231) / 客服部(232)
--   └── 华南大区(301)
--       ├── 广州分公司(310) → 销售部(311) / 市场部(312) / 售后部(313)
--       └── 深圳分公司(320) → 研发部(321) / 产品部(322)

INSERT INTO `sys_dept` (`id`, `name`, `parent_id`, `ancestors`, `sort`, `status`)
VALUES
-- 顶级
(100, '豆豆科技集团', 0, '0', 1, 'ACTIVE'),
-- 一级
(101, '总部', 100, '0,100', 1, 'ACTIVE'),
(201, '华东大区', 100, '0,100', 2, 'ACTIVE'),
(301, '华南大区', 100, '0,100', 3, 'ACTIVE'),
-- 总部子部门
(110, '行政中心', 101, '0,100,101', 1, 'ACTIVE'),
(120, '技术中心', 101, '0,100,101', 2, 'ACTIVE'),
(111, '行政部', 110, '0,100,101,110', 1, 'ACTIVE'),
(112, '人事部', 110, '0,100,101,110', 2, 'ACTIVE'),
(113, '财务部', 110, '0,100,101,110', 3, 'ACTIVE'),
(121, '研发部', 120, '0,100,101,120', 1, 'ACTIVE'),
(122, '测试部', 120, '0,100,101,120', 2, 'ACTIVE'),
(123, '运维部', 120, '0,100,101,120', 3, 'ACTIVE'),
-- 华东大区子部门
(210, '上海分公司', 201, '0,100,201', 1, 'ACTIVE'),
(220, '杭州分公司', 201, '0,100,201', 2, 'ACTIVE'),
(230, '南京分公司', 201, '0,100,201', 3, 'ACTIVE'),
(211, '上海销售部', 210, '0,100,201,210', 1, 'ACTIVE'),
(212, '上海售后部', 210, '0,100,201,210', 2, 'ACTIVE'),
(221, '杭州销售部', 220, '0,100,201,220', 1, 'ACTIVE'),
(222, '杭州市场部', 220, '0,100,201,220', 2, 'ACTIVE'),
(231, '南京销售部', 230, '0,100,201,230', 1, 'ACTIVE'),
(232, '南京客服部', 230, '0,100,201,230', 2, 'ACTIVE'),
-- 华南大区子部门
(310, '广州分公司', 301, '0,100,301', 1, 'ACTIVE'),
(320, '深圳分公司', 301, '0,100,301', 2, 'ACTIVE'),
(311, '广州销售部', 310, '0,100,301,310', 1, 'ACTIVE'),
(312, '广州市场部', 310, '0,100,301,310', 2, 'ACTIVE'),
(313, '广州售后部', 310, '0,100,301,310', 3, 'ACTIVE'),
(321, '深圳研发部', 320, '0,100,301,320', 1, 'ACTIVE'),
(322, '深圳产品部', 320, '0,100,301,320', 2, 'ACTIVE');

-- =============================================================================
-- 种子数据：角色（4 个，对应 4 级数据权限）
-- =============================================================================
INSERT INTO `sys_role` (`id`, `code`, `name`, `data_scope`, `sort`, `status`)
VALUES (1, 'admin', '系统管理员', 'ALL', 1, 'ACTIVE'),
       (2, 'manager', '部门主管', 'DEPT_AND_SUB', 2, 'ACTIVE'),
       (3, 'analyst', '数据分析员', 'DEPT', 3, 'ACTIVE'),
       (4, 'employee', '普通员工', 'SELF', 4, 'ACTIVE')
    ON DUPLICATE KEY UPDATE name = VALUES(name), data_scope = VALUES(data_scope);

-- =============================================================================
-- 种子数据：用户 + 角色 + 部门（统一明文密码 123456）
--   id  username       nickname    role  dept                     scope
--   1   admin          超级管理员   1     100 集团                  ALL
--   10  mgr_test       华东主管     2     201 华东大区              DEPT_AND_SUB
--   11  analyst_test   上海分析员   3     211 上海销售部            DEPT
--   20  sh_sales       上海销售员   4     211 上海销售部            SELF
--   21  hz_sales       杭州销售员   4     221 杭州销售部            SELF
--   22  nj_sales       南京销售员   4     231 南京销售部            SELF
--   23  gz_sales       广州销售员   4     311 广州销售部            SELF
--   24  hz_analyst     杭州分析员   3     221 杭州销售部            DEPT
--   25  nj_analyst     南京分析员   3     231 南京销售部            DEPT
--   26  gz_analyst     广州分析员   3     311 广州销售部            DEPT
--   27  cross_analyst  跨区分析员   3     211+221 上海/杭州销售部   DEPT（跨部门）
-- admin/mgr_test/analyst_test/三个 analyst 不挂业绩，user_id 不会出现在 rental/payment。
-- =============================================================================
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `status`)
VALUES (1,  'admin',         '123456', '超级管理员', 'ACTIVE'),
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
    ON DUPLICATE KEY UPDATE
       `password` = VALUES(`password`),
       `nickname` = VALUES(`nickname`);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
VALUES (1, 1), (10, 2), (11, 3),
       (20, 4), (21, 4), (22, 4), (23, 4),
       (24, 3), (25, 3), (26, 3), (27, 3)
    ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- cross_analyst 跨部门挂载：sys_user_dept 联合主键，每对一行
INSERT INTO `sys_user_dept` (`user_id`, `dept_id`)
VALUES (1,  100),
       (10, 201),
       (11, 211),
       (20, 211), (21, 221), (22, 231), (23, 311),
       (24, 221), (25, 231), (26, 311),
       (27, 211), (27, 221)
    ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id);


-- ===========================================================================
-- 清理 sakila 自带视图（演示库里只保留基础表 + 业务表，避免视图干扰）
-- ===========================================================================
DROP VIEW IF EXISTS
    `actor_info`,
    `customer_list`,
    `film_list`,
    `nicer_but_slower_film_list`,
    `sales_by_film_category`,
    `sales_by_store`,
    `staff_list`;

-- ===========================================================================
-- 1. 删除所有指向 staff / store 的外键（包括 staff↔store 自身互指、rental→staff）
--    用存储过程按「真实约束名」动态删，保证脚本可重复执行（幂等）。
-- ===========================================================================
DROP PROCEDURE IF EXISTS `drop_fk_if_exists`;
DELIMITER $$
CREATE PROCEDURE `drop_fk_if_exists`(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64)
)
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE fk_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT CONSTRAINT_NAME
        FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
          AND REFERENCED_TABLE_NAME IS NOT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO fk_name;
        IF done = 1 THEN LEAVE read_loop; END IF;
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` DROP FOREIGN KEY `', fk_name, '`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

-- payment → staff / customer → store / inventory → store
-- staff → store / store → staff / rental → staff（rental 的 FK 上一步漏删会导致后面 DROP INDEX 失败）
CALL drop_fk_if_exists('payment',   'staff_id');
CALL drop_fk_if_exists('customer',  'store_id');
CALL drop_fk_if_exists('inventory', 'store_id');
CALL drop_fk_if_exists('staff',     'store_id');
CALL drop_fk_if_exists('store',     'manager_staff_id');
CALL drop_fk_if_exists('rental',    'staff_id');

DROP PROCEDURE IF EXISTS `drop_fk_if_exists`;

-- ===========================================================================
-- 2. 删除 staff 表、store 表
-- ===========================================================================
DROP TABLE IF EXISTS `staff`;
DROP TABLE IF EXISTS `store`;

-- ===========================================================================
-- 3. rental：staff_id → user_id + 新增 dept_id（只加索引，不加外键）
-- ===========================================================================
-- dept_id 写入时从经手业务员的部门「快照」过来，业绩归属跟着业务员走。
ALTER TABLE `rental`
    DROP INDEX `idx_fk_staff_id`,
    CHANGE COLUMN `staff_id` `user_id` bigint NOT NULL COMMENT '处理本次租赁的员工ID（sys_user.id）',
    ADD COLUMN `dept_id` bigint NOT NULL DEFAULT 1 COMMENT '业绩归属部门（数据权限改写用）',
    ADD INDEX `idx_user_id` (`user_id`),
    ADD INDEX `idx_dept_id` (`dept_id`);

-- ===========================================================================
-- 4. payment：staff_id → user_id + 新增 dept_id（只加索引，不加外键）
-- ===========================================================================
ALTER TABLE `payment`
    DROP INDEX `idx_fk_staff_id`,
    CHANGE COLUMN `staff_id` `user_id` bigint NOT NULL COMMENT '收款员工ID（sys_user.id）',
    ADD COLUMN `dept_id` bigint NOT NULL DEFAULT 1 COMMENT '业绩归属部门（数据权限改写用）',
    ADD INDEX `idx_user_id` (`user_id`),
    ADD INDEX `idx_dept_id` (`dept_id`);

-- ===========================================================================
-- 5. customer / inventory：删除 store_id（主数据不挂部门）
-- ===========================================================================
ALTER TABLE `customer`  DROP COLUMN `store_id`;
ALTER TABLE `inventory` DROP COLUMN `store_id`;

-- ===========================================================================
-- 6. 填充 rental/payment：user_id 按 customer_id 哈希，dept_id 从业务员继承
-- ===========================================================================
UPDATE `rental`
SET `user_id` = `customer_id` MOD 4 + 20;
UPDATE `payment`
SET `user_id` = `customer_id` MOD 4 + 20;

UPDATE `rental` r
    JOIN `sys_user_dept` ud ON r.`user_id` = ud.`user_id`
SET r.`dept_id` = ud.`dept_id`;
UPDATE `payment` p
    JOIN `sys_user_dept` ud ON p.`user_id` = ud.`user_id`
SET p.`dept_id` = ud.`dept_id`;

-- ===========================================================================
-- 7. 用户个人信息表（演示 SELF 数据权限）
-- ===========================================================================
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

INSERT INTO `user_profile` (`user_id`, `real_name`, `id_card`, `home_address`, `age`, `education`) VALUES
(1,  '王大锤', '110101198001011001', '北京市东城区豆豆胡同1号',     45, '硕士'),
(10, '华宏达', '310101198501051002', '上海市浦东新区张江路100号',   40, '本科'),
(11, '申浩然', '310101199003031003', '上海市徐汇区漕河泾50号',      35, '硕士'),
(24, '陈志远', '330101199207071004', '杭州市西湖区文三路200号',     33, '本科'),
(25, '李梦琪', '320101199509091005', '南京市鼓楼区中山路300号',     30, '本科'),
(26, '黄俊杰', '440101199711111006', '广州市天河区珠江新城400号',   28, '本科'),
(27, '林思远', '310101199208081007', '上海市闵行区莘庄500号',       33, '硕士'),
(20, '张明',   '310101199405051008', '上海市闵行区莘庄500号',       31, '大专'),
(21, '周宇',   '330101199606061009', '杭州市滨江区江南大道600号',   29, '大专'),
(22, '吴凯',   '320101199707071010', '南京市玄武区中山门大街700号', 28, '大专'),
(23, '郑丽',   '440101199812121011', '广州市越秀区北京路800号',     27, '大专')
ON DUPLICATE KEY UPDATE
    `real_name` = VALUES(`real_name`),
    `id_card` = VALUES(`id_card`);


-- ===========================================================================
-- 验证查询（执行后人工确认）
-- ===========================================================================

-- ① 残留检查（应为空）
SELECT '== 残留检查（应为空） ==' AS '';
SELECT TABLE_NAME, COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME IN ('staff_id', 'store_id');

-- ② 用户角色与 scope 一览
SELECT '== 用户角色与数据范围 ==' AS '';
SELECT u.id, u.username, u.nickname, r.code AS role_code, r.name AS role_name, r.data_scope
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id = u.id
LEFT JOIN sys_role r ON r.id = ur.role_id
ORDER BY u.id;

-- ③ 用户部门挂载（cross_analyst 应有 2 行）
SELECT '== 用户部门挂载（cross_analyst 应有 2 行） ==' AS '';
SELECT u.username, d.id AS dept_id, d.name AS dept_name
FROM sys_user u
JOIN sys_user_dept ud ON ud.user_id = u.id
JOIN sys_dept d ON d.id = ud.dept_id
ORDER BY u.id, d.id;

-- ④ 业绩分布（admin/mgr_test/analyst_test/cross_analyst 应为 0）
SELECT '== 业绩分布 ==' AS '';
SELECT u.id, u.username, u.nickname,
       COALESCE(r.cnt, 0) AS rental_cnt,
       COALESCE(p.cnt, 0) AS payment_cnt,
       COALESCE(p.total, 0) AS total_amount
FROM sys_user u
LEFT JOIN (SELECT user_id, COUNT(*) AS cnt FROM rental GROUP BY user_id) r ON r.user_id = u.id
LEFT JOIN (SELECT user_id, COUNT(*) AS cnt, SUM(amount) AS total FROM payment GROUP BY user_id) p ON p.user_id = u.id
ORDER BY u.id;

-- ⑤ 部门业绩分布（rental/payment 的 dept_id 跟随业务员）
SELECT '== 部门业绩分布（rental/payment） ==' AS '';
SELECT 'rental' AS tbl, dept_id, COUNT(*) AS cnt FROM rental GROUP BY dept_id
UNION ALL SELECT 'payment', dept_id, COUNT(*) FROM payment GROUP BY dept_id
ORDER BY tbl, dept_id;

-- ⑥ user_profile 数据预览（admin 看全部 11 条；其他按 scope 过滤）
SELECT '== user_profile 全量预览 ==' AS '';
SELECT u.username, up.real_name, up.id_card, up.age, up.education, up.home_address
FROM user_profile up
JOIN sys_user u ON u.id = up.user_id
ORDER BY up.user_id;
