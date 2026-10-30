# shang_pin_miao_sha —— 商品秒杀 · Redis 热 Key 治理

> 对应方案：**客户端 / 代理端 / Redis 服务端 三层监控热 Key + 打散 + 二级缓存 + 强一致数据本地预扣减**
> 技术栈：Java 21 · Spring Boot 3.2.5 · MySQL 8.0 · Redis 6+ · MyBatis · Caffeine · Lettuce · Micrometer

---

## 1. 它解决什么问题

秒杀场景下单品类 Key（如 `hot-product:10086`）QPS 可达数万，会导致：
单分片 CPU 打满 → 拖垮同分片其他 Key → 缓存击穿 → 数据库被压垮。

本项目给出**可运行**的完整闭环：

| 能力 | 落地位置 | 说明 |
|---|---|---|
| 客户端热 Key 监控 | `SlidingWindowCounter` + `HotKeyCollector` + `@RedisHotKeyTrack` 切面 | Count-Min Sketch 滑动窗口，内存恒定，开销约 4 次哈希 |
| 代理端 / 服务端监控 | `README` 第 6 节 + `HotKeyReporter` | `redis-cli --hotkeys`(LFU)、`OBJECT FREQ`、`SLOWLOG`、`INFO commandstats` |
| 热 Key 打散 | `HotKeySplitter` + `ProductQueryService` | 热点商品拆 8 份副本 `hot-product:{id}:{0..7}`，读请求随机分摊 |
| 二级缓存 | `LocalProductCache`（Caffeine） | 仅热 Key 进本地缓存，TTL 兜底 + Pub/Sub 主动失效 |
| 强一致数据降压力 | `LocalStockManager` + `StockCacheService`(Lua) | 库存**禁止打散**，改为「本地配额预扣减 + Redis Lua 原子兜底」 |
| 降级 | `ProductQueryService#degradeToLocal` | Redis 宕机时读本地内存，仍可对外服务 |
| 可观测 | `/actuator/health`、`/api/hotkey/*`、Micrometer `hotkey.qps` | 热 Key TopN、告警级别与处置建议 |

---

## 2. 目录结构

```
shang_pin_miao_sha
├── pom.xml
├── scripts
│   ├── verify.mjs            # 端到端接口验证（Node 标准库，无依赖）
│   └── seckill-stress.mjs    # 并发抢购正确性验证（防超卖/重复单）
└── src
    ├── main
    │   ├── java/com/hzp/miaosha
    │   │   ├── common
    │   │   │   ├── algorithm    # CountMinSketch、SlidingWindowCounter（热 Key 监控核心算法）
    │   │   │   ├── annotation   # @RedisHotKeyTrack
    │   │   │   ├── aspect       # RedisHotKeyTrackAspect（客户端打点切面）
    │   │   │   ├── constant / enums / result / exception / util
    │   │   ├── config           # Redis / MyBatis / 配置属性 / Pub-Sub
    │   │   ├── controller       # 商品、秒杀、热 Key、演示数据
    │   │   ├── domain           # po / vo / dto
    │   │   ├── mapper           # MyBatis 接口
    │   │   └── service
    │   │       ├── cache        # 本地二级缓存、打散、商品查询、库存缓存、失效广播
    │   │       ├── hotkey       # 采集、上报、预热、健康检查、压测
    │   │       └── seckill      # 本地配额、秒杀核心、优雅下线
    │   └── resources
    │       ├── application.yml
    │       ├── db
    │       │   ├── schema.sql   # 建库建表（幂等，Spring 启动自动执行）
    │       │   ├── data.sql     # 仿真种子数据（幂等，含过期数据）
    │       │   ├── init.sql     # 一键初始化（schema + data）
    │       │   └── check.sql    # 环境与数据诊断（只读）
    │       └── mapper/*.xml
    └── test
        └── java/com/hzp/miaosha
            ├── DatabaseIntegrationTest     # 连真实 MySQL 的集成测试
            ├── common/algorithm/*Test      # CMS / 滑动窗口（含并发安全）
            ├── service/cache/*Test         # 本地缓存、打散、查询链路、降级
            ├── service/seckill/*Test       # 本地库存并发扣减（防超卖）
            └── support/*                   # 内存版 Redis 替身、测试数据工厂
```

---

## 3. 快速开始

### 3.1 前置条件

| 组件 | 版本 | 本机实测 |
|---|---|---|
| JDK | 17+（推荐 21） | ✅ 21.0.10 |
| Maven | 3.8+ | ✅ 3.9.14 |
| MySQL | 8.0+ | ✅ 8.0.46（数据库名 `shang_pin_miao_sha`） |
| Redis | 5.0+（可选，缺失时自动降级） | ⚠️ 本机未安装，用便携版 16379 验证 |

### 3.2 构建

```bash
# 编译 + 单元测试（不依赖数据库/Redis）
mvn clean package

# 额外跑数据库集成测试（需本机 MySQL 可用）
# Windows PowerShell:
$env:RUN_DB_TEST="true"; mvn clean package
# Linux / macOS:
RUN_DB_TEST=true mvn clean package
```

### 3.3 启动

```bash
# 默认配置：MySQL root/root，Redis 127.0.0.1:6379
java -jar target/shang_pin_miao_sha.jar

# 自定义连接信息
java -jar target/shang_pin_miao_sha.jar \
  --spring.datasource.username=root \
  --spring.datasource.password=root \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3306/shang_pin_miao_sha?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.data.redis.port=6379
```

> Spring 启动时会自动执行 `db/schema.sql` + `db/data.sql`（`spring.sql.init.mode=always`，两者均幂等）。
> 生产环境由 DBA 初始化时，把 `SQL_INIT_MODE=never`。

### 3.4 手工初始化数据库（可选）

```bash
mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 < src/main/resources/db/init.sql
mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 < src/main/resources/db/check.sql
```

---

## 4. 接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/product/{id}` | 商品详情（热 Key 治理读链路：本地缓存 → 副本 → DB → 降级） |
| GET | `/api/product/batch?productIds=1,2` | 批量查询 |
| POST | `/api/product/{id}/warm-up` | 预热商品（主 Key + 8 副本 + 本地缓存） |
| POST | `/api/product/{id}/cache/evict` | 清理缓存并广播失效 |
| POST | `/api/seckill/order` | 秒杀下单 `{"activityId":1,"userId":10086,"quantity":1}` |
| GET | `/api/seckill/activity/{id}/stock` | 库存视图（DB / Redis / 本地配额） |
| POST | `/api/seckill/activity/{id}/refill` | 从 Redis 领取一批本地配额 |
| POST | `/api/seckill/stock/release` | 归还本地配额 |
| GET | `/api/hotkey/top?topN=10` | 热 Key TopN（含级别与处置建议） |
| GET | `/api/hotkey/overview` | 监控概览（窗口、阈值、最近告警） |
| POST | `/api/hotkey/benchmark/{id}?count=30000` | 制造热 Key（压测） |
| GET | `/api/demo/products` | 商品全量（含已下架/已结束） |
| GET | `/api/demo/activities/lifecycle` | 活动按**过去 / 现在 / 未来**分组 |
| GET | `/api/demo/expired-stat` | 过期数据统计 |
| GET | `/actuator/health` | 健康检查（Redis 不可用时状态 `OUT_OF_SERVICE`，HTTP 200） |

---

## 5. 实测数据（本机运行结果）

环境：JDK 21.0.10 / MySQL 8.0.46 / Redis 8.10.3（便携版，端口 16379）

### 5.1 热 Key 监控与二级缓存效果

| 轮次 | 请求数 | Redis 实际访问 | 本地命中 | TPS |
|---|---|---|---|---|
| 第 1 轮（冷启动） | 30,000 | 4,999（16.7%） | 25,001 | 22,868 |
| 第 2 轮（已判定为热 Key） | 30,000 | **0** | 30,000 | 213,096 |

热 Key 判定结果：

```json
{"key":"product:1","count":91657,"qps":9165.7,"level":"RED",
 "action":"商品详情：本地二级缓存 + 打散副本（replica=8）","levelDesc":"告警"}
```

Redis 中的打散副本（8 份全部就位）：

```
hot-product:1:0 ... hot-product:1:7     # 读请求随机分摊
product:1                               # 主 Key
stock:1                                 # 库存 key（唯一，禁止打散）
```

### 5.2 并发抢购正确性

```
请求数=100 并发=50
返回码分布：{"200":100}
成功订单=100（去重后 100，无重复单）
DB 扣减=100；Redis 剩余=87 + 本地配额=99 + 已售 100 = 286 == 抢购前 386-100 ✅ 账目自洽
```

### 5.3 端到端验证

```bash
node scripts/verify.mjs http://127.0.0.1:8080
# → 通过 11 项，失败 0 项
```

---

## 6. 热 Key 的三层监控怎么用

### 6.1 客户端（本项目已实现）

`HotKeyCollector` 用 **Count-Min Sketch + 10 秒滑动窗口**统计每个 Key 的访问频次：

- 内存恒定：`windowSeconds × width × depth × 8B`，与 Key 总数无关；
- 判定：QPS ≥ `hot-qps-threshold` 即视为热 Key，自动启用本地缓存 + 打散副本；
- 上报：`HotKeyReporter` 每 5 秒输出 TopN、Micrometer 指标 `hotkey.qps`、告警处置建议；
- 通用打点：方法加 `@RedisHotKeyTrack(keyPrefix = "product:")` 即可自动统计。

### 6.2 代理端（Twemproxy / Codis / Envoy）

在转发路径上聚合统计后**批量上报**，切忌对每个 `GET` 做 `INCR`（会把监控本身变成新热点）。

### 6.3 Redis 服务端

```bash
# ⚠️ MONITOR 会打印所有命令，8 万 QPS 时本身就会打满网络，只适合秒级排障
redis-cli MONITOR | head -1000

# 推荐：LFU 热 Key 统计（Redis 4.0+）
redis-cli CONFIG SET maxmemory-policy allkeys-lfu
redis-cli CONFIG SET lfu-log-factor 10
redis-cli CONFIG SET lfu-decay-time 1
redis-cli --hotkeys
# 输出：hot key found with counter: 19283747  key: product:1

# 分片级排查
redis-cli INFO commandstats      # 某分片 GET 占比异常
redis-cli SLOWLOG GET 20         # 慢请求
redis-cli --bigkeys              # 大 Key（与热 Key 是两回事）
```

---

## 7. 配置说明（`application.yml` → `miao-sha.*`）

| 配置项 | 默认 | 说明 |
|---|---|---|
| `hot-key.window-seconds` | 10 | 统计窗口秒数 |
| `hot-key.hot-qps-threshold` | 1000 | 热 Key 阈值（超过进本地缓存 + 打散） |
| `hot-key.red-qps-threshold` | 2000 | 红色告警阈值 |
| `hot-key.replica-count` | 8 | 打散副本数（建议为集群 master 数的 2~4 倍） |
| `hot-key.local-cache-expire-seconds` | 10 | 本地缓存 TTL（也是 Redis 宕机后可兜底的时长） |
| `hot-key.local-cache-max-size` | 2000 | 本地缓存容量上限 |
| `seckill.stock-batch-size` | 100 | 单次从 Redis 领取的本地配额 |
| `seckill.max-local-stock` | 5000 | 单实例本地配额上限 |
| `redis.pubsub-enabled` | false | 多实例本地缓存失效广播（需 Redis 可用） |
| `spring.sql.init.mode` | always | 启动时执行建表 + 种子数据 |

---

## 8. 关键设计取舍（面试可讲）

1. **为什么不用 `ConcurrentHashMap` 统计热 Key？**
   Key 数量不可控会 OOM，且高并发下写锁竞争严重。CMS 用固定内存给出频率上界估计，对「判定是否热点」已足够。

2. **为什么库存不能打散？**
   库存是强一致数据，N 个副本各自扣减必然超卖。正确做法是本地配额预扣减（Redis QPS 从「每单 1 次」降到「每百单 1 次」）+ Lua 原子兜底 + 活动结束 DB 对账。

3. **本地缓存一致性怎么保证？**
   TTL 兜底（10s）+ 更新时主动失效 + Pub/Sub 广播所有实例。Pub/Sub 不保证送达，因此 TTL 是不可省略的最终防线。

4. **Redis 宕机怎么办？**
   `ProductQueryService` 捕获连接异常后读本地内存并标记 `degraded=true`；健康检查返回 `OUT_OF_SERVICE`（HTTP 200，避免被 K8s 误杀），Pub/Sub 容器按需启动、失败不阻塞启动。

5. **本项目在开发中发现并修复的真实缺陷**（均已补充回归测试）：
   - `ProductDetailVo` 只有 `@Builder` 没有无参构造 → Jackson 反序列化失败、读到 null；
   - 开启 Lettuce 连接池但缺 `commons-pool2` → 启动即 `NoClassDefFoundError`；
   - `delete(Collection)` 多 Key 删除在代理下的返回类型不匹配；
   - `RedisTemplate.opsForValue()` 本身就可能抛连接异常，必须放在 try 内。

---

## 9. 阿里开发规约落实

- 分层：`controller → service → mapper`，禁止 Controller 直连 Mapper 做业务；
- 命名：PO/VO/DTO 后缀区分，常量类 `RedisKeyConstants`，枚举替代魔法值；
- 异常：`BizException` + `GlobalExceptionHandler` 统一返回结构 `Result<T>`；
- 日志：`slf4j` 占位符，禁止字符串拼接；关键链路 INFO，异常 ERROR 带堆栈；
- 并发：`AtomicLong`/`ConcurrentHashMap`/CAS，单元测试覆盖并发扣减；
- 数据库：表必备主键、`create_time`/`update_time`、逻辑删除、唯一索引幂等（`uk_activity_user`）；
- 静态检查：`mvn -Ppmd -DskipTests compile pmd:check`（规则集见 `config/pmd-ruleset.xml`）。

> **关于 p3c 插件**：`p3c-pmd 2.1.1` 的规则实现编译在 PMD 6 API 之上，而 PMD 6 无法解析
> JDK 21 字节码（`Unsupported class file major version 65`），二者无法同时满足。
> 因此本项目改用 PMD 7 + 与阿里规约重合的规则（日志、异常、集合、无用代码、命名、并发），
> 当前检查结果：**0 处解析错误、0 处违规**。若项目降到 JDK 8/11，可直接改用 p3c 官方规则集。

---

## 10. 常见问题

**Q：启动报 `Unable to connect to Redis`？**
A：这是**预期行为**。本项目的定位是「Redis 可降级」，没有 Redis 时依然能启动、能查商品（走 DB/本地缓存），只有秒杀扣库存会返回 `503 当前为降级模式`。装好 Redis 后即可全功能运行。

**Q：端口冲突？**
A：`java -jar ... --server.port=18080`；Redis 端口用 `--spring.data.redis.port=16379`。

**Q：Windows 控制台中文乱码？**
A：启动加 `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8`；`pom.xml` 的 surefire 已配置测试编码，源码统一 UTF-8。

**Q：想跑集群模式？**
A：把 `spring.data.redis` 换成 `cluster.nodes`，并开启 `RedisPubSubConfig`（`miao-sha.redis.pubsub-enabled=true`）；打散副本数建议 ≥ master 数。
