package com.hzp.miaosha.service.cache;

import com.hzp.miaosha.common.util.JsonUtil;
import com.hzp.miaosha.domain.vo.ProductDetailVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 库存缓存组件（Redis + Lua 原子操作）。
 * <p>为什么库存不能像商品详情一样简单打散：
 * 库存是强一致数据，分散到多个副本会出现「各副本独立扣减导致超卖」。
 * 因此库存只保留唯一 key，靠「本地配额预扣减 + 异步对账」降低 Redis 压力。</p>
 *
 * @author hzp
 */
@Slf4j
@Component
public class StockCacheService {

    /**
     * 原子「判断 + 扣减」脚本，返回扣减后的剩余库存；-1 表示 key 不存在，-2 表示库存不足。
     */
    private static final String DECREASE_SCRIPT = """
            local v = redis.call('GET', KEYS[1])
            if not v then return -1 end
            if tonumber(v) < tonumber(ARGV[1]) then return -2 end
            return redis.call('DECRBY', KEYS[1], ARGV[1])
            """;

    /**
     * 原子「领取配额」脚本：把 Redis 库存搬运到实例本地，返回实际领取数量。
     */
    private static final String FETCH_BATCH_SCRIPT = """
            local v = redis.call('GET', KEYS[1])
            if not v then return 0 end
            local current = tonumber(v)
            local want = tonumber(ARGV[1])
            if current <= 0 then return 0 end
            local take = math.min(current, want)
            redis.call('DECRBY', KEYS[1], take)
            return take
            """;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisScript<Long> decreaseScript;

    private final RedisScript<Long> fetchBatchScript;

    public StockCacheService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.decreaseScript = new DefaultRedisScript<>(DECREASE_SCRIPT, Long.class);
        this.fetchBatchScript = new DefaultRedisScript<>(FETCH_BATCH_SCRIPT, Long.class);
    }

    /**
     * 初始化库存（不存在才写入，避免重复预热重置已售库存）。
     *
     * @param stockKey 库存 key
     * @param stock    库存数
     * @return 是否写入
     */
    public boolean initStockIfAbsent(String stockKey, int stock) {
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(stockKey, String.valueOf(stock));
        return Boolean.TRUE.equals(success);
    }

    /**
     * 强制重置库存（运维修复用）。
     *
     * @param stockKey 库存 key
     * @param stock    库存数
     */
    public void resetStock(String stockKey, int stock) {
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }

    /**
     * 原子扣减库存。
     *
     * @param stockKey 库存 key
     * @param quantity 扣减数量
     * @return 扣减后库存；-1 key 不存在；-2 库存不足
     */
    public long decrease(String stockKey, int quantity) {
        Long result = stringRedisTemplate.execute(decreaseScript,
                Collections.singletonList(stockKey), String.valueOf(quantity));
        return Objects.isNull(result) ? -1L : result;
    }

    /**
     * 从 Redis 领取一批库存到本地（本地配额预扣减的核心）。
     *
     * @param stockKey  库存 key
     * @param batchSize 期望领取数量
     * @return 实际领取数量
     */
    public long fetchBatch(String stockKey, int batchSize) {
        Long result = stringRedisTemplate.execute(fetchBatchScript,
                Collections.singletonList(stockKey), String.valueOf(batchSize));
        return Objects.isNull(result) ? 0L : result;
    }

    /**
     * 归还未售出的本地配额（实例下线/活动结束时对账）。
     *
     * @param stockKey 库存 key
     * @param quantity 归还数量
     * @return 归还后的库存；-1 表示 key 不存在
     */
    public long giveBack(String stockKey, int quantity) {
        if (quantity <= 0) {
            return -1L;
        }
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
            return -1L;
        }
        Long remain = stringRedisTemplate.opsForValue().increment(stockKey, quantity);
        return Objects.isNull(remain) ? -1L : remain;
    }

    /**
     * 查询剩余库存。
     *
     * @param stockKey 库存 key
     * @return 库存；key 不存在返回 -1
     */
    public long getStock(String stockKey) {
        String value = stringRedisTemplate.opsForValue().get(stockKey);
        if (Objects.isNull(value)) {
            return -1L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            log.warn("库存值非法，stockKey={}, value={}", stockKey, value);
            return -1L;
        }
    }

    /**
     * 批量初始化活动内所有商品库存。
     *
     * @param pairs 库存 key 与库存数（顺序：key, value, key, value ...）
     */
    public void batchInit(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("库存初始化参数必须成对出现");
        }
        for (int i = 0; i < pairs.length; i += 2) {
            initStockIfAbsent(pairs[i], Integer.parseInt(pairs[i + 1]));
        }
    }

    /**
     * 商品详情缓存写入（供预热非库存数据使用）。
     *
     * @param key      缓存 key
     * @param value    值
     * @param ttlSecs  过期秒数
     */
    public void setJson(String key, Object value, long ttlSecs) {
        try {
            stringRedisTemplate.opsForValue().set(key, JsonUtil.toJson(value),
                    java.time.Duration.ofSeconds(ttlSecs));
        } catch (RedisConnectionFailureException ex) {
            log.warn("写入 Redis 失败，key={}", key);
        }
    }

    /**
     * 读取商品对象缓存（返回 null 表示不存在）。
     *
     * @param key 缓存 key
     * @return 商品详情
     */
    public ProductDetailVo getProduct(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            return JsonUtil.fromJson(json, ProductDetailVo.class);
        } catch (DataAccessException ex) {
            log.warn("读取 Redis 失败，key={}", key);
            return null;
        }
    }

    /**
     * 删除多个 key。
     *
     * @param keys key 列表
     * @return 删除数量
     */
    public long delete(List<String> keys) {
        if (Objects.isNull(keys) || keys.isEmpty()) {
            return 0L;
        }
        Long deleted = stringRedisTemplate.delete(keys);
        return Objects.isNull(deleted) ? 0L : deleted;
    }
}
