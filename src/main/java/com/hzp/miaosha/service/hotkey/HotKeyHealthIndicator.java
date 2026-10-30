package com.hzp.miaosha.service.hotkey;

import com.hzp.miaosha.service.cache.LocalProductCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 热 Key 治理健康检查。
 * <p>暴露到 /actuator/health，可被 K8s / 监控平台直接消费。
 * Redis 不可用时服务仍可读本地缓存，因此标记为 OUT_OF_SERVICE 而不是 DOWN。</p>
 *
 * @author hzp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotKeyHealthIndicator implements HealthIndicator {

    private final HotKeyCollector hotKeyCollector;

    private final LocalProductCache localProductCache;

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>(8);
        details.put("windowSeconds", hotKeyCollector.getWindowSeconds());
        details.put("trackedKeyCount", hotKeyCollector.getTrackedKeyCount());
        details.put("localCacheSize", localProductCache.estimatedSize());
        details.put("localCacheHitRate", localProductCache.stats().hitRate());
        boolean redisOk = pingRedis();
        details.put("redisAvailable", redisOk);
        details.put("degraded", !redisOk);
        if (redisOk) {
            return Health.up().withDetails(details).build();
        }
        // 使用 Spring 内置的 OUT_OF_SERVICE：/actuator/health 仍返回 HTTP 200（服务可用），
        // 但整体 status 明确显示为 OUT_OF_SERVICE，而不是容易误判为"全部正常"的 UP。
        return Health.status(org.springframework.boot.actuate.health.Status.OUT_OF_SERVICE)
                .withDetails(details)
                .build();
    }

    private boolean pingRedis() {
        try {
            redisConnectionFactory.getConnection().ping();
            return true;
        } catch (RuntimeException ex) {
            log.debug("Redis 健康检查失败：{}", ex.getMessage());
            return false;
        }
    }
}
