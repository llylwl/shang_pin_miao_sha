package com.hzp.miaosha.controller;

import com.hzp.miaosha.common.result.Result;
import com.hzp.miaosha.service.hotkey.HotKeyBenchmarkService;
import com.hzp.miaosha.service.hotkey.HotKeyReporter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 热 Key 监控与演示接口。
 *
 * @author hzp
 */
@RestController
@RequestMapping("/api/hotkey")
@RequiredArgsConstructor
public class HotKeyController {

    private static final int MAX_TOP_N = 200;

    private final com.hzp.miaosha.service.hotkey.HotKeyCollector hotKeyCollector;

    private final HotKeyReporter hotKeyReporter;

    private final HotKeyBenchmarkService hotKeyBenchmarkService;

    /**
     * 查看当前 TopN 热 Key。
     *
     * @param topN 条数
     * @return 热 Key 列表
     */
    @GetMapping("/top")
    public Result<List<HotKeyReporter.HotKeySnapshot>> top(
            @RequestParam(value = "topN", defaultValue = "10") int topN) {
        int size = Math.min(Math.max(topN, 1), MAX_TOP_N);
        return Result.success(hotKeyReporter.currentTopN(size));
    }

    /**
     * 监控概览：窗口配置、跟踪 key 数、最近一次告警。
     *
     * @return 概览
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>(16);
        data.put("windowSeconds", hotKeyCollector.getWindowSeconds());
        data.put("trackedKeyCount", hotKeyCollector.getTrackedKeyCount());
        data.put("hotQpsThreshold", hotKeyCollector.getProperties().getHotQpsThreshold());
        data.put("redQpsThreshold", hotKeyCollector.getProperties().getRedQpsThreshold());
        data.put("replicaCount", hotKeyCollector.getProperties().getReplicaCount());
        data.put("localCacheEnabled", hotKeyCollector.getProperties().isAutoLocalCacheEnabled());
        data.put("lastSnapshot", hotKeyReporter.lastSnapshot());
        return Result.success(data);
    }

    /**
     * 制造热 Key：对指定商品发起 N 次读请求，用于演示「客户端监控 -> 热 Key 识别」。
     *
     * @param productId 商品ID
     * @param count     次数
     * @return 压测结果
     */
    @PostMapping("/benchmark/{productId}")
    public ResponseEntity<Result<HotKeyBenchmarkService.BenchmarkResult>> benchmark(
            @PathVariable("productId") Long productId,
            @RequestParam(value = "count", defaultValue = "10000") int count) {
        HotKeyBenchmarkService.BenchmarkResult result;
        try {
            result = hotKeyBenchmarkService.benchmark(productId, count);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Result.fail(400, ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.OK).body(Result.success(result));
    }
}
