package com.hzp.miaosha.service.cache;

import com.hzp.miaosha.common.constant.RedisKeyConstants;
import com.hzp.miaosha.config.properties.HotKeyProperties;
import com.hzp.miaosha.domain.po.ProductPo;
import com.hzp.miaosha.domain.vo.ProductDetailVo;
import com.hzp.miaosha.mapper.ProductMapper;
import com.hzp.miaosha.service.hotkey.HotKeyCollector;
import com.hzp.miaosha.support.BrokenRedisSupport;
import com.hzp.miaosha.support.InMemoryRedis;
import com.hzp.miaosha.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * \u5546\u54C1\u67E5\u8BE2\u670D\u52A1\u5355\u5143\u6D4B\u8BD5\uFF1A\u9A8C\u8BC1\u300C\u76D1\u63A7 -> \u672C\u5730\u7F13\u5B58 -> \u6253\u6563 -> \u964D\u7EA7\u300D\u5B8C\u6574\u94FE\u8DEF\u3002
 *
 * @author hzp
 */
@DisplayName("\u5546\u54C1\u67E5\u8BE2\u670D\u52A1\uFF08\u70ED Key \u6CBB\u7406\u94FE\u8DEF\uFF09")
class ProductQueryServiceTest {

    private InMemoryRedis redis;

    private StringRedisTemplate redisTemplate;

    private ProductMapper productMapper;

    private HotKeyCollector hotKeyCollector;

    private LocalProductCache localProductCache;

    private HotKeySplitter hotKeySplitter;

    private HotKeyProperties properties;

    private ProductQueryService service;

    private RedisConnectionFactory connectionFactory;

    /**
     * selectById 的返回值约定：未登记的 ID 返回仿真商品，登记为 {@code null} 的返回 null（模拟商品不存在）。
     */
    private final Map<Long, ProductPo> idStubs = new HashMap<>();

    /**
     * 基于 JDK 动态代理的 ProductMapper 测试替身。
     * <p>不使用 Mockito 的 inline mock maker：其依赖运行时自附加 agent，
     * 在部分受限环境（如禁止 attach 的沙箱 / JDK 高版本安全策略）会启动失败，
     * 而这里只需要拦截接口方法，动态代理足够且零额外依赖。</p>
     *
     * @return 可注入的 ProductMapper 替身
     */
    private static ProductMapper stubProductMapper(Map<Long, ProductPo> idStubs) {
        return (ProductMapper) Proxy.newProxyInstance(
                ProductMapper.class.getClassLoader(),
                new Class<?>[]{ProductMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName()) && args != null && args.length == 1) {
                        Long id = (Long) args[0];
                        return idStubs.containsKey(id) ? idStubs.get(id) : TestDataFactory.product(id);
                    }
                    throw new UnsupportedOperationException("测试替身未实现的方法: " + method.getName());
                });
    }

    @BeforeEach
    void setUp() {
        redis = InMemoryRedis.create();
        redisTemplate = redis.newTemplate();
        productMapper = stubProductMapper(idStubs);
        properties = TestDataFactory.hotKeyProperties();
        hotKeyCollector = new HotKeyCollector(properties);
        localProductCache = new LocalProductCache(properties);
        localProductCache.init();
        hotKeySplitter = new HotKeySplitter(properties);
        connectionFactory = redisTemplate.getConnectionFactory();

        CacheInvalidateBroadcaster broadcaster = new CacheInvalidateBroadcaster(redisTemplate, localProductCache);
        // 用 CGLIB 代理包装目标对象并挂上热 Key 切面，确保 @RedisHotKeyTrack 在单元测试中同样生效
        org.springframework.aop.framework.ProxyFactory proxyFactory =
                new org.springframework.aop.framework.ProxyFactory();
        proxyFactory.setTarget(new ProductQueryService(productMapper, redisTemplate, connectionFactory,
                hotKeyCollector, localProductCache, hotKeySplitter, properties, broadcaster));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TrackingInterceptor(hotKeyCollector));
        service = (ProductQueryService) proxyFactory.getProxy();
    }

    /**
     * \u5148\u628A\u67D0\u4E2A key \u5728\u5BA2\u6237\u7AEF\u8BA1\u6570\u5668\u91CC\u5237\u6210\u70ED Key\u3002
     *
     * @param productId \u5546\u54C1ID
     */
    private void makeHot(Long productId) {
        hotKeyCollector.getCounter().record(RedisKeyConstants.PRODUCT_PREFIX + productId, 10_000L);
    }

    @Test
    @DisplayName("\u9996\u6B21\u67E5\u8BE2\u56DE\u6E90 DB\uFF0C\u5E76\u56DE\u586B Redis \u4E3B key")
    void firstQueryShouldLoadFromDbAndBackfill() {
        ProductDetailVo vo = service.queryDetail(1L);

        assertThat(vo).isNotNull();
        assertThat(vo.getSource()).isEqualTo("DB");
        assertThat(redis.store()).containsKey("product:1");
        assertThat(service.getRedisAccessCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("\u51B7 Key \u7B2C\u4E8C\u6B21\u67E5\u8BE2\u547D\u4E2D Redis \u4E3B key\uFF0C\u4E14\u4E0D\u5199\u672C\u5730\u7F13\u5B58")
    void coldKeySecondQueryShouldHitRedis() {
        service.queryDetail(2L);
        ProductDetailVo second = service.queryDetail(2L);

        assertThat(second.getSource()).isEqualTo("REDIS");
        // 2 \u6B21\u90FD\u8BBF\u95EE\u4E86 Redis\uFF08\u51B7 key \u4E0D\u7528\u672C\u5730\u7F13\u5B58\uFF09
        assertThat(service.getRedisAccessCount()).isEqualTo(2L);
        assertThat(localProductCache.estimatedSize()).isZero();
    }

    @Test
    @DisplayName("\u70ED Key \u56DE\u586B 8 \u4E2A\u526F\u672C\uFF0CRedis \u8BBF\u95EE\u6B21\u6570\u88AB\u672C\u5730\u7F13\u5B58\u5403\u6389")
    void hotKeyShouldSplitAndUseLocalCache() {
        makeHot(1L);
        service.queryDetail(1L);

        assertThat(redis.store()).containsKeys("product:1",
                "hot-product:1:0", "hot-product:1:1", "hot-product:1:2", "hot-product:1:3",
                "hot-product:1:4", "hot-product:1:5", "hot-product:1:6", "hot-product:1:7");

        service.resetStatistics();
        for (int i = 0; i < 100; i++) {
            service.queryDetail(1L);
        }
        // 100 \u6B21\u8BF7\u6C42\u5168\u90E8\u547D\u4E2D\u672C\u5730\u7F13\u5B58\uFF0CRedis \u8BBF\u95EE\u6B21\u6570\u5E94\u4E3A 0 \u6216\u6781\u5C11
        assertThat(service.getRedisAccessCount()).isLessThanOrEqualTo(1L);
        assertThat(localProductCache.estimatedSize()).isEqualTo(1L);
    }

    @Test
    @DisplayName("\u70ED Key \u8BFB\u7684\u662F\u6253\u6563\u540E\u7684\u526F\u672C key")
    void hotKeyShouldReadReplicaKey() {
        makeHot(1L);
        // \u5148\u5236\u9020\u4E00\u6B21\u56DE\u6E90\uFF0C\u5199\u6EE1\u526F\u672C
        service.queryDetail(1L);
        // \u6E05\u7A7A\u672C\u5730\u7F13\u5B58\uFF0C\u5F3A\u5236\u8D70 Redis
        localProductCache.invalidate(1L);

        ProductDetailVo vo = service.queryDetail(1L);

        assertThat(vo.getSource()).isEqualTo("REDIS_REPLICA");
    }

    @Test
    @DisplayName("\u5546\u54C1\u4E0D\u5B58\u5728\u65F6\u5199\u5165\u7A7A\u503C\u7F13\u5B58\uFF0C\u9632\u6B62\u7F13\u5B58\u7A7F\u900F")
    void notFoundShouldCacheNull() {
        idStubs.put(99999L, null);
        ProductDetailVo vo = service.queryDetail(99999L);

        assertThat(vo).isNull();
        assertThat(redis.store().get("product:99999")).isEqualTo("__NULL__");
    }

    @Test
    @DisplayName("Redis \u5B95\u673A\u4E14\u672C\u5730\u65E0\u7F13\u5B58\u65F6\u56DE\u6E90 DB \u515C\u5E95")
    void shouldFallbackToDbWhenRedisDownAndLocalMiss() {
        StringRedisTemplate broken = BrokenRedisSupport.brokenTemplate();
        CacheInvalidateBroadcaster brokenBroadcaster = new CacheInvalidateBroadcaster(broken, localProductCache);
        ProductQueryService degradedService = new ProductQueryService(productMapper, broken, connectionFactory,
                hotKeyCollector, localProductCache, hotKeySplitter, properties, brokenBroadcaster);

        ProductDetailVo vo = degradedService.queryDetail(5L);

        assertThat(vo).isNotNull();
        assertThat(vo.getSource()).isEqualTo("DB_DEGRADED");
        assertThat(vo.getDegraded()).isTrue();
    }

    @Test
    @DisplayName("\u9884\u70ED\u4F1A\u5199\u5165\u4E3B key \u4E0E\u5168\u90E8\u526F\u672C")
    void warmUpShouldWriteAllReplicas() {
        boolean success = service.warmUp(3L);

        assertThat(success).isTrue();
        assertThat(redis.store()).containsKeys("product:3", "hot-product:3:0", "hot-product:3:7");
        assertThat(localProductCache.get(3L)).isNotNull();
    }

    @Test
    @DisplayName("\u6E05\u7406\u7F13\u5B58\u4F1A\u5220\u9664\u4E3B key \u4E0E\u526F\u672C\uFF0C\u5E76\u6E05\u7A7A\u672C\u5730\u7F13\u5B58")
    void evictShouldRemoveAllKeys() {
        service.warmUp(4L);
        // 先读一次，确保本地缓存中存在该商品
        localProductCache.put(4L, service.queryDetail(4L));
        service.evict(4L);

        assertThat(redis.store()).doesNotContainKeys("product:4", "hot-product:4:0");
        assertThat(localProductCache.get(4L)).isNull();
    }

    @Test
    @DisplayName("\u975E\u6CD5\u5546\u54C1ID\u76F4\u63A5\u8FD4\u56DE null")
    void invalidIdShouldReturnNull() {
        assertThat(service.queryDetail(null)).isNull();
        assertThat(service.queryDetail(0L)).isNull();
        assertThat(service.queryDetail(-1L)).isNull();
    }

    @Test
    @DisplayName("TopN \u80FD\u8BC6\u522B\u51FA\u88AB\u9AD8\u9891\u8BBF\u95EE\u7684\u5546\u54C1")
    void topHotKeysShouldContainHotProduct() {
        makeHot(1L);
        service.queryDetail(1L);
        service.queryDetail(1L);

        var top = service.topHotKeys(5);
        assertThat(top).isNotEmpty();
        assertThat(top.get(0).getKey()).isEqualTo("product:1");
    }

    @Test
    @DisplayName("\u5546\u54C1\u8BE6\u60C5\u5B57\u6BB5\u6620\u5C04\u6B63\u786E")
    void shouldMapFieldsCorrectly() {
        ProductPo po = TestDataFactory.product(7L);
        idStubs.put(7L, po);
        ProductDetailVo vo = service.queryDetail(7L);

        assertThat(vo.getProductId()).isEqualTo(7L);
        assertThat(vo.getProductName()).isEqualTo(po.getProductName());
        assertThat(vo.getPrice()).isEqualTo("7999.00");
        assertThat(vo.getHot()).isTrue();
    }
}
