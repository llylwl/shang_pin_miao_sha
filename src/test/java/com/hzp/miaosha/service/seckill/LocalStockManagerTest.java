package com.hzp.miaosha.service.seckill;

import com.hzp.miaosha.config.properties.SeckillProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地库存配额管理器单元测试。
 *
 * @author hzp
 */
@DisplayName("本地库存配额（强一致数据降 Redis 压力）")
class LocalStockManagerTest {

    private LocalStockManager manager;

    private SeckillProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SeckillProperties();
        properties.setStockBatchSize(100);
        properties.setAllowRefill(true);
        properties.setMaxLocalStock(5000);
        manager = new LocalStockManager(properties);
    }

    @Test
    @DisplayName("存入配额后可按次扣减")
    void depositThenConsume() {
        manager.deposit("stock:1", 10);
        assertThat(manager.available("stock:1")).isEqualTo(10);

        assertThat(manager.tryConsume("stock:1", 1)).isTrue();
        assertThat(manager.tryConsume("stock:1", 3)).isTrue();
        assertThat(manager.available("stock:1")).isEqualTo(6);
    }

    @Test
    @DisplayName("配额不足时扣减失败且不产生负数")
    void consumeShouldFailWhenNotEnough() {
        manager.deposit("stock:1", 2);
        assertThat(manager.tryConsume("stock:1", 5)).isFalse();
        assertThat(manager.available("stock:1")).isEqualTo(2);
    }

    @Test
    @DisplayName("未存入配额时扣减失败")
    void consumeWithoutDeposit() {
        assertThat(manager.tryConsume("stock:999", 1)).isFalse();
        assertThat(manager.available("stock:999")).isZero();
    }

    @Test
    @DisplayName("归还配额后可用量恢复")
    void giveBackShouldRestore() {
        manager.deposit("stock:1", 5);
        manager.tryConsume("stock:1", 5);
        manager.giveBack("stock:1", 5);
        assertThat(manager.available("stock:1")).isEqualTo(5);
    }

    @Test
    @DisplayName("并发扣减不会超卖（本地 CAS 保证）")
    void concurrentConsumeShouldNotOversell() throws InterruptedException {
        manager.deposit("stock:1", 100);
        int threads = 20;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < perThread; i++) {
                        if (manager.tryConsume("stock:1", 1)) {
                            success.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(success.get()).isEqualTo(100);
        assertThat(manager.available("stock:1")).isZero();
    }

    @Test
    @DisplayName("releaseAll 返回未售出的配额并清零")
    void releaseAll() {
        manager.deposit("stock:1", 10);
        manager.tryConsume("stock:1", 4);
        manager.deposit("stock:2", 5);

        var released = manager.releaseAll();

        assertThat(released.get("stock:1")).isEqualTo(6);
        assertThat(released.get("stock:2")).isEqualTo(5);
        assertThat(manager.available("stock:1")).isZero();
        assertThat(manager.available("stock:2")).isZero();
    }

    @Test
    @DisplayName("统计已领取总量")
    void fetchedTotal() {
        manager.deposit("stock:1", 100);
        manager.deposit("stock:1", 50);
        assertThat(manager.fetchedTotal("stock:1")).isEqualTo(150);
        assertThat(manager.fetchedTotal("stock:unknown")).isZero();
    }

    @Test
    @DisplayName("允许回源与批量大小读取配置")
    void configAccessors() {
        assertThat(manager.allowRefill()).isTrue();
        assertThat(manager.batchSize()).isEqualTo(100);
        assertThat(manager.maxLocalStock()).isEqualTo(5000);
        properties.setAllowRefill(false);
        assertThat(manager.allowRefill()).isFalse();
    }

    @Test
    @DisplayName("clear 清空单个 key 的状态")
    void clearShouldResetKey() {
        manager.deposit("stock:1", 3);
        manager.clear("stock:1");
        assertThat(manager.available("stock:1")).isZero();
    }
}
