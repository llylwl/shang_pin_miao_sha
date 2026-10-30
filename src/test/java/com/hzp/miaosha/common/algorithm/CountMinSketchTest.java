package com.hzp.miaosha.common.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Count-Min Sketch 单元测试。
 *
 * @author hzp
 */
@DisplayName("Count-Min Sketch 频率估算")
class CountMinSketchTest {

    @Test
    @DisplayName("估算值不会小于真实计数")
    void estimateShouldNotBeLessThanRealCount() {
        CountMinSketch sketch = new CountMinSketch(0.01D, 0.001D);
        for (int i = 0; i < 1000; i++) {
            sketch.increment("product:1");
        }
        sketch.increment("product:2", 17L);

        assertThat(sketch.estimate("product:1")).isGreaterThanOrEqualTo(1000L);
        assertThat(sketch.estimate("product:2")).isGreaterThanOrEqualTo(17L);
        assertThat(sketch.estimate("product:unknown")).isZero();
    }

    @Test
    @DisplayName("清空后计数归零")
    void clearShouldResetCounter() {
        CountMinSketch sketch = new CountMinSketch(0.01D, 0.001D);
        sketch.increment("product:1", 50L);
        assertThat(sketch.estimate("product:1")).isEqualTo(50L);

        sketch.clear();

        assertThat(sketch.estimate("product:1")).isZero();
    }

    @Test
    @DisplayName("参数非法时抛出 IllegalArgumentException")
    void invalidParameterShouldThrow() {
        assertThatThrownBy(() -> new CountMinSketch(0D, 0.001D))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epsilon");
        assertThatThrownBy(() -> new CountMinSketch(0.01D, 1D))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delta");
        assertThatThrownBy(() -> new CountMinSketch(0, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("容量按 ε 与 δ 推算：宽度 >= e/ε，深度 >= ln(1/δ)")
    void capacityShouldFollowFormula() {
        CountMinSketch sketch = new CountMinSketch(0.01D, 0.001D);
        assertThat(sketch.getWidth()).isGreaterThanOrEqualTo((int) Math.ceil(Math.E / 0.01D));
        assertThat(sketch.getDepth()).isGreaterThanOrEqualTo((int) Math.ceil(Math.log(1000D)));
    }

    @Test
    @DisplayName("大基数下热点 key 依然可被准确识别")
    void shouldIdentifyHotKeyAmongManyKeys() {
        CountMinSketch sketch = new CountMinSketch(0.001D, 0.01D);
        for (int i = 0; i < 20_000; i++) {
            sketch.increment("product:" + (i % 5_000), 1L);
        }
        for (int i = 0; i < 5_000; i++) {
            sketch.increment("product:hot", 1L);
        }
        assertThat(sketch.estimate("product:hot")).isGreaterThanOrEqualTo(5_000L);
    }
}
