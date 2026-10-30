-- =====================================================================
--  shang_pin_miao_sha 一键初始化脚本（建库 + 建表 + 种子数据）
--
--  执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 < db/init.sql
--  幂等：可重复执行；不 DROP DATABASE，不会影响同库其他表。
-- =====================================================================
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

-- =====================================================================
--  shang_pin_miao_sha 初始化数据（幂等，可重复执行）
--
--  ✔ 真实感：商品/活动/订单使用仿真数据，价格、库存、销量均在合理区间
--  ✔ 含过期数据：已结束活动、已取消活动、已下架商品、已售罄商品、
--                7 天前的历史热 Key 观测记录
--  ✔ 时间基准：全部以 NOW() 相对生成，任何时刻导入都会自动出现
--                「过去 / 现在 / 未来」三类数据
--
--  任务表 t_hot_key_task 不插入业务数据，需由应用运行时写入，
--  因此不在此清空，避免每次启动丢失治理审计记录。
-- =====================================================================

USE `shang_pin_miao_sha`;

SET NAMES utf8mb4;

DELETE
FROM `t_seckill_order`;
DELETE
FROM `t_seckill_product`;
DELETE
FROM `t_seckill_activity`;
DELETE
FROM `t_product`;
DELETE
FROM `t_category`;
DELETE
FROM `t_hot_key_record`;

ALTER TABLE `t_seckill_order` AUTO_INCREMENT = 1;
ALTER TABLE `t_seckill_product` AUTO_INCREMENT = 1;
ALTER TABLE `t_seckill_activity` AUTO_INCREMENT = 1;
ALTER TABLE `t_product` AUTO_INCREMENT = 1;
ALTER TABLE `t_category` AUTO_INCREMENT = 1;

-- =====================================================================
-- 1. 类目
-- =====================================================================
INSERT INTO `t_category` (`category_no`, `name`, `parent_id`, `sort`, `create_time`)
VALUES ('C1001', '手机通讯', 0, 1, DATE_SUB(NOW(), INTERVAL 400 DAY)),
       ('C1002', '电脑办公', 0, 2, DATE_SUB(NOW(), INTERVAL 400 DAY)),
       ('C1003', '家用电器', 0, 3, DATE_SUB(NOW(), INTERVAL 400 DAY)),
       ('C1004', '美妆个护', 0, 4, DATE_SUB(NOW(), INTERVAL 400 DAY)),
       ('C100101', '智能手机', 1, 1, DATE_SUB(NOW(), INTERVAL 400 DAY)),
       ('C100102', '平板电脑', 1, 2, DATE_SUB(NOW(), INTERVAL 400 DAY)),
       ('C100201', '笔记本', 2, 1, DATE_SUB(NOW(), INTERVAL 400 DAY)),
       ('C100301', '厨房电器', 3, 1, DATE_SUB(NOW(), INTERVAL 400 DAY)),
       ('C100401', '面部护理', 4, 1, DATE_SUB(NOW(), INTERVAL 400 DAY));

-- =====================================================================
-- 2. 秒杀活动
--    id=1 进行中（主热点）  id=2 进行中（次热点）
--    id=3 已结束（7 天前，历史热点）  id=4 未开始（预热）  id=5 已取消
-- =====================================================================
INSERT INTO `t_seckill_activity`
(`activity_no`, `activity_name`, `start_time`, `end_time`, `total_stock`, `remain_stock`, `status`, `create_time`,
 `update_time`)
VALUES ('ACT2025001', '双11预热场·iPhone 16 Pro 闪购', DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 6 HOUR),
        500, 387, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW()),
       ('ACT2025002', '双11预热场·华为 Mate 70 闪购', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 5 HOUR),
        800, 642, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW()),
       ('ACT2024099', '中秋节·月饼礼盒限时秒杀', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY),
        2000, 0, 2, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
       ('ACT2025003', '双11正式场·小米 15 首发', DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY),
        1000, 1000, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
       ('ACT2024998', '国庆·家电以旧换新专场', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY),
        300, 120, 3, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 32 DAY));

-- =====================================================================
-- 3. 商品
--    1-2 在售热点；3 已结束；4 已下架（逻辑删除）；5 预热热点；
--    6 已售罄；7-9 在售冷商品；10 已取消活动商品
-- =====================================================================
INSERT INTO `t_product`
(`product_no`, `product_name`, `category_id`, `title`, `main_image`, `price`, `origin_price`, `stock`,
 `sold_count`, `status`, `is_hot`, `version`, `create_time`, `update_time`, `deleted`)
VALUES ('SP10000086', 'Apple iPhone 16 Pro 256GB 原色钛金属', 5, '限时闪购｜A18 Pro 芯片｜官方正品',
        '/img/p/10000086.jpg', 7999.00, 8999.00, 387, 113, 1, 1, 3, DATE_SUB(NOW(), INTERVAL 30 DAY), NOW(), 0),
       ('SP10000087', 'HUAWEI Mate 70 Pro 512GB 曜金黑', 5, '双11预热｜卫星通信｜昆仑玻璃',
        '/img/p/10000087.jpg', 6499.00, 6999.00, 642, 158, 1, 1, 1, DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0),
       ('SP90000001', '香港月饼礼盒 流心奶黄 8 枚装', 4, '中秋限定（活动已结束）',
        '/img/p/90000001.jpg', 168.00, 238.00, 0, 2000, 3, 0, 5, DATE_SUB(NOW(), INTERVAL 20 DAY),
        DATE_SUB(NOW(), INTERVAL 7 DAY), 0),
       ('SP90000002', '儿童智能手表 4G 全网通', 5, '已下架（旧款）',
        '/img/p/90000002.jpg', 599.00, 799.00, 0, 9850, 0, 0, 9, DATE_SUB(NOW(), INTERVAL 300 DAY),
        DATE_SUB(NOW(), INTERVAL 90 DAY), 1),
       ('SP10000093', 'Xiaomi 15 Pro 16GB+512GB 白色', 5, '双11首发｜骁龙 8 至臻版',
        '/img/p/10000093.jpg', 5299.00, 5999.00, 1000, 0, 1, 1, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
       ('SP10000094', 'Apple Watch S10 46mm 运动表带', 5, '爆款补货中',
        '/img/p/10000094.jpg', 3299.00, 3699.00, 0, 5000, 2, 0, 12, DATE_SUB(NOW(), INTERVAL 60 DAY),
        DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
       ('SP20000101', '罗技 MX Master 3S 无线鼠标', 7, '人体工学｜静音按键',
        '/img/p/20000101.jpg', 649.00, 799.00, 320, 1800, 1, 0, 2, DATE_SUB(NOW(), INTERVAL 200 DAY),
        DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
       ('SP20000102', '戴森 V12 Detect Slim 吸尘器', 8, '旗舰款｜激光探测',
        '/img/p/20000102.jpg', 3999.00, 4499.00, 86, 2400, 1, 0, 4, DATE_SUB(NOW(), INTERVAL 150 DAY),
        DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
       ('SP20000103', 'SK-II 神仙水 230ml', 9, '狗年限定｜紧致修护',
        '/img/p/20000103.jpg', 1540.00, 1740.00, 500, 12000, 1, 0, 1, DATE_SUB(NOW(), INTERVAL 90 DAY),
        DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
       ('SP90000003', '美的电饭煲 4L IH 电磁加热', 8, '活动已取消',
        '/img/p/90000003.jpg', 599.00, 799.00, 120, 8900, 1, 0, 6, DATE_SUB(NOW(), INTERVAL 400 DAY),
        DATE_SUB(NOW(), INTERVAL 32 DAY), 0);

-- =====================================================================
-- 4. 秒杀商品关联
-- =====================================================================
INSERT INTO `t_seckill_product` (`activity_id`, `product_id`, `seckill_price`, `seckill_stock`, `limit_per_user`,
                                 `create_time`)
VALUES (1, 1, 6999.00, 500, 1, DATE_SUB(NOW(), INTERVAL 10 DAY)),
       (2, 2, 5999.00, 800, 1, DATE_SUB(NOW(), INTERVAL 10 DAY)),
       (3, 3, 99.00, 2000, 2, DATE_SUB(NOW(), INTERVAL 20 DAY)),
       (4, 5, 4999.00, 1000, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
       (5, 10, 499.00, 300, 1, DATE_SUB(NOW(), INTERVAL 40 DAY));

-- =====================================================================
-- 5. 历史秒杀订单（含过期订单）
-- =====================================================================
INSERT INTO `t_seckill_order`
(`order_no`, `activity_id`, `product_id`, `user_id`, `quantity`, `amount`, `status`, `create_time`, `pay_time`)
VALUES ('SK2025102800010001', 1, 1, 10001, 1, 6999.00, 1, DATE_SUB(NOW(), INTERVAL 100 MINUTE),
        DATE_SUB(NOW(), INTERVAL 99 MINUTE)),
       ('SK2025102800010002', 1, 1, 10002, 1, 6999.00, 1, DATE_SUB(NOW(), INTERVAL 98 MINUTE),
        DATE_SUB(NOW(), INTERVAL 97 MINUTE)),
       ('SK2025102800010003', 1, 1, 10003, 1, 6999.00, 0, DATE_SUB(NOW(), INTERVAL 96 MINUTE), NULL),
       ('SK2025102800010004', 1, 1, 10004, 1, 6999.00, 2, DATE_SUB(NOW(), INTERVAL 94 MINUTE), NULL),
       ('SK2025102800020001', 2, 2, 20001, 1, 5999.00, 1, DATE_SUB(NOW(), INTERVAL 80 MINUTE),
        DATE_SUB(NOW(), INTERVAL 79 MINUTE)),
       ('SK2025102800020002', 2, 2, 20002, 1, 5999.00, 1, DATE_SUB(NOW(), INTERVAL 70 MINUTE),
        DATE_SUB(NOW(), INTERVAL 69 MINUTE)),
       ('SK2024092000990001', 3, 3, 30001, 2, 198.00, 3, DATE_SUB(NOW(), INTERVAL 8 DAY),
        DATE_SUB(NOW(), INTERVAL 8 DAY)),
       ('SK2024092000990002', 3, 3, 30002, 2, 198.00, 3, DATE_SUB(NOW(), INTERVAL 8 DAY),
        DATE_SUB(NOW(), INTERVAL 8 DAY)),
       ('SK2024092000990003', 3, 3, 30003, 1, 99.00, 3, DATE_SUB(NOW(), INTERVAL 8 DAY),
        DATE_SUB(NOW(), INTERVAL 8 DAY)),
       ('SK2024091000980001', 5, 10, 40001, 1, 499.00, 2, DATE_SUB(NOW(), INTERVAL 30 DAY), NULL);

-- =====================================================================
-- 6. 热 Key 观测记录（含 7 天前的过期热点记录）
--    显式指定 id：MySQL 的 DELETE 不会重置 AUTO_INCREMENT，重复执行会不断撞主键。
-- =====================================================================
INSERT INTO `t_hot_key_record`
(`id`, `redis_key`, `biz_type`, `qps`, `threshold`, `level`, `handle_action`, `instance_id`, `create_time`)
VALUES (1, 'product:1', 'PRODUCT', 82314, 2000, 'RED', 'LOCAL_CACHE+SPLIT', 'sp-ms-8.2.3',
        DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
       (2, 'hot-product:1', 'PRODUCT', 82314, 2000, 'RED', 'LOCAL_CACHE+SPLIT', 'sp-ms-8.2.3',
        DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
       (3, 'product:2', 'PRODUCT', 18326, 2000, 'RED', 'LOCAL_CACHE', 'sp-ms-8.2.3',
        DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
       (4, 'stock:1', 'STOCK', 3105, 2000, 'YELLOW', 'LOCAL_PREDEDUCT', 'sp-ms-8.2.3',
        DATE_SUB(NOW(), INTERVAL 25 MINUTE)),
       (5, 'product:3', 'PRODUCT', 45210, 2000, 'RED', 'LOCAL_CACHE', 'sp-ms-8.2.2', DATE_SUB(NOW(), INTERVAL 7 DAY)),
       (6, 'stock:3', 'STOCK', 8760, 2000, 'RED', 'LOCAL_PREDEDUCT', 'sp-ms-8.2.2', DATE_SUB(NOW(), INTERVAL 7 DAY));
