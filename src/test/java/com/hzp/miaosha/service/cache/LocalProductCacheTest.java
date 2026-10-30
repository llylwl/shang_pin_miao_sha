package com.hzp.miaosha.service.cache;

import com.hzp.miaosha.domain.vo.ProductDetailVo;
import com.hzp.miaosha.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * \u672C\u5730\u4E8C\u7EA7\u7F13\u5B58\u5355\u5143\u6D4B\u8BD5\u3002
 *
 * @author hzp
 */
@DisplayName("\u672C\u5730\u4E8C\u7EA7\u7F13\u5B58\uFF08Caffeine\uFF09")
class LocalProductCacheTest {

    private LocalProductCache localProductCache;

    @BeforeEach
    void setUp() {
        localProductCache = new LocalProductCache(TestDataFactory.hotKeyProperties());
        localProductCache.init();
    }

    private ProductDetailVo vo(Long id) {
        return ProductDetailVo.builder().productId(id).productName("\u5546\u54C1" + id).build();
    }

    @Test
    @DisplayName("\u5199\u5165\u540E\u53EF\u8BFB\u53D6")
    void putThenGet() {
        localProductCache.put(1L, vo(1L));
        assertThat(localProductCache.get(1L)).isNotNull();
        assertThat(localProductCache.get(1L).getProductName()).isEqualTo("\u5546\u54C11");
    }

    @Test
    @DisplayName("putIfPresent \u4E0D\u4F1A\u4E3A\u51B7\u5546\u54C1\u521B\u5EFA\u7F13\u5B58")
    void putIfPresentShouldNotCreateEntry() {
        boolean created = localProductCache.putIfPresent(2L, vo(2L));
        assertThat(created).isFalse();
        assertThat(localProductCache.get(2L)).isNull();

        localProductCache.put(2L, vo(2L));
        boolean updated = localProductCache.putIfPresent(2L, vo(2L));
        assertThat(updated).isTrue();
    }

    @Test
    @DisplayName("\u5931\u6548\u540E\u8BFB\u53D6\u4E0D\u5230")
    void invalidateShouldRemove() {
        localProductCache.put(1L, vo(1L));
        localProductCache.invalidate(1L);
        assertThat(localProductCache.get(1L)).isNull();
    }

    @Test
    @DisplayName("\u6279\u91CF\u5931\u6548")
    void invalidateAllByIds() {
        localProductCache.put(1L, vo(1L));
        localProductCache.put(2L, vo(2L));
        localProductCache.put(3L, vo(3L));

        localProductCache.invalidateAll(java.util.List.of(1L, 3L));

        assertThat(localProductCache.get(1L)).isNull();
        assertThat(localProductCache.get(2L)).isNotNull();
        assertThat(localProductCache.get(3L)).isNull();
    }

    @Test
    @DisplayName("\u91CD\u590D\u5199\u5165\u540C\u4E00 key \u53EA\u5360\u4E00\u4E2A\u4F4D\u7F6E")
    void duplicateKeyShouldNotGrow() {
        localProductCache.put(1L, vo(1L));
        localProductCache.put(1L, vo(1L));
        assertThat(localProductCache.estimatedSize()).isEqualTo(1L);
    }

    @Test
    @DisplayName("\u7A7A\u503C\u5165\u53C2\u4E0D\u629B\u5F02\u5E38")
    void nullInputShouldBeIgnored() {
        localProductCache.put(null, vo(1L));
        localProductCache.put(1L, null);
        assertThat(localProductCache.estimatedSize()).isZero();
    }
}
