package com.hzp.miaosha.service.hotkey;

import com.hzp.miaosha.common.algorithm.SlidingWindowCounter;
import com.hzp.miaosha.config.properties.HotKeyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 客户端热 Key 数据中心（客户端统计方案的核心）。
 * <p>
 * 每一台应用实例在 JVM 内独立维护一份滑动窗口计数器：
 * <ul>
 *   <li>记录：{@link #record(String, long)}，仅做 4 次哈希 + 4 次 CAS；</li>
 *   <li>判定：{@link #isHot(String)}，用于决定是否进入本地二级缓存；</li>
 *   <li>上报：{@link #topN(int)} + 定时任务，汇总到监控平台。</li>
 * </ul>
 * 不依赖 Redis / 代理，因此即使 Redis 抖动，监控数据依然准确。
 * </p>
 *
 * @author hzp
 */
@Slf4j
@Component
public class HotKeyCollector {

    private final HotKeyProperties properties;

    private final SlidingWindowCounter counter;

    public HotKeyCollector(HotKeyProperties properties) {
        this.properties = properties;
        this.counter = new SlidingWindowCounter(properties.getWindowSeconds(),
                properties.getCmsEpsilon(), properties.getCmsDelta());
        log.info("客户端热 Key 计数器初始化完成，window={}s, cms={}", properties.getWindowSeconds(), counter);
    }

    /**
     * 记录一次访问。
     *
     * @param redisKey Redis 键
     */
    public void record(String redisKey) {
        if (redisKey == null || redisKey.isEmpty()) {
            return;
        }
        counter.record(redisKey);
    }

    /**
     * 批量记录访问。
     *
     * @param redisKey Redis 键
     * @param times    次数
     */
    public void record(String redisKey, long times) {
        if (redisKey == null || redisKey.isEmpty() || times <= 0L) {
            return;
        }
        counter.record(redisKey, times);
    }

    /**
     * 估算某 key 当前 QPS。
     *
     * @param redisKey Redis 键
     * @return QPS
     */
    public double qps(String redisKey) {
        return counter.qps(redisKey);
    }

    /**
     * 判断是否为热 Key。
     *
     * @param redisKey Redis 键
     * @return 是否达到热 Key 阈值
     */
    public boolean isHot(String redisKey) {
        if (!properties.isAutoLocalCacheEnabled()) {
            return false;
        }
        return counter.qps(redisKey) >= properties.getHotQpsThreshold();
    }

    /**
     * 判断是否达到红色告警阈值。
     *
     * @param redisKey Redis 键
     * @return 是否红色告警
     */
    public boolean isRed(String redisKey) {
        return counter.qps(redisKey) >= properties.getRedQpsThreshold();
    }

    /**
     * 获取底层滑动窗口计数器（仅供测试与监控端点读取）。
     *
     * @return 计数器
     */
    public SlidingWindowCounter getCounter() {
        return counter;
    }

    /**
     * 当前窗口 TopN。
     *
     * @param topN 条数
     * @return TopN 列表
     */
    public List<SlidingWindowCounter.HotKeyItem> topN(int topN) {
        return counter.topN(topN);
    }

    /**
     * 清理长时间未访问的 key 索引。
     *
     * @param idleSeconds 空闲秒数
     * @return 清理数量
     */
    public int evictIdle(long idleSeconds) {
        return counter.evictIdle(idleSeconds);
    }

    public int getTrackedKeyCount() {
        return counter.getTrackedKeyCount();
    }

    public int getWindowSeconds() {
        return counter.getWindowSeconds();
    }

    public HotKeyProperties getProperties() {
        return properties;
    }
}
