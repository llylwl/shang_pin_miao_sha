package com.hzp.miaosha.controller;

import com.hzp.miaosha.common.result.Result;
import com.hzp.miaosha.domain.po.ProductPo;
import com.hzp.miaosha.domain.po.SeckillActivityPo;
import com.hzp.miaosha.mapper.ProductMapper;
import com.hzp.miaosha.mapper.SeckillActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 演示数据接口：便于在浏览器中直接查看「过去 / 现在 / 未来」三类数据。
 *
 * @author hzp
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoDataController {

    private final ProductMapper productMapper;

    private final SeckillActivityMapper seckillActivityMapper;

    /**
     * 全部商品（含已下架、已结束、已售罄）。
     *
     * @return 商品列表
     */
    @GetMapping("/products")
    public Result<List<ProductPo>> products() {
        return Result.success(productMapper.selectValidList(100));
    }

    /**
     * 全部活动（含已结束、已取消、未开始）。
     *
     * @return 活动列表
     */
    @GetMapping("/activities")
    public Result<List<SeckillActivityPo>> activities() {
        return Result.success(seckillActivityMapper.selectAll());
    }

    /**
     * 活动时间窗口归类：过去 / 现在 / 未来。
     *
     * @return 归类结果
     */
    @GetMapping("/activities/lifecycle")
    public Result<Map<String, List<SeckillActivityPo>>> lifecycle() {
        List<SeckillActivityPo> all = seckillActivityMapper.selectAll();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Map<String, List<SeckillActivityPo>> data = new LinkedHashMap<>(4);
        data.put("past", all.stream().filter(item -> now.isAfter(item.getEndTime())).toList());
        data.put("current", all.stream()
                .filter(item -> !now.isBefore(item.getStartTime()) && !now.isAfter(item.getEndTime()))
                .toList());
        data.put("future", all.stream().filter(item -> now.isBefore(item.getStartTime())).toList());
        return Result.success(data);
    }

    /**
     * 过期数据统计。
     *
     * @return 统计结果
     */
    @GetMapping("/expired-stat")
    public Result<Map<String, Object>> expiredStat() {
        List<ProductPo> products = productMapper.selectValidList(1000);
        List<SeckillActivityPo> activities = seckillActivityMapper.selectAll();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("expiredActivityCount",
                activities.stream().filter(item -> now.isAfter(item.getEndTime())).count());
        data.put("upcomingActivityCount",
                activities.stream().filter(item -> now.isBefore(item.getStartTime())).count());
        data.put("deletedProductCount",
                products.stream().filter(item -> Objects.equals(1, item.getDeleted())).count());
        data.put("endedProductCount",
                products.stream().filter(item -> Objects.equals(3, item.getStatus())).count());
        data.put("soldOutProductCount",
                products.stream().filter(item -> Objects.equals(2, item.getStatus())).count());
        return Result.success(data);
    }
}
