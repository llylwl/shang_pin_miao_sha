-- =====================================================================
--  shang_pin_miao_sha 建库建表脚本（可重复执行）
--
--  用法一（推荐，手工初始化）：
--    mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 < db/init.sql
--  用法二（Spring 自动建表）：本项目默认 spring.sql.init.mode=always，
--    启动时会执行本文件与 data.sql，二者均幂等。
--
--  注意：本脚本不会 DROP DATABASE，只做 IF NOT EXISTS / DROP TABLE IF EXISTS，
--        不会误删同库中的其他业务表。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `shang_pin_miao_sha`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `shang_pin_miao_sha`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 商品表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product`
(
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '商品主键',
    `product_no`    VARCHAR(32)    NOT NULL COMMENT '商品编号（业务唯一键）',
    `product_name`  VARCHAR(128)   NOT NULL COMMENT '商品名称',
    `category_id`   BIGINT         NOT NULL COMMENT '类目ID',
    `title`         VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '副标题/营销文案',
    `main_image`    VARCHAR(512)   NOT NULL DEFAULT '' COMMENT '主图地址',
    `price`         DECIMAL(10, 2) NOT NULL COMMENT '当前售价（元）',
    `origin_price`  DECIMAL(10, 2) NOT NULL COMMENT '原价（元）',
    `stock`         INT            NOT NULL DEFAULT 0 COMMENT '可售库存',
    `sold_count`    INT            NOT NULL DEFAULT 0 COMMENT '已售数量',
    `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：0-已下架 1-在售 2-已售罄 3-已结束',
    `is_hot`        TINYINT        NOT NULL DEFAULT 0 COMMENT '是否秒杀热点商品：0-否 1-是',
    `version`       INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_no` (`product_no`),
    KEY `idx_category_status` (`category_id`, `status`),
    KEY `idx_is_hot` (`is_hot`, `status`),
    KEY `idx_update_time` (`update_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品表';

-- ---------------------------------------------------------------------
-- 2. 商品类目表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_category`;
CREATE TABLE `t_category`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '类目主键',
    `category_no` VARCHAR(32) NOT NULL COMMENT '类目编号',
    `name`        VARCHAR(64) NOT NULL COMMENT '类目名称',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父类目ID，0 表示一级类目',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_no` (`category_no`),
    KEY `idx_parent_sort` (`parent_id`, `sort`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品类目表';

-- ---------------------------------------------------------------------
-- 3. 秒杀活动表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_seckill_activity`;
CREATE TABLE `t_seckill_activity`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动主键',
    `activity_no`   VARCHAR(32)  NOT NULL COMMENT '活动编号',
    `activity_name` VARCHAR(128) NOT NULL COMMENT '活动名称',
    `start_time`    DATETIME     NOT NULL COMMENT '活动开始时间',
    `end_time`      DATETIME     NOT NULL COMMENT '活动结束时间',
    `total_stock`   INT          NOT NULL COMMENT '活动总库存',
    `remain_stock`  INT          NOT NULL COMMENT '活动剩余库存',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-未开始 1-进行中 2-已结束 3-已取消',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_no` (`activity_no`),
    KEY `idx_time_window` (`start_time`, `end_time`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀活动表';

-- ---------------------------------------------------------------------
-- 4. 秒杀商品关联表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_seckill_product`;
CREATE TABLE `t_seckill_product`
(
    `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id`    BIGINT         NOT NULL COMMENT '活动ID',
    `product_id`     BIGINT         NOT NULL COMMENT '商品ID',
    `seckill_price`  DECIMAL(10, 2) NOT NULL COMMENT '秒杀价（元）',
    `seckill_stock`  INT            NOT NULL COMMENT '秒杀库存',
    `limit_per_user` INT            NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_product` (`activity_id`, `product_id`),
    KEY `idx_product` (`product_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀商品关联表';

-- ---------------------------------------------------------------------
-- 5. 秒杀订单表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_seckill_order`;
CREATE TABLE `t_seckill_order`
(
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单主键',
    `order_no`    VARCHAR(40)    NOT NULL COMMENT '订单号',
    `activity_id` BIGINT         NOT NULL COMMENT '活动ID',
    `product_id`  BIGINT         NOT NULL COMMENT '商品ID',
    `user_id`     BIGINT         NOT NULL COMMENT '用户ID',
    `quantity`    INT            NOT NULL DEFAULT 1 COMMENT '购买数量',
    `amount`      DECIMAL(10, 2) NOT NULL COMMENT '订单金额（元）',
    `status`      TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：0-待支付 1-已支付 2-已取消 3-已完成',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `pay_time`    DATETIME       DEFAULT NULL COMMENT '支付时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`),
    KEY `idx_product_time` (`product_id`, `create_time`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀订单表';

-- ---------------------------------------------------------------------
-- 6. 热 Key 观测记录表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_hot_key_record`;
CREATE TABLE `t_hot_key_record`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `redis_key`     VARCHAR(255) NOT NULL COMMENT 'Redis 键',
    `biz_type`      VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '业务类型',
    `qps`           INT          NOT NULL DEFAULT 0 COMMENT '观测到的 QPS',
    `threshold`     INT          NOT NULL DEFAULT 0 COMMENT '触发阈值',
    `level`         VARCHAR(16)  NOT NULL DEFAULT 'YELLOW' COMMENT '告警级别：YELLOW-关注 RED-告警',
    `handle_action` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '处置动作',
    `instance_id`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '上报实例',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_key_time` (`redis_key`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '热 Key 观测记录表';

-- ---------------------------------------------------------------------
-- 7. 热 Key 治理任务表（记录打散/预热/清理的执行结果，便于复盘）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_hot_key_task`;
CREATE TABLE `t_hot_key_task`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `redis_key`     VARCHAR(255) NOT NULL COMMENT '被治理的 Redis 键',
    `action_type`   VARCHAR(32)  NOT NULL COMMENT '动作：SPLIT-打散 WARM_UP-预热 LOCAL_CACHE-本地缓存 EVICT-淘汰',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-待执行 1-成功 2-失败',
    `detail`        VARCHAR(512) NOT NULL DEFAULT '' COMMENT '执行详情/失败原因',
    `operator`      VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '操作者',
    `cost_ms`       INT          NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_key_action` (`redis_key`, `action_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '热 Key 治理任务表';

-- ---------------------------------------------------------------------
-- 8. 视图：热商品治理总览
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS `v_hot_product_overview`;
CREATE VIEW `v_hot_product_overview` AS
SELECT p.`id`              AS `product_id`,
       p.`product_no`      AS `product_no`,
       p.`product_name`    AS `product_name`,
       p.`status`          AS `product_status`,
       p.`stock`           AS `stock`,
       p.`is_hot`          AS `is_hot`,
       a.`activity_no`     AS `activity_no`,
       a.`status`          AS `activity_status`,
       a.`start_time`      AS `start_time`,
       a.`end_time`        AS `end_time`,
       CASE
           WHEN a.`id` IS NULL THEN '非活动商品'
           WHEN a.`status` = 1 THEN '活动中'
           WHEN a.`status` = 0 THEN '待开始'
           WHEN a.`status` = 2 THEN '已结束'
           ELSE '已取消'
           END             AS `activity_status_name`,
       sp.`seckill_price`  AS `seckill_price`
FROM `t_product` p
         LEFT JOIN `t_seckill_product` sp ON sp.`product_id` = p.`id`
         LEFT JOIN `t_seckill_activity` a ON a.`id` = sp.`activity_id`
WHERE p.`deleted` = 0;
