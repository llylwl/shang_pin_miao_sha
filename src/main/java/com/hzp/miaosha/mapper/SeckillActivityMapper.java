package com.hzp.miaosha.mapper;

import com.hzp.miaosha.domain.po.SeckillActivityPo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀活动 Mapper。
 *
 * @author hzp
 */
public interface SeckillActivityMapper {

    /**
     * 按主键查询活动。
     *
     * @param id 活动ID
     * @return 活动；不存在返回 null
     */
    SeckillActivityPo selectById(@Param("id") Long id);

    /**
     * 查询所有活动（含已结束/已取消，便于演示过期数据）。
     *
     * @return 活动列表
     */
    List<SeckillActivityPo> selectAll();

    /**
     * 扣减活动剩余库存（带库存校验，防负数）。
     *
     * @param id        活动ID
     * @param quantity  扣减数量
     * @return 影响行数
     */
    int decreaseRemainStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
