package com.hzp.miaosha.service.cache;

import com.hzp.miaosha.common.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 热 Key 打散器。
 * <p>
 * 原理：把 {@code hot-product:1} 水平拆成 {@code hot-product:1:0 ~ hot-product:1:7} 共
 * {@code replicaCount} 份，读时随机选一份。
 * </p>
 * <p>打散只适用于「读多写少、非强一致」的数据：
 * 商品详情、商品文案、类目聚合等。库存这类强一致数据禁止打散（会导致超卖），
 * 正确做法见 {@code LocalStockManager}（本地配额预扣减）。</p>
 *
 * @author hzp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotKeySplitter {

    private final com.hzp.miaosha.config.properties.HotKeyProperties properties;

    /**
     * 生成随机副本 key。
     *
     * @param productId 商品ID
     * @return 形如 hot-product:1:3
     */
    public String randomReplicaKey(Long productId) {
        int replica = ThreadLocalRandom.current().nextInt(Math.max(1, properties.getReplicaCount()));
        return hotProductKey(productId, replica);
    }

    /**
     * 生成指定副本 key。
     *
     * @param productId 商品ID
     * @param replica   副本序号
     * @return 形如 hot-product:1:3
     */
    public String hotProductKey(Long productId, int replica) {
        return RedisKeyConstants.HOT_PRODUCT_PREFIX + productId + RedisKeyConstants.SEPARATOR + replica;
    }

    /**
     * 生成主 key（未打散）。
     *
     * @param productId 商品ID
     * @return 形如 product:1
     */
    public String mainProductKey(Long productId) {
        return RedisKeyConstants.PRODUCT_PREFIX + productId;
    }

    /**
     * 所有副本 key。
     *
     * @param productId 商品ID
     * @return 副本 key 列表
     */
    public List<String> allReplicaKeys(Long productId) {
        int count = Math.max(1, properties.getReplicaCount());
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> hotProductKey(productId, i))
                .toList();
    }

    public int getReplicaCount() {
        return Math.max(1, properties.getReplicaCount());
    }
}
