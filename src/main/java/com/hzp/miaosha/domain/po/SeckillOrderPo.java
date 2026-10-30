package com.hzp.miaosha.domain.po;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单持久化对象（对应表 t_seckill_order）。
 *
 * @author hzp
 */
@Data
public class SeckillOrderPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    private Long id;

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 活动ID。
     */
    private Long activityId;

    /**
     * 商品ID。
     */
    private Long productId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 数量。
     */
    private Integer quantity;

    /**
     * 金额。
     */
    private BigDecimal amount;

    /**
     * 状态：0-待支付 1-已支付 2-已取消 3-已完成。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 支付时间。
     */
    private LocalDateTime payTime;
}
