package com.hzp.miaosha.service.seckill;

import com.hzp.miaosha.config.properties.SeckillProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地库存配额管理器（强一致数据的降 Redis 压力方案）。
 * <p>
 * 秒杀库存<b>不能</b>打散：库存分散到 N 个副本后，每个副本独立扣减会超卖。
 * 正确做法是：从 Redis 一次性「领取」一批库存放到本实例内存，
 * 之后本地 CAS 扣减，Redis QPS 从「每单一次」降为「每批一次」。
 * </p>
 * <p>代价：本实例宕机时会丢失未售出的配额，因此需要：
 * <ol>
 *   <li>配额不宜过大（默认 100）；</li>
 *   <li>实例优雅下线时归还配额（{@link #releaseAll()}）；</li>
 *   <li>活动结束后以 DB 为准对账。</li>
 * </ol>
 * </p>
 *
 * @author hzp
 */
@Slf4j
@Component
public class LocalStockManager {

    private final SeckillProperties seckillProperties;

    /**
     * 排名配额：stockKey -> 本地剩余配额。
     */
    private final Map<String, AtomicInteger> localStock = new ConcurrentHashMap<>(64);

    /**
     * 累计领取数量：stockKey -> 已从 Redis 领取的总量。
     */
    private final Map<String, AtomicInteger> fetchedTotal = new ConcurrentHashMap<>(64);

    public LocalStockManager(SeckillProperties seckillProperties) {
        this.seckillProperties = seckillProperties;
    }

    /**
     * 存入配额。
     *
     * @param stockKey 库存 key
     * @param quantity 数量
     */
    public void deposit(String stockKey, int quantity) {
        if (quantity <= 0) {
            return;
        }
        localStock.computeIfAbsent(stockKey, key -> new AtomicInteger(0)).addAndGet(quantity);
        fetchedTotal.computeIfAbsent(stockKey, key -> new AtomicInteger(0)).addAndGet(quantity);
    }

    /**
     * 本地扣减。
     *
     * @param stockKey 库存 key
     * @param quantity 数量
     * @return 是否扣减成功
     */
    public boolean tryConsume(String stockKey, int quantity) {
        AtomicInteger counter = localStock.get(stockKey);
        if (counter == null) {
            return false;
        }
        while (true) {
            int current = counter.get();
            if (current < quantity) {
                return false;
            }
            if (counter.compareAndSet(current, current - quantity)) {
                return true;
            }
        }
    }

    /**
     * 归还本地配额（例如 Redis 侧扣减失败时回滚）。
     *
     * @param stockKey 库存 key
     * @param quantity 数量
     */
    public void giveBack(String stockKey, int quantity) {
        AtomicInteger counter = localStock.get(stockKey);
        if (counter != null && quantity > 0) {
            counter.addAndGet(quantity);
        }
    }

    /**
     * 查询本地剩余配额。
     *
     * @param stockKey 库存 key
     * @return 剩余配额（无配额返回 0）
     */
    public int available(String stockKey) {
        AtomicInteger counter = localStock.get(stockKey);
        return counter == null ? 0 : counter.get();
    }

    /**
     * 查询是否还有本地配额。
     *
     * @param stockKey 库存 key
     * @return 是否还有配额
     */
    public boolean hasQuota(String stockKey) {
        return available(stockKey) > 0;
    }

    /**
     * 是否允许继续回源领取。
     *
     * @return 是否允许
     */
    public boolean allowRefill() {
        return seckillProperties.isAllowRefill();
    }

    /**
     * 单次领取配额大小。
     *
     * @return 配额大小
     */
    public int batchSize() {
        return seckillProperties.getStockBatchSize();
    }

    /**
     * 单实例配额上限。
     *
     * @return 上限
     */
    public int maxLocalStock() {
        return seckillProperties.getMaxLocalStock();
    }

    /**
     * 优雅下线：归还全部未售出配额。
     *
     * @return 需要归还的 map（stockKey -> 数量）
     */
    public Map<String, Integer> releaseAll() {
        Map<String, Integer> released = new ConcurrentHashMap<>(localStock.size());
        localStock.forEach((key, counter) -> {
            int remaining = counter.getAndSet(0);
            if (remaining > 0) {
                released.put(key, remaining);
            }
        });
        log.info("本地库存配额已释放，共 {} 个 key", released.size());
        return released;
    }

    /**
     * 清理某个 key 的本地状态。
     *
     * @param stockKey 库存 key
     */
    public void clear(String stockKey) {
        localStock.remove(stockKey);
        fetchedTotal.remove(stockKey);
    }

    /**
     * 统计已领取总量。
     *
     * @param stockKey 库存 key
     * @return 已领取总量
     */
    public int fetchedTotal(String stockKey) {
        AtomicInteger total = fetchedTotal.get(stockKey);
        return total == null ? 0 : total.get();
    }

    public Map<String, AtomicInteger> snapshot() {
        return new ConcurrentHashMap<>(localStock);
    }
}
