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
