package com.hzp.miaosha.common.algorithm;

import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动窗口热点计数器（基于 Count-Min Sketch + 时间片）。
 * <p>
 * 实现要点：
 * <ol>
 *   <li>把时间切成 {@code windowSeconds} 个「时间片」，每个时间片对应一个 CMS；</li>
 *   <li>记录每次访问时，若当前时间片不是上一次写入的片，则把该片「打点时间」
 *       更新为当前秒，并惰性清空其中残留的旧数据；</li>
 *   <li>查询时只累加「未过期」的片：{@code now - sliceStamp < windowSeconds}；</li>
 *   <li>内存恒定（{@code windowSeconds * width * depth * 8} 字节），
 *       且不会因为 key 数量增长而 OOM。</li>
 * </ol>
 * 该实现适合「客户端本地」统计热 Key：开销约 4 次哈希 + 4 次 CAS，可在 10^5 QPS 级别运行。
 * </p>
 *
 * @author hzp
 */
public class SlidingWindowCounter {

    private final int windowSeconds;

    private final CountMinSketch[] slices;

    /**
     * 每个时间片最近一次被写入的「秒」，用于惰性清空。
     */
    private final long[] sliceStamp;

    private final Object rollLock = new Object();

    /**
     * 第一级索引：key -> 最近访问秒，用于 TopN 扫描与空闲清理。
     * 只保存「窗口内出现过」的 key，规模与热点数量（而非总 key 数）同一量级。
     */
    private final Map<String, Long> keyAccessSecond = new ConcurrentHashMap<>(2048);

    /**
     * 构造滑动窗口计数器。
     *
     * @param windowSeconds 窗口秒数
     * @param epsilon       误差率
     * @param delta         失败概率
     */
    public SlidingWindowCounter(int windowSeconds, double epsilon, double delta) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds 必须大于 0");
        }
        this.windowSeconds = windowSeconds;
        this.slices = new CountMinSketch[windowSeconds];
        this.sliceStamp = new long[windowSeconds];
        for (int i = 0; i < windowSeconds; i++) {
            this.slices[i] = new CountMinSketch(epsilon, delta);
            this.sliceStamp[i] = Long.MIN_VALUE;
        }
    }

    /**
     * 记录一次 key 访问。
     *
     * @param key redis key
     */
    public void record(String key) {
        record(key, 1L);
    }

    /**
     * 记录一次 key 访问（可批量）。
     *
     * @param key   redis key
     * @param times 访问次数
     */
    public void record(String key, long times) {
        long now = currentSecond();
        int index = Math.floorMod(now, windowSeconds);
        synchronized (rollLock) {
            if (sliceStamp[index] != now) {
                // 这一片是 windowSeconds 秒前的旧数据，整体清空后复用
                slices[index].clear();
                sliceStamp[index] = now;
            }
        }
        slices[index].increment(key, times);
        keyAccessSecond.put(key, now);
    }

    /**
     * 估算 key 在窗口内的访问次数。
     *
     * @param key redis key
     * @return 窗口内次数
     */
    public long count(String key) {
        long now = currentSecond();
        long total = 0L;
        for (int i = 0; i < windowSeconds; i++) {
            int index = Math.floorMod(now - i, windowSeconds);
            if (now - sliceStamp[index] >= windowSeconds) {
                continue;
            }
            total += slices[index].estimate(key);
        }
        return total;
    }

    /**
     * 估算 key 的 QPS。
     *
     * @param key redis key
     * @return QPS
     */
    public double qps(String key) {
        return (double) count(key) / windowSeconds;
    }

    /**
     * 输出当前窗口内的 TopN。
     *
     * @param topN 条数
     * @return 按访问次数倒序的列表
     */
    public List<HotKeyItem> topN(int topN) {
        long now = currentSecond();
        List<HotKeyItem> items = new ArrayList<>(keyAccessSecond.size());
        for (Map.Entry<String, Long> entry : keyAccessSecond.entrySet()) {
            if (now - entry.getValue() >= windowSeconds) {
                continue;
            }
            long count = count(entry.getKey());
            if (count > 0L) {
                items.add(new HotKeyItem(entry.getKey(), count, (double) count / windowSeconds));
            }
        }
        items.sort(Comparator.comparingLong(HotKeyItem::getCount).reversed());
        if (items.size() <= topN) {
            return items;
        }
        return new ArrayList<>(items.subList(0, topN));
    }

    /**
     * 清理超过 idleSeconds 未访问的 key，避免第一级索引无限增长。
     *
     * @param idleSeconds 空闲秒数
     * @return 清理数量
     */
    public int evictIdle(long idleSeconds) {
        long now = currentSecond();
        int before = keyAccessSecond.size();
        keyAccessSecond.entrySet().removeIf(entry -> now - entry.getValue() > idleSeconds);
        return before - keyAccessSecond.size();
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public int getTrackedKeyCount() {
        return keyAccessSecond.size();
    }

    /**
     * 重置全部统计（测试用）。
     */
    public synchronized void reset() {
        for (int i = 0; i < windowSeconds; i++) {
            slices[i].clear();
            sliceStamp[i] = Long.MIN_VALUE;
        }
        keyAccessSecond.clear();
    }

    private static long currentSecond() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * 热 Key 条目。
     */
    @Getter
    @ToString
    public static final class HotKeyItem {

        private final String key;

        private final long count;

        private final double qps;

        public HotKeyItem(String key, long count, double qps) {
            this.key = key;
            this.count = count;
            this.qps = qps;
        }
    }
}
