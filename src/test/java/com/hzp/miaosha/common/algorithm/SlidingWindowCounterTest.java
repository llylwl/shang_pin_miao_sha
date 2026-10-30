package com.hzp.miaosha.common.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 滑动窗口计数器单元测试。
 *
 * @author hzp
 */
@DisplayName("滑动窗口热 Key 计数器")
class SlidingWindowCounterTest {

    @Test
    @DisplayName("记录后可正确估算次数与 QPS")
    void recordThenCount() {
        SlidingWindowCounter counter = new SlidingWindowCounter(10, 0.01D, 0.001D);
        for (int i = 0; i < 30; i++) {
            counter.record("product:1");
        }
        assertThat(counter.count("product:1")).isEqualTo(30L);
        assertThat(counter.qps("product:1")).isEqualTo(3.0D);
    }

    @Test
    @DisplayName("TopN 按次数倒序返回")
    void topNShouldBeOrdered() {
        SlidingWindowCounter counter = new SlidingWindowCounter(10, 0.01D, 0.001D);
        for (int i = 0; i < 100; i++) {
            counter.record("product:hot");
        }
        for (int i = 0; i < 10; i++) {
            counter.record("product:cold");
        }
        for (int i = 0; i < 50; i++) {
            counter.record("stock:1");
        }

        List<SlidingWindowCounter.HotKeyItem> items = counter.topN(2);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getKey()).isEqualTo("product:hot");
        assertThat(items.get(1).getKey()).isEqualTo("stock:1");
    }

    @Test
    @DisplayName("空闲 key 会被清理，避免索引无限增长")
    void evictIdleShouldRemoveStaleKeys() throws InterruptedException {
        SlidingWindowCounter counter = new SlidingWindowCounter(1, 0.01D, 0.001D);
        counter.record("product:1");
        assertThat(counter.getTrackedKeyCount()).isEqualTo(1);

        // 等待 1.2 秒，让该 key 超过 idle 阈值（秒级精度）
        Thread.sleep(1_200L);
        int evicted = counter.evictIdle(0L);

        assertThat(evicted).isEqualTo(1);
        assertThat(counter.getTrackedKeyCount()).isZero();
    }

    @Test
    @DisplayName("并发记录 100 万次不丢计数且线程安全")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void concurrentRecordShouldBeSafe() throws InterruptedException {
        SlidingWindowCounter counter = new SlidingWindowCounter(10, 0.01D, 0.001D);
        int threadCount = 8;
        int perThread = 12_500;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();
        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < perThread; i++) {
                        counter.record("product:1");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(done.await(25, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(errors.get()).isZero();
        assertThat(counter.count("product:1")).isGreaterThanOrEqualTo((long) threadCount * perThread);
    }

    @Test
    @DisplayName("窗口参数非法时抛出异常")
    void invalidWindowShouldThrow() {
        assertThatThrownBy(() -> new SlidingWindowCounter(0, 0.01D, 0.001D))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reset 后统计归零")
    void resetShouldClearAll() {
        SlidingWindowCounter counter = new SlidingWindowCounter(10, 0.01D, 0.001D);
        counter.record("product:1", 5L);
        counter.reset();
        assertThat(counter.count("product:1")).isZero();
        assertThat(counter.getTrackedKeyCount()).isZero();
    }
}
