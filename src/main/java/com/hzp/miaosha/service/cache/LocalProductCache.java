package com.hzp.miaosha.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.hzp.miaosha.config.properties.HotKeyProperties;
import com.hzp.miaosha.domain.vo.ProductDetailVo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 本地二级缓存（JVM 内，Caffeine 实现）。
 * <p>
 * 作用：
 * <ul>
 *   <li>把「热点商品」提前加载到本地内存，削掉 90%+ 的 Redis 读；</li>
 *   <li>Redis 宕机时作为兜底数据源（降级读本地内存）。</li>
 * </ul>
 * 关键约束：
 * <ul>
 *   <li>必须有 TTL 上限，防止商品更新后本地永久脏读；</li>
 *   <li>写操作后需主动失效，并通过 Pub/Sub 广播到所有实例。</li>
 * </ul>
 * </p>
 *
 * @author hzp
 */
@Slf4j
@Component
public class LocalProductCache {

    private final HotKeyProperties properties;

    private Cache<String, ProductDetailVo> cache;

    public LocalProductCache(HotKeyProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getLocalCacheMaxSize())
                .expireAfterWrite(Duration.ofSeconds(properties.getLocalCacheExpireSeconds()))
                .recordStats()
                .build();
        log.info("本地二级缓存初始化完成，maxSize={}, expire={}s",
                properties.getLocalCacheMaxSize(), properties.getLocalCacheExpireSeconds());
    }

    /**
     * 读取本地缓存。
     *
     * @param productId 商品ID
     * @return 商品；未命中返回 null
     */
    public ProductDetailVo get(Long productId) {
        return cache.getIfPresent(buildKey(productId));
    }

    /**
     * 写入本地缓存。
     *
     * @param productId 商品ID
     * @param detailVo  商品详情
     */
    public void put(Long productId, ProductDetailVo detailVo) {
        if (Objects.isNull(productId) || Objects.isNull(detailVo)) {
            return;
        }
        cache.put(buildKey(productId), detailVo);
    }

    /**
     * 仅当本地缓存已存在时更新（避免把冷商品也放进本地缓存）。
     *
     * @param productId 商品ID
     * @param detailVo  商品详情
     * @return 是否更新成功
     */
    public boolean putIfPresent(Long productId, ProductDetailVo detailVo) {
        String key = buildKey(productId);
        if (Objects.isNull(cache.getIfPresent(key))) {
            return false;
        }
        cache.put(key, detailVo);
        return true;
    }

    /**
     * 删除单个 key。
     *
     * @param productId 商品ID
     */
    public void invalidate(Long productId) {
        cache.invalidate(buildKey(productId));
    }

    /**
     * 批量删除。
     *
     * @param productIds 商品ID集合
     */
    public void invalidateAll(List<Long> productIds) {
        if (Objects.isNull(productIds) || productIds.isEmpty()) {
            return;
        }
        cache.invalidateAll(productIds.stream().map(LocalProductCache::buildKey).toList());
    }

    /**
     * 清空（测试/运维使用）。
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 当前缓存条数。
     *
     * @return 条数
     */
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    /**
     * 命中统计。
     *
     * @return 统计信息
     */
    public CacheStats stats() {
        return cache.stats();
    }

    /**
     * 缓存 key 规则：只以商品ID为 key，避免「product:1」与「hot-product:1:3」两套 key 重复命中。
     *
     * @param productId 商品ID
     * @return 本地缓存 key
     */
    public static String buildKey(Long productId) {
        return String.valueOf(productId);
    }
}
