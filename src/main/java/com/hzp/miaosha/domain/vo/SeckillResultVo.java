package com.hzp.miaosha.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀下单结果。
 *
 * @author hzp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否抢购成功。
     */
    private Boolean success;

    /**
     * 订单号（成功时返回）。
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
     * 本次扣减使用的库存来源：LOCAL-本地配额 / REDIS-回源 Redis。
     */
    private String stockSource;

    /**
     * 提示信息。
     */
    private String message;

    /**
     * 商品名称。
     */
    private String productName;

    /**
     * 订单金额。
     */
    private String amount;
}
