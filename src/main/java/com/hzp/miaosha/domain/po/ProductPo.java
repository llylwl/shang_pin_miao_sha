package com.hzp.miaosha.domain.po;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品持久化对象（对应表 t_product）。
 *
 * @author hzp
 */
@Data
public class ProductPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品主键。
     */
    private Long id;

    /**
     * 商品编号。
     */
    private String productNo;

    /**
     * 商品名称。
     */
    private String productName;

    /**
     * 类目ID。
     */
    private Long categoryId;

    /**
     * 营销文案。
     */
    private String title;

    /**
     * 主图地址。
     */
    private String mainImage;

    /**
     * 售价。
     */
    private BigDecimal price;

    /**
     * 原价。
     */
    private BigDecimal originPrice;

    /**
     * 可售库存。
     */
    private Integer stock;

    /**
     * 已售数量。
     */
    private Integer soldCount;

    /**
     * 商品状态：0-已下架 1-在售 2-已售罄 3-已结束。
     */
    private Integer status;

    /**
     * 是否热点商品。
     */
    private Integer isHot;

    /**
     * 乐观锁版本号。
     */
    private Integer version;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记。
     */
    private Integer deleted;
}
