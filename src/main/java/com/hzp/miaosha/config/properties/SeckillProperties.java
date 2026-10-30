package com.hzp.miaosha.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 秒杀业务配置。
 *
 * @author hzp
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "miao-sha.seckill")
public class SeckillProperties {

    /**
     * 单实例从 Redis 一次领取的库存配额。
     */
    private int stockBatchSize = 100;

    /**
     * 本地配额耗尽后是否允许回源 Redis 再领取。
     */
    private boolean allowRefill = true;

    /**
     * 单实例本地配额上限。
     */
    private int maxLocalStock = 5000;

    /**
     * 活动开始前多少秒进行预热。
     */
    private long warmUpBeforeSeconds = 1800L;

    /**
     * 预热扫描间隔毫秒。
     */
    private long warmUpScanIntervalMs = 60000L;
}
