package com.hzp.miaosha.service.cache;

import com.hzp.miaosha.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 热 Key 打散器单元测试。
 *
 * @author hzp
 */
@DisplayName("热 Key 打散")
class HotKeySplitterTest {

    private HotKeySplitter hotKeySplitter;

    @BeforeEach
    void setUp() {
        hotKeySplitter = new HotKeySplitter(TestDataFactory.hotKeyProperties());
    }

    @Test
    @DisplayName("副本 key 命名符合 hot-product:{id}:{replica} 格式")
    void hotProductKeyFormat() {
        assertThat(hotKeySplitter.hotProductKey(1L, 3)).isEqualTo("hot-product:1:3");
        assertThat(hotKeySplitter.mainProductKey(1L)).isEqualTo("product:1");
    }

    @Test
    @DisplayName("生成全部副本，数量与配置一致且不重复")
    void allReplicaKeys() {
        List<String> keys = hotKeySplitter.allReplicaKeys(1L);
        assertThat(keys).hasSize(8);
        assertThat(keys).doesNotHaveDuplicates();
        assertThat(keys).contains("hot-product:1:0", "hot-product:1:7");
    }

    @Test
    @DisplayName("随机副本落在合法区间，且能均匀打散")
    void randomReplicaShouldSpreadOverAllReplicas() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            String key = hotKeySplitter.randomReplicaKey(1L);
            assertThat(key).startsWith("hot-product:1:");
            seen.add(key);
        }
        assertThat(seen).hasSize(8);
    }

    @Test
    @DisplayName("副本数为 1 时退化为单副本")
    void singleReplica() {
        com.hzp.miaosha.config.properties.HotKeyProperties properties = TestDataFactory.hotKeyProperties();
        properties.setReplicaCount(1);
        HotKeySplitter splitter = new HotKeySplitter(properties);
        assertThat(splitter.randomReplicaKey(2L)).isEqualTo("hot-product:2:0");
        assertThat(splitter.allReplicaKeys(2L)).hasSize(1);
    }
}
