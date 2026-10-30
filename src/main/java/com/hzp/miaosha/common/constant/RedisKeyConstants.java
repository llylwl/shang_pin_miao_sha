package com.hzp.miaosha.common.constant;

/**
 * Redis Key 统一常量。
 * <p>阿里规约：禁止在代码中硬编码字符串，统一收敛到常量类。</p>
 *
 * @author hzp
 */
public final class RedisKeyConstants {

    /**
     * Redis 键分隔符。
     */
    public static final String SEPARATOR = ":";

    /**
     * 商品详情（主 Key），例如 product:1。
     */
    public static final String PRODUCT_PREFIX = "product:";

    /**
     * 商品详情（打散后的热 Key 副本），例如 hot-product:1:3。
     */
    public static final String HOT_PRODUCT_PREFIX = "hot-product:";

    /**
     * 库存 Key，例如 stock:1。
     */
    public static final String STOCK_PREFIX = "stock:";

    /**
     * 本地缓存失效广播频道。
     */
    public static final String CACHE_INVALIDATE_CHANNEL = "cache:invalidate:topic";

    /**
     * 秒杀去重（同一用户同一活动只允许一次），例如 seckill:dedup:1。
     */
    public static final String SECKILL_DEDUP_PREFIX = "seckill:dedup:";

    private RedisKeyConstants() {
    }
}
