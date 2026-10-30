package com.hzp.miaosha.mapper;

import com.hzp.miaosha.domain.po.ProductPo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品 Mapper。
 *
 * @author hzp
 */
public interface ProductMapper {

    /**
     * 按主键查询商品（含已下架/已结束数据，由业务层判断状态）。
     *
     * @param id 商品ID
     * @return 商品；不存在返回 null
     */
    ProductPo selectById(@Param("id") Long id);

    /**
     * 查询所有未删除商品。
     *
     * @param limit 最大条数
     * @return 商品列表
     */
    List<ProductPo> selectValidList(@Param("limit") int limit);

    /**
     * 查询活动商品（用于预热）。
     *
     * @param activityId 活动ID
     * @return 商品列表
     */
    List<ProductPo> selectByActivityId(@Param("activityId") Long activityId);

    /**
     * 乐观锁扣减库存（仅用于兜底修复，正常秒杀走本地配额 + Redis）。
     *
     * @param id      商品ID
     * @param version 版本号
     * @param count   扣减数量
     * @return 影响行数
     */
    int decreaseStock(@Param("id") Long id, @Param("version") Integer version, @Param("count") Integer count);

    /**
     * 查询即将开始的活动商品ID（预热用）。
     *
     * @param aheadSeconds 提前多少秒
     * @return 商品列表
     */
    List<ProductPo> selectWarmUpProducts(@Param("aheadSeconds") long aheadSeconds);
}
