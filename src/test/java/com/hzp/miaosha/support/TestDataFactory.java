package com.hzp.miaosha.support;

import com.hzp.miaosha.config.properties.HotKeyProperties;
import com.hzp.miaosha.domain.po.ProductPo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测试数据工厂。
 *
 * @author hzp
 */
public final class TestDataFactory {

    private TestDataFactory() {
    }

    /**
     * 构造默认热 Key 配置（测试用，窗口缩短为 2 秒以便快速验证）。
     *
     * @return 配置
     */
    public static HotKeyProperties hotKeyProperties() {
        return hotKeyProperties(50);
    }

    /**
     * 构造指定热 Key 阈值的配置（压测场景可设 0 让首个请求即为热 Key）。
     *
     * @param hotQpsThreshold 热 Key 阈值
     * @return 配置
     */
    public static HotKeyProperties hotKeyProperties(int hotQpsThreshold) {
        HotKeyProperties properties = new HotKeyProperties();
        properties.setWindowSeconds(2);
        properties.setHotQpsThreshold(hotQpsThreshold);
        properties.setRedQpsThreshold(Math.max(200, hotQpsThreshold));
        properties.setLocalCacheExpireSeconds(30L);
        properties.setReplicaCount(8);
        properties.setProductCacheSeconds(600L);
        return properties;
    }

    /**
     * 构造商品。
     *
     * @param id 商品ID
     * @return 商品
     */
    public static ProductPo product(Long id) {
        ProductPo po = new ProductPo();
        po.setId(id);
        po.setProductNo("SP" + id);
        po.setProductName("TestProduct-" + id);
        po.setCategoryId(5L);
        po.setTitle("TestTitle");
        po.setPrice(new BigDecimal("7999.00"));
        po.setOriginPrice(new BigDecimal("8999.00"));
        po.setStock(100);
        po.setSoldCount(0);
        po.setStatus(1);
        po.setIsHot(1);
        po.setVersion(0);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        po.setDeleted(0);
        return po;
    }
}
