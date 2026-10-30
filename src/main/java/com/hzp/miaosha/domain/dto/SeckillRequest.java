package com.hzp.miaosha.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀下单请求。
 *
 * @author hzp
 */
@Data
public class SeckillRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 活动ID。
     */
    @NotNull(message = "活动ID不能为空")
    @Min(value = 1, message = "活动ID不合法")
    private Long activityId;

    /**
     * 用户ID。
     */
    @NotNull(message = "用户ID不能为空")
    @Min(value = 1, message = "用户ID不合法")
    private Long userId;

    /**
     * 购买数量。
     */
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = 5, message = "单次购买数量过多")
    private Integer quantity = 1;
}
