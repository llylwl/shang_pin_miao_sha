package com.hzp.miaosha.mapper;

import com.hzp.miaosha.domain.po.SeckillOrderPo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀订单 Mapper。
 *
 * @author hzp
 */
public interface SeckillOrderMapper {

    /**
     * 插入订单。
     *
     * @param order 订单
     * @return 影响行数
     */
    int insert(SeckillOrderPo order);

    /**
     * 统计用户在某活动的有效订单数（含待支付，不含已取消）。
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 订单数
     */
    int countValidOrder(@Param("activityId") Long activityId, @Param("userId") Long userId);

    /**
     * 按活动统计订单数量。
     *
     * @param activityId 活动ID
     * @return 数量
     */
    int countByActivityId(@Param("activityId") Long activityId);

    /**
     * 查询用户订单。
     *
     * @param userId 用户ID
     * @param limit  最大条数
     * @return 订单列表
     */
    List<SeckillOrderPo> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}
