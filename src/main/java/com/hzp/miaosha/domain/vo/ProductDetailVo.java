package com.hzp.miaosha.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品详情视图对象（同时作为 Redis 缓存序列化对象）。
 * <p>
 * 注意：必须同时具备「无参构造 + 全参构造 + Builder」，
 * 否则 Jackson 反序列化会报
 * {@code no Creators, like default constructor, exist}，
 * 导致缓存回源后读到 null（本项目在单元测试中发现并修复了该问题）。
 * </p>
 *
 * @author hzp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品ID。
     */
    private Long productId;

    /**
     * 商品编号。
     */
    private String productNo;

    /**
     * 商品名称。
     */
    private String productName;

    /**
     * 营销文案。
     */
    private String title;

    /**
     * 售价。
     */
    private String price;

    /**
     * 原价。
     */
    private String originPrice;

    /**
     * 可售库存。
     */
    private Integer stock;

    /**
     * 状态：0-已下架 1-在售 2-已售罄 3-已结束。
     */
    private Integer status;

    /**
     * 是否热点商品。
     */
    private Boolean hot;

    /**
     * 命中的数据来源：LOCAL / REDIS / REDIS_REPLICA / DB / LOCAL_DEGRADED / DB_DEGRADED。
     */
    private String source;

    /**
     * 是否处于降级模式（Redis 不可用）。
     */
    private Boolean degraded;
}
