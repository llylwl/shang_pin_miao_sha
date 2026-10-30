package com.hzp.miaosha.controller;

import com.hzp.miaosha.common.result.Result;
import com.hzp.miaosha.common.result.ResultCode;
import com.hzp.miaosha.common.util.StrUtil;
import com.hzp.miaosha.domain.vo.ProductDetailVo;
import com.hzp.miaosha.service.cache.ProductQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品查询接口。
 * <p>每个接口都会在客户端本地打点，用于热 Key 监控演示。</p>
 *
 * @author hzp
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductQueryService productQueryService;

    /**
     * 商品详情（热 Key 治理后的标准读路径）。
     *
     * @param productId 商品ID
     * @return 商品详情
     */
    @GetMapping("/{productId}")
    public Result<ProductDetailVo> detail(@PathVariable("productId") @Min(value = 1, message = "商品ID不合法") Long productId) {
        ProductDetailVo detailVo = productQueryService.queryDetail(productId);
        if (detailVo == null) {
            return Result.fail(ResultCode.PRODUCT_NOT_FOUND);
        }
        return Result.success(detailVo);
    }

    /**
     * 批量查询（模拟首页/榜单读热点）。
     *
     * @param productIds 商品ID，逗号分隔
     * @return 商品列表
     */
    @GetMapping("/batch")
    public Result<List<ProductDetailVo>> batch(String productIds) {
        if (StrUtil.isBlank(productIds)) {
            return Result.fail(ResultCode.PARAM_INVALID, "productIds 不能为空");
        }
        List<ProductDetailVo> list = java.util.Arrays.stream(productIds.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(Long::valueOf)
                .map(productQueryService::queryDetail)
                .filter(java.util.Objects::nonNull)
                .toList();
        return Result.success(list);
    }

    /**
     * 商品更新后清理缓存（演示「主动失效 + Pub/Sub 广播」）。
     *
     * @param productId 商品ID
     * @return 操作结果
     */
    @org.springframework.web.bind.annotation.PostMapping("/{productId}/cache/evict")
    public Result<Map<String, Object>> evict(@PathVariable("productId") @Min(1) Long productId) {
        productQueryService.evict(productId);
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("productId", productId);
        data.put("evicted", Boolean.TRUE);
        return Result.success(data);
    }

    /**
     * 预热商品到 Redis 与本地缓存。
     *
     * @param productId 商品ID
     * @return 操作结果
     */
    @org.springframework.web.bind.annotation.PostMapping("/{productId}/warm-up")
    public Result<Map<String, Object>> warmUp(@PathVariable("productId") @Min(1) Long productId) {
        boolean success = productQueryService.warmUp(productId);
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("productId", productId);
        data.put("success", success);
        return success ? Result.success(data) : Result.fail(ResultCode.PRODUCT_NOT_FOUND);
    }
}
