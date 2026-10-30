package com.hzp.miaosha.service.hotkey;

import com.hzp.miaosha.common.algorithm.SlidingWindowCounter;
import com.hzp.miaosha.common.enums.HotKeyLevelEnum;
import com.hzp.miaosha.config.properties.HotKeyProperties;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 热 Key 定时上报器。
 * <p>
 * 每 {@code report-interval-ms} 扫描一次客户端本地计数器：
 * <ul>
 *   <li>把 TopN 以日志 + Micrometer 指标形式上报；</li>
 *   <li>超过红色阈值时打印处置建议（打散 / 本地缓存 / 本地预扣减）。</li>
 * </ul>
 * 真实项目中这里还会推送到 Prometheus / 阿里云 SLS / 自建热 Key 中台。
 * </p>
 *
 * @author hzp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotKeyReporter {

    private static final int LOG_TOP_N = 5;

    private final HotKeyCollector hotKeyCollector;

    private final HotKeyProperties properties;

    private final MeterRegistry meterRegistry;

    /**
     * 最近一次上报的快照，供监控端点读取。
     */
    private volatile List<HotKeySnapshot> lastSnapshot = new ArrayList<>();

    /**
     * 定时扫描并上报。
     */
    @Scheduled(fixedDelayString = "${miao-sha.hot-key.report-interval-ms:5000}",
            initialDelayString = "${miao-sha.hot-key.report-interval-ms:5000}")
    public void report() {
        List<SlidingWindowCounter.HotKeyItem> items = hotKeyCollector.topN(properties.getTopN());
        List<HotKeySnapshot> snapshot = new ArrayList<>(items.size());
        for (SlidingWindowCounter.HotKeyItem item : items) {
            HotKeyLevelEnum level = resolveLevel(item.getQps());
            if (HotKeyLevelEnum.NORMAL == level) {
                continue;
            }
            HotKeySnapshot hotKeySnapshot = HotKeySnapshot.builder()
                    .key(item.getKey())
                    .count(item.getCount())
                    .qps(item.getQps())
                    .level(level)
                    .action(suggest(item.getKey(), level))
                    .build();
            snapshot.add(hotKeySnapshot);
            meterRegistry.gauge("hotkey.qps", io.micrometer.core.instrument.Tags.of("key", item.getKey()),
                    item.getQps());
        }
        this.lastSnapshot = snapshot;
        if (snapshot.isEmpty()) {
            log.debug("本轮未发现热点 Key，window={}s", properties.getWindowSeconds());
            return;
        }
        snapshot.stream().limit(LOG_TOP_N)
                .forEach(item -> log.warn("热 Key 告警 [{}] key={} qps={} count={} 建议={}",
                        item.getLevel().getDesc(), item.getKey(),
                        String.format("%.1f", item.getQps()), item.getCount(), item.getAction()));
        hotKeyCollector.evictIdle(properties.getWindowSeconds() * 3L);
    }

    /**
     * 当前 TopN（含正常级别，供接口展示）。
     *
     * @param topN 条数
     * @return 快照列表
     */
    public List<HotKeySnapshot> currentTopN(int topN) {
        List<SlidingWindowCounter.HotKeyItem> items = hotKeyCollector.topN(topN);
        List<HotKeySnapshot> result = new ArrayList<>(items.size());
        for (SlidingWindowCounter.HotKeyItem item : items) {
            HotKeyLevelEnum level = resolveLevel(item.getQps());
            result.add(HotKeySnapshot.builder()
                    .key(item.getKey())
                    .count(item.getCount())
                    .qps(item.getQps())
                    .level(level)
                    .action(suggest(item.getKey(), level))
                    .build());
        }
        return result;
    }

    /**
     * 最近一次告警快照。
     *
     * @return 快照
     */
    public List<HotKeySnapshot> lastSnapshot() {
        return lastSnapshot;
    }

    private HotKeyLevelEnum resolveLevel(double qps) {
        if (qps >= properties.getRedQpsThreshold()) {
            return HotKeyLevelEnum.RED;
        }
        if (qps >= properties.getHotQpsThreshold()) {
            return HotKeyLevelEnum.YELLOW;
        }
        return HotKeyLevelEnum.NORMAL;
    }

    private String suggest(String key, HotKeyLevelEnum level) {
        if (HotKeyLevelEnum.NORMAL == level) {
            return "无需处理";
        }
        if (key.startsWith(com.hzp.miaosha.common.constant.RedisKeyConstants.STOCK_PREFIX)) {
            return "库存类强一致数据：禁止打散，使用本地配额预扣减 + 定期对账";
        }
        if (key.startsWith(com.hzp.miaosha.common.constant.RedisKeyConstants.PRODUCT_PREFIX)) {
            return HotKeyLevelEnum.RED == level
                    ? "商品详情：本地二级缓存 + 打散副本（replica=" + properties.getReplicaCount() + "）"
                    : "商品详情：加入本地二级缓存即可";
        }
        if (key.startsWith(com.hzp.miaosha.common.constant.RedisKeyConstants.SECKILL_DEDUP_PREFIX)) {
            return "秒杀去重：本地 Bloom/Caffeine 前置拦截，减少 Redis 访问";
        }
        return "观察中，评估打散或本地缓存";
    }

    /**
     * 热 Key 快照。
     */
    @Data
    @Builder
    public static class HotKeySnapshot {

        /**
         * Redis 键。
         */
        private String key;

        /**
         * 窗口内访问次数。
         */
        private Long count;

        /**
         * 估算 QPS。
         */
        private Double qps;

        /**
         * 告警级别。
         */
        private HotKeyLevelEnum level;

        /**
         * 处置建议。
         */
        private String action;

        /**
         * 级别描述。
         *
         * @return 描述
         */
        public String getLevelDesc() {
            return Objects.isNull(level) ? "" : level.getDesc();
        }
    }
}
