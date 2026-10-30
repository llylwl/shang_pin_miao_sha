package com.hzp.miaosha.service.cache;

import com.hzp.miaosha.common.algorithm.SlidingWindowCounter;
import com.hzp.miaosha.common.annotation.RedisHotKeyTrack;
import com.hzp.miaosha.common.constant.RedisKeyConstants;
import com.hzp.miaosha.common.util.JsonUtil;
import com.hzp.miaosha.config.properties.HotKeyProperties;
import com.hzp.miaosha.domain.po.ProductPo;
import com.hzp.miaosha.domain.vo.ProductDetailVo;
import com.hzp.miaosha.mapper.ProductMapper;
import com.hzp.miaosha.service.hotkey.HotKeyCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 商品详情查询服务：把「客户端监控 + 二级缓存 + 热 Key 打散 + 降级」串成一条链路。
 * <p>查询链路（与热 Key 治理方案一一对应）：</p>
 * <pre>
 * 1. 记录访问      -> 客户端计数器（Count-Min Sketch 滑动窗口）
 * 2. 判定是否热 Key -> QPS 达到阈值则启用本地缓存 + 打散
 * 3. 本地缓存命中   -> 直接返回（Redis 零访问）
 * 4. Redis 随机副本 -> 读取打散后的副本 key
 * 5. 回源 DB        -> 空值缓存防止穿透
 * 6. Redis 异常     -> 读本地内存兜底，并标记降级
 * </pre>
 *
 * @author hzp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private static final long NULL_VALUE_TTL_SECONDS = 120L;

    private static final String NULL_PLACEHOLDER = "__NULL__";

    private static final String SOURCE_LOCAL = "LOCAL";

    private static final String SOURCE_REDIS = "REDIS";

    private static final String SOURCE_REDIS_REPLICA = "REDIS_REPLICA";

    private static final String SOURCE_DB = "DB";

    private static final String SOURCE_LOCAL_DEGRADED = "LOCAL_DEGRADED";

    private static final String SOURCE_DB_DEGRADED = "DB_DEGRADED";

    private final ProductMapper productMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisConnectionFactory redisConnectionFactory;

    private final HotKeyCollector hotKeyCollector;

    private final LocalProductCache localProductCache;

    private final HotKeySplitter hotKeySplitter;

    private final HotKeyProperties properties;

    private final CacheInvalidateBroadcaster cacheInvalidateBroadcaster;

    /**
     * Redis 实际访问次数，用于演示治理效果（越接近 0 说明本地缓存越有效）。
     */
    private final AtomicLong redisAccessCount = new AtomicLong();

    /**
     * 降级次数。
     */
    private final AtomicLong degradeCount = new AtomicLong();

    /**
     * 查询商品详情。
     *
     * @param productId 商品ID
     * @return 商品详情；不存在返回 null
     */
    @RedisHotKeyTrack(value = "PRODUCT", keyPrefix = RedisKeyConstants.PRODUCT_PREFIX)
    public ProductDetailVo queryDetail(Long productId) {
        if (Objects.isNull(productId) || productId <= 0L) {
            return null;
        }
        String mainKey = hotKeySplitter.mainProductKey(productId);
        // 1. 客户端本地打点：离 key 最近的一层统计
        hotKeyCollector.record(mainKey);
        boolean hot = hotKeyCollector.isHot(mainKey);

        // 2. 本地二级缓存：只有热 Key 才写入，避免污染本地内存
        if (hot) {
            ProductDetailVo localValue = localProductCache.get(productId);
            if (Objects.nonNull(localValue)) {
                return copyWithSource(localValue, SOURCE_LOCAL, false);
            }
        }

        // 3. Redis：热 Key 走随机副本，实现读流量打散
        // 注意：必须写在 try 内——取得操作句柄这一步就可能因连接不可用而失败
        String redisKey = hot ? hotKeySplitter.randomReplicaKey(productId) : mainKey;
        String cached;
        try {
            redisAccessCount.incrementAndGet();
            cached = stringRedisTemplate.opsForValue().get(redisKey);
        } catch (RuntimeException ex) {
            // 6. Redis 不可用：降级读本地内存
            degradeCount.incrementAndGet();
            log.warn("Redis 不可用，降级读本地缓存，productId={}, cause={}", productId, ex.getMessage());
            return degradeToLocal(productId);
        }

        if (Objects.nonNull(cached)) {
            if (NULL_PLACEHOLDER.equals(cached)) {
                return null;
            }
            ProductDetailVo cachedVo = JsonUtil.fromJson(cached, ProductDetailVo.class);
            if (Objects.isNull(cachedVo)) {
                return null;
            }
            if (hot) {
                localProductCache.put(productId, cachedVo);
            }
            return copyWithSource(cachedVo, hot ? SOURCE_REDIS_REPLICA : SOURCE_REDIS, false);
        }

        // 4. 回源数据库
        ProductPo productPo = productMapper.selectById(productId);
        if (Objects.isNull(productPo)) {
            // 空值缓存：防止缓存穿透
            try {
                stringRedisTemplate.opsForValue()
                        .set(mainKey, NULL_PLACEHOLDER, Duration.ofSeconds(NULL_VALUE_TTL_SECONDS));
            } catch (RuntimeException ex) {
                log.warn("写入空值缓存失败，productId={}", productId);
            }
            return null;
        }
        ProductDetailVo detailVo = toVo(productPo);
        String json = JsonUtil.toJson(detailVo);
        try {
            ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
            Duration ttl = Duration.ofSeconds(properties.getProductCacheSeconds());
            ops.set(mainKey, json, ttl);
            if (hot) {
                // 预热全部副本，读请求才能均匀分布到各副本
                for (String replicaKey : hotKeySplitter.allReplicaKeys(productId)) {
                    ops.set(replicaKey, json, ttl);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("回填 Redis 失败，productId={}", productId);
        }
        if (hot) {
            localProductCache.put(productId, detailVo);
        }
        return copyWithSource(detailVo, SOURCE_DB, false);
    }

    /**
     * 预热指定商品到 Redis 与本地缓存（活动开始前调用）。
     *
     * @param productId 商品ID
     * @return 是否预热成功
     */
    @RedisHotKeyTrack(value = "PRODUCT_WARM_UP", keyPrefix = RedisKeyConstants.PRODUCT_PREFIX)
    public boolean warmUp(Long productId) {
        ProductPo productPo = productMapper.selectById(productId);
        if (Objects.isNull(productPo)) {
            log.warn("预热失败，商品不存在，productId={}", productId);
            return false;
        }
        ProductDetailVo detailVo = toVo(productPo);
        String json = JsonUtil.toJson(detailVo);
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        Duration ttl = Duration.ofSeconds(properties.getProductCacheSeconds());
        ops.set(hotKeySplitter.mainProductKey(productId), json, ttl);
        for (String replicaKey : hotKeySplitter.allReplicaKeys(productId)) {
            ops.set(replicaKey, json, ttl);
        }
        localProductCache.put(productId, detailVo);
        log.info("商品预热完成，productId={}, replica={}", productId, hotKeySplitter.getReplicaCount());
        return true;
    }

    /**
     * 删除缓存（商品更新/下架时调用），并广播给所有实例失效本地缓存。
     *
     * @param productId 商品ID
     */
    public void evict(Long productId) {
        localProductCache.invalidate(productId);
        List<String> replicaKeys = hotKeySplitter.allReplicaKeys(productId);
        try {
            stringRedisTemplate.delete(hotKeySplitter.mainProductKey(productId));
            stringRedisTemplate.delete(replicaKeys);
        } catch (RuntimeException ex) {
            log.warn("删除 Redis 缓存失败，productId={}, 原因={}", productId, ex.getMessage());
        }
        cacheInvalidateBroadcaster.broadcast(productId);
        log.info("清理商品缓存，productId={}, 副本数={}", productId, replicaKeys.size());
    }

    /**
     * 当前 TopN 热 Key。
     *
     * @param topN 条数
     * @return TopN
     */
    public List<SlidingWindowCounter.HotKeyItem> topHotKeys(int topN) {
        return hotKeyCollector.topN(topN);
    }

    public long getRedisAccessCount() {
        return redisAccessCount.get();
    }

    public long getDegradeCount() {
        return degradeCount.get();
    }

    /**
     * 重置统计（压测前后对比使用）。
     */
    public void resetStatistics() {
        redisAccessCount.set(0L);
        degradeCount.set(0L);
    }

    /**
     * 判断 Redis 是否可用（健康检查使用）。
     *
     * @return 是否可用
     */
    public boolean isRedisAvailable() {
        try {
            redisConnectionFactory.getConnection().close();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 主 key 前缀（调试展示用）。
     *
     * @return 前缀
     */
    public String productKeyPrefix() {
        return RedisKeyConstants.PRODUCT_PREFIX;
    }

    private ProductDetailVo degradeToLocal(Long productId) {
        ProductDetailVo localValue = localProductCache.get(productId);
        if (Objects.nonNull(localValue)) {
            return copyWithSource(localValue, SOURCE_LOCAL_DEGRADED, true);
        }
        ProductPo productPo = productMapper.selectById(productId);
        if (Objects.isNull(productPo)) {
            return null;
        }
        ProductDetailVo detailVo = toVo(productPo);
        localProductCache.put(productId, detailVo);
        return copyWithSource(detailVo, SOURCE_DB_DEGRADED, true);
    }

    private ProductDetailVo toVo(ProductPo po) {
        return ProductDetailVo.builder()
                .productId(po.getId())
                .productNo(po.getProductNo())
                .productName(po.getProductName())
                .title(po.getTitle())
                .price(Objects.isNull(po.getPrice()) ? null : po.getPrice().toPlainString())
                .originPrice(Objects.isNull(po.getOriginPrice()) ? null : po.getOriginPrice().toPlainString())
                .stock(po.getStock())
                .status(po.getStatus())
                .hot(Objects.equals(1, po.getIsHot()))
                .build();
    }

    private ProductDetailVo copyWithSource(ProductDetailVo source, String sourceName, boolean degraded) {
        return ProductDetailVo.builder()
                .productId(source.getProductId())
                .productNo(source.getProductNo())
                .productName(source.getProductName())
                .title(source.getTitle())
                .price(source.getPrice())
                .originPrice(source.getOriginPrice())
                .stock(source.getStock())
                .status(source.getStatus())
                .hot(source.getHot())
                .source(sourceName)
                .degraded(degraded)
                .build();
    }
}
