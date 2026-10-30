package com.hzp.miaosha.controller;

import com.hzp.miaosha.common.constant.RedisKeyConstants;
import com.hzp.miaosha.common.result.Result;
import com.hzp.miaosha.common.result.ResultCode;
import com.hzp.miaosha.domain.dto.SeckillRequest;
import com.hzp.miaosha.domain.po.ProductPo;
import com.hzp.miaosha.domain.po.SeckillActivityPo;
import com.hzp.miaosha.domain.vo.SeckillResultVo;
import com.hzp.miaosha.mapper.SeckillActivityMapper;
import com.hzp.miaosha.service.seckill.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 秒杀接口。
 *
 * @author hzp
 */
@Slf4j
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    private final SeckillActivityMapper seckillActivityMapper;

    /**
     * 执行秒杀。
     *
     * @param request 请求
     * @return 秒杀结果
     */
    @PostMapping("/order")
    public Result<SeckillResultVo> order(@Valid @RequestBody SeckillRequest request) {
        SeckillActivityPo activityPo = seckillActivityMapper.selectById(request.getActivityId());
        if (Objects.isNull(activityPo)) {
            return Result.fail(ResultCode.ACTIVITY_CANCELED, "活动不存在");
        }
        List<ProductPo> products = seckillService.listActivityProducts(request.getActivityId());
        if (products.isEmpty()) {
            return Result.fail(ResultCode.PRODUCT_NOT_FOUND, "活动未关联任何商品");
        }
        Long productId = products.get(0).getId();
        SeckillResultVo resultVo = seckillService.execute(request, productId, activityPo);
        return Result.success(resultVo);
    }

    /**
     * 初始化活动库存（把 DB 剩余库存写入 Redis，并清空本地配额）。
     *
     * @param activityId 活动ID
     * @return 操作结果
     */
    @PostMapping("/activity/{activityId}/init-stock")
    public Result<Map<String, Object>> initStock(@PathVariable("activityId") Long activityId) {
        SeckillActivityPo activityPo = seckillActivityMapper.selectById(activityId);
        if (Objects.isNull(activityPo)) {
            return Result.fail(ResultCode.ACTIVITY_CANCELED, "活动不存在");
        }
        boolean created;
        try {
            created = seckillService.initActivityStock(activityPo);
        } catch (RuntimeException ex) {
            return redisUnavailable(ex);
        }
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("activityId", activityId);
        data.put("created", created);
        data.put("dbRemainStock", activityPo.getRemainStock());
        return Result.success(data);
    }

    /**
     * 查询库存视图（Redis 库存 vs 本地配额 vs DB 库存）。
     *
     * @param activityId 活动ID
     * @return 库存信息
     */
    @GetMapping("/activity/{activityId}/stock")
    public Result<Map<String, Object>> stock(@PathVariable("activityId") Long activityId) {
        SeckillActivityPo activityPo = seckillActivityMapper.selectById(activityId);
        if (Objects.isNull(activityPo)) {
            return Result.fail(ResultCode.ACTIVITY_CANCELED, "活动不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("activityId", activityId);
        data.put("activityName", activityPo.getActivityName());
        data.put("dbRemainStock", activityPo.getRemainStock());
        try {
            data.put("redisStock", seckillService.redisStock(activityId));
        } catch (RuntimeException ex) {
            // Redis 不可用：明确返回降级提示，而不是 500
            data.put("redisStock", null);
            data.put("localQuota", seckillService.localStock(activityId));
            data.put("degraded", Boolean.TRUE);
            log.warn("查询 Redis 库存失败（降级），activityId={}, 原因={}", activityId, ex.getMessage());
            return Result.fail(ResultCode.DEGRADED);
        }
        data.put("localQuota", seckillService.localStock(activityId));
        data.put("degraded", Boolean.FALSE);
        return Result.success(data);
    }

    /**
     * 从 Redis 主动领取一批配额到本实例内存。
     *
     * @param activityId 活动ID
     * @return 库存信息
     */
    @PostMapping("/activity/{activityId}/refill")
    public Result<Map<String, Object>> refill(@PathVariable("activityId") Long activityId) {
        String stockKey = RedisKeyConstants.STOCK_PREFIX + activityId;
        boolean success;
        try {
            success = seckillService.refillFromRedis(stockKey);
        } catch (RuntimeException ex) {
            return redisUnavailable(ex);
        }
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("activityId", activityId);
        data.put("success", success);
        data.put("localQuota", seckillService.localStock(activityId));
        return Result.success(data);
    }

    /**
     * 归还本地配额到 Redis（实例下线 / 活动结束）。
     *
     * @return 操作结果
     */
    @PostMapping("/stock/release")
    public Result<String> release() {
        seckillService.releaseLocalStock();
        return Result.success("已归还本地配额");
    }

    private static <T> Result<T> redisUnavailable(RuntimeException ex) {
        log.warn("Redis 不可用，秒杀库存相关操作已降级：{}", ex.getMessage());
        return Result.fail(ResultCode.DEGRADED);
    }
}
