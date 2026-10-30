package com.hzp.miaosha.common.algorithm;

import java.util.Arrays;

/**
 * Count-Min Sketch 频率估算器（空间固定，可估算超大基数的 key 频率）。
 * <p>
 * 热点 Key 监控的难点在于：既不能对每个 key 做精确计数（内存会爆），
 * 也不能用全量 {@code ConcurrentHashMap}（GC 压力大、易成为新的锁竞争点）。
 * Count-Min Sketch 用 {@code depth} 个哈希数组，恒定内存即可给出「上界估算」，
 * 用于热点判定足够。误差率 {@code ε}、失败概率 {@code δ} 可配置。
 * </p>
 * <p>线程安全说明：数组元素使用 {@link java.util.concurrent.atomic.AtomicLongArray}
 * 做无锁累加，支持多线程并发写入；{@link #clear()} 为低频操作，加锁执行。</p>
 *
 * @author hzp
 */
public class CountMinSketch {

    private static final int HASH_SEED = 0x9E3779B1;

    private static final int MULTIPLIER_A = 0x85EBCA77;

    private static final int MULTIPLIER_B = 0xC2B2AE3D;

    private final int width;

    private final int depth;

    private final java.util.concurrent.atomic.AtomicLongArray[] table;

    /**
     * 构造估算器。
     *
     * @param epsilon 误差率（越小越准，内存越大）
     * @param delta   失败概率
     */
    public CountMinSketch(double epsilon, double delta) {
        this(computeWidth(epsilon), computeDepth(delta));
    }

    /**
     * 以指定容量构造估算器（便于单元测试确定宽度）。
     *
     * @param width 每个哈希数组的宽度
     * @param depth 哈希函数数量
     */
    @SuppressWarnings("unchecked")
    public CountMinSketch(int width, int depth) {
        if (width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("width 与 depth 必须为正数");
        }
        this.width = width;
        this.depth = depth;
        this.table = new java.util.concurrent.atomic.AtomicLongArray[depth];
        for (int i = 0; i < depth; i++) {
            this.table[i] = new java.util.concurrent.atomic.AtomicLongArray(width);
        }
    }

    /**
     * 计数 + 1。
     *
     * @param key 待统计的 key
     */
    public void increment(String key) {
        increment(key, 1L);
    }

    /**
     * 计数 + delta。
     *
     * @param key   待统计的 key
     * @param delta 增量
     */
    public void increment(String key, long delta) {
        int[] hashes = hashes(key);
        for (int i = 0; i < depth; i++) {
            table[i].addAndGet(hashes[i], delta);
        }
    }

    /**
     * 估算 key 的计数（只会偏大，不会偏小）。
     *
     * @param key 待查询的 key
     * @return 估算值
     */
    public long estimate(String key) {
        int[] hashes = hashes(key);
        long min = Long.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            long value = table[i].get(hashes[i]);
            if (value < min) {
                min = value;
            }
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    /**
     * 清空统计（用于时间片滚动）。
     */
    public synchronized void clear() {
        for (java.util.concurrent.atomic.AtomicLongArray row : table) {
            for (int i = 0; i < width; i++) {
                row.set(i, 0L);
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    private int[] hashes(String key) {
        int h = key.hashCode();
        int[] result = new int[depth];
        for (int i = 0; i < depth; i++) {
            h = h * MULTIPLIER_A + (i + 1) * MULTIPLIER_B + HASH_SEED;
            h ^= h >>> 15;
            h *= 0x2C1B3C6D;
            h ^= h >>> 12;
            result[i] = Math.floorMod(h, width);
        }
        return result;
    }

    private static int computeWidth(double epsilon) {
        if (epsilon <= 0D || epsilon >= 1D) {
            throw new IllegalArgumentException("epsilon 必须位于 (0,1) 区间");
        }
        return Math.max(8, (int) Math.ceil(Math.E / epsilon));
    }

    private static int computeDepth(double delta) {
        if (delta <= 0D || delta >= 1D) {
            throw new IllegalArgumentException("delta 必须位于 (0,1) 区间");
        }
        return Math.max(2, (int) Math.ceil(Math.log(1D / delta)));
    }

    /**
     * 返回内部容量描述，便于日志排查。
     *
     * @return 形如 width=272,depth=7 的字符串
     */
    @Override
    public String toString() {
        return "CountMinSketch{width=" + width + ", depth=" + depth + ", cells=" + Arrays.stream(table).mapToInt(java.util.concurrent.atomic.AtomicLongArray::length).sum() + '}';
    }
}
