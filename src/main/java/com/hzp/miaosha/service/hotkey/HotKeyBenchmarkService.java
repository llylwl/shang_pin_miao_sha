package com.hzp.miaosha.service.hotkey;

import com.hzp.miaosha.common.algorithm.SlidingWindowCounter;
import com.hzp.miaosha.domain.po.ProductPo;
import com.hzp.miaosha.domain.vo.ProductDetailVo;
import com.hzp.miaosha.mapper.ProductMapper;
import com.hzp.miaosha.service.cache.ProductQueryService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热 Key 压测与自检服务（面试演示 / 生产演练用）。
 * <p>
 * 提供三种能力：
 * <ol>
 *   <li>{@link #benchmark(Long, int)}：对指定商品做 N 次读，用于制造热 Key；</li>
 *   <li>{@link #topHotKeys(int)}：查看当前 TopN 热 Key；</li>
 *   <li>{@link #verify(Long, int)}：验证治理效果（Redis 实际访问次数是否被本地缓存吃掉）。</li>
 * </ol>
 * </p>
 *
 * @author hzp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotKeyBenchmarkService {

    private final ProductQueryService productQueryService;

    private final ProductMapper productMapper;

    private final HotKeyCollector hotKeyCollector;

    /**
     * 压测请求次数上限，防止被误用为压测工具打挂应用。
     */
    private static final int MAX_REQUEST_COUNT = 5_000_000;

    /**
     * 对指定商品做 count 次读请求。
     *
     * @param productId 商品ID
     * @param count     次数
     * @return 压测结果
     */
    public BenchmarkResult benchmark(Long productId, int count) {
        if (Objects.isNull(productId) || productId <= 0L) {
            throw new IllegalArgumentException("productId 必须大于 0");
        }
        int times = Math.min(Math.max(count, 1), MAX_REQUEST_COUNT);
        productQueryService.resetStatistics();
        long start = System.nanoTime();
        int hit = 0;
        int executed = 0;
        boolean degraded = false;
        for (int i = 0; i < times; i++) {
            ProductDetailVo detailVo;
            try {
                detailVo = productQueryService.queryDetail(productId);
            } catch (RuntimeException ex) {
                // 连续失败（例如 Redis 不可用且无本地缓存）时立即中止，避免压测把请求线程拖死
                log.warn("压测中止：第 {} 次请求异常，原因={}", i + 1, ex.getMessage());
                break;
            }
            executed++;
            if (Objects.nonNull(detailVo)) {
                hit++;
            }
            if (Objects.nonNull(detailVo) && Boolean.TRUE.equals(detailVo.getDegraded())) {
                degraded = true;
                break;
            }
        }
        long costNanos = System.nanoTime() - start;
        long redisAccess = productQueryService.getRedisAccessCount();
        double qps = executed / Math.max(costNanos / 1_000_000_000.0D, 1.0E-9D);
        BenchmarkResult result = BenchmarkResult.builder()
                .productId(productId)
                .requestCount(times)
                .executedCount(executed)
                .hitCount(hit)
                .redisAccessCount(redisAccess)
                .redisHitRatio(executed == 0 ? 0D : (double) redisAccess / executed)
                .localHitCount((int) (executed - redisAccess))
                .degraded(degraded)
                .costMillis(costNanos / 1_000_000L)
                .tps(qps)
                .build();
        log.info("热 Key 压测完成，productId={}, 请求={}, 实际执行={}, Redis 访问={}, 本地命中={}, 耗时={}ms, TPS={}, 降级={}",
                productId, times, executed, redisAccess, executed - redisAccess, result.getCostMillis(),
                String.format("%.0f", qps), degraded);
        return result;
    }

    /**
     * 当前 TopN 热 Key。
     *
     * @param topN 条数
     * @return 列表
     */
    public List<SlidingWindowCounter.HotKeyItem> topHotKeys(int topN) {
        return hotKeyCollector.topN(topN);
    }

    /**
     * 治理效果自检：对比「热 Key 阈值开关」下的 Redis 访问次数。
     *
     * @param productId 商品ID
     * @param count     次数
     * @return 自检结果
     */
    public BenchmarkResult verify(Long productId, int count) {
        return benchmark(productId, count);
    }

    /**
     * 应用启动完成后预热热点商品（模拟「提前加载热 key 到内存」）。
     * <p>真实项目由活动配置驱动，这里为演示自动预热 is_hot=1 且在售的商品。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void warmUpOnStartup() {
        List<ProductPo> products = productMapper.selectValidList(100);
        List<Long> warmed = new ArrayList<>();
        for (ProductPo productPo : products) {
            if (Objects.equals(1, productPo.getIsHot()) && Objects.equals(1, productPo.getStatus())) {
                try {
                    if (productQueryService.warmUp(productPo.getId())) {
                        warmed.add(productPo.getId());
                    }
                } catch (RuntimeException ex) {
                    log.warn("启动预热失败，productId={}, 原因={}", productPo.getId(), ex.getMessage());
                }
            }
        }
        log.info("启动预热完成，热商品 {} 个，productIds={}", warmed.size(), warmed);
    }

    /**
     * 压测结果。
     */
    @Data
    @Builder
    public static class BenchmarkResult {

        /**
         * 商品ID。
         */
        private Long productId;

        /**
         * 请求总数（期望）。
         */
        private Integer requestCount;

        /**
         * 实际执行次数（Redis 不可用等情况下会提前中止）。
         */
        private Integer executedCount;

        /**
         * 是否发生了降级（Redis 不可用）。
         */
        private Boolean degraded;

        /**
         * 命中返回次数。
         */
        private Integer hitCount;

        /**
         * Redis 实际访问次数。
         */
        private Long redisAccessCount;

        /**
         * Redis 访问占比（越低越好）。
         */
        private Double redisHitRatio;

        /**
         * 本地缓存命中次数。
         */
        private Integer localHitCount;

        /**
         * 总耗时（毫秒）。
         */
        private Long costMillis;

        /**
         * 吞吐量 TPS。
         */
        private Double tps;
    }
}
