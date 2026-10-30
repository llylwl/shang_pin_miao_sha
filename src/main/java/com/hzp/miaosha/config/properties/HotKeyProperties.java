package com.hzp.miaosha.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 热 Key 治理配置。
 *
 * @author hzp
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "miao-sha.hot-key")
public class HotKeyProperties {

    /**
     * 是否启用「客户端统计 -> 自动本地缓存」联动。
     */
    private boolean autoLocalCacheEnabled = true;

    /**
     * 判定为热 Key 的 QPS 阈值。
     */
    private int hotQpsThreshold = 1000;

    /**
     * 红色告警 QPS 阈值。
     */
    private int redQpsThreshold = 2000;

    /**
     * 统计窗口秒数。
     */
    private int windowSeconds = 10;

    /**
     * Count-Min Sketch 误差率 ε。
     */
    private double cmsEpsilon = 0.01D;

    /**
     * Count-Min Sketch 置信度 δ。
     */
    private double cmsDelta = 0.001D;

    /**
     * 上报间隔毫秒。
     */
    private long reportIntervalMs = 5000L;

    /**
     * 单次上报 TopN 数量。
     */
    private int topN = 50;

    /**
     * 本地缓存最大条数。
     */
    private long localCacheMaxSize = 2000L;

    /**
     * 本地缓存过期秒数。
     */
    private long localCacheExpireSeconds = 10L;

    /**
     * 热 Key 打散副本数。
     */
    private int replicaCount = 8;

    /**
     * 商品缓存过期秒数。
     */
    private long productCacheSeconds = 600L;
}
