-- =====================================================================
--  环境与数据诊断脚本（只读，不修改任何数据）
--
--  用途：验证本机 MySQL 是否满足项目要求，以及演示数据是否包含
--        「过去 / 现在 / 未来」三类真实感数据。
--  执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 < db/check.sql
-- =====================================================================

USE `shang_pin_miao_sha`;

SELECT '=== 1. 数据库版本与环境 ===' AS `section`;
SELECT VERSION()                                        AS `mysql_version`,
       @@character_set_database                        AS `charset`,
       @@collation_database                            AS `collation`,
       @@sql_mode                                      AS `sql_mode`,
       @@innodb_buffer_pool_size                       AS `buffer_pool_bytes`,
       @@max_connections                               AS `max_connections`;

SELECT '=== 2. 表数据量 ===' AS `section`;
SELECT 't_product' AS `table_name`, COUNT(*) AS `row_cnt` FROM `t_product`
UNION ALL
SELECT 't_category', COUNT(*) FROM `t_category`
UNION ALL
SELECT 't_seckill_activity', COUNT(*) FROM `t_seckill_activity`
UNION ALL
SELECT 't_seckill_product', COUNT(*) FROM `t_seckill_product`
UNION ALL
SELECT 't_seckill_order', COUNT(*) FROM `t_seckill_order`
UNION ALL
SELECT 't_hot_key_record', COUNT(*) FROM `t_hot_key_record`
UNION ALL
SELECT 't_hot_key_task', COUNT(*) FROM `t_hot_key_task`;

SELECT '=== 3. 商品状态分布 ===' AS `section`;
SELECT `status`,
       CASE
           WHEN `status` = 0 THEN '已下架'
           WHEN `status` = 1 THEN '在售'
           WHEN `status` = 2 THEN '已售罄'
           WHEN `status` = 3 THEN '已结束'
           ELSE '未知'
           END   AS `status_name`,
       COUNT(*)  AS `cnt`,
       SUM(`deleted`) AS `deleted_cnt`
FROM `t_product`
GROUP BY `status`
ORDER BY `status`;

SELECT '=== 4. 活动时间窗口（过去 / 现在 / 未来） ===' AS `section`;
SELECT `id`,
       `activity_no`,
       `activity_name`,
       `status`,
       `start_time`,
       `end_time`,
       CASE
           WHEN NOW() < `start_time` THEN '未来（未开始）'
           WHEN NOW() > `end_time` THEN '过去（已过期）'
           ELSE '现在（进行中）'
           END AS `time_window`,
       TIMESTAMPDIFF(MINUTE, `start_time`, `end_time`) AS `duration_minutes`
FROM `t_seckill_activity`
ORDER BY `start_time`;

SELECT '=== 5. 过期数据统计（验证治理不再只看"当前热"） ===' AS `section`;
SELECT (SELECT COUNT(*) FROM `t_seckill_activity` WHERE `end_time` < NOW())                         AS `expired_activity_cnt`,
       (SELECT COUNT(*) FROM `t_seckill_activity` WHERE `start_time` > NOW())                      AS `upcoming_activity_cnt`,
       (SELECT COUNT(*) FROM `t_product` WHERE `deleted` = 1)                                      AS `deleted_product_cnt`,
       (SELECT COUNT(*) FROM `t_product` WHERE `status` = 3)                                       AS `ended_product_cnt`,
       (SELECT COUNT(*) FROM `t_product` WHERE `status` = 2)                                       AS `sold_out_product_cnt`,
       (SELECT COUNT(*) FROM `t_hot_key_record` WHERE `create_time` < DATE_SUB(NOW(), INTERVAL 5 DAY)) AS `stale_hot_key_cnt`;

SELECT '=== 6. 热商品治理总览（视图） ===' AS `section`;
SELECT `product_id`, `product_name`, `activity_status_name`, `is_hot`, `stock`, `seckill_price`
FROM `v_hot_product_overview`
WHERE `activity_no` IS NOT NULL
ORDER BY `product_id`;

SELECT '=== 7. 热 Key 观测记录 ===' AS `section`;
SELECT `id`, `redis_key`, `biz_type`, `qps`, `level`, `handle_action`, `create_time`
FROM `t_hot_key_record`
ORDER BY `create_time` DESC;
