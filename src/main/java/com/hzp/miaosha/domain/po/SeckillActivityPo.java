package com.hzp.miaosha.domain.po;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动持久化对象（对应表 t_seckill_activity）。
 *
 * @author hzp
 */
@Data
public class SeckillActivityPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 活动主键。
     */
    private Long id;

    /**
     * 活动编号。
     */
    private String activityNo;

    /**
     * 活动名称。
     */
    private String activityName;

    /**
     * 开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 活动总库存。
     */
    private Integer totalStock;

    /**
     * 活动剩余库存。
     */
    private Integer remainStock;

    /**
     * 状态：0-未开始 1-进行中 2-已结束 3-已取消。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
