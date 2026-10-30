package com.hzp.miaosha.common.aspect;

import com.hzp.miaosha.common.annotation.RedisHotKeyTrack;
import com.hzp.miaosha.service.hotkey.HotKeyCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

/**
 * 热 Key 统计切面。
 * <p>
 * 配合 {@link RedisHotKeyTrack} 使用：在被注解的方法上自动记录「本次访问了哪个 Redis Key」，
 * 实现客户端侧热 Key 监控的通用能力，业务代码只需加注解，不需要手动调用计数器。
 * </p>
 * <p>
 * 约定：方法第 1 个参数为 Redis Key 或可推导 Key 的 ID（由 {@code keyFromId} 决定）。
 * </p>
 *
 * @author hzp
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RedisHotKeyTrackAspect {

    private final HotKeyCollector hotKeyCollector;

    /**
     * 环绕通知：执行前打点，不改变返回结果，异常照常抛出。
     *
     * @param joinPoint 连接点
     * @param track     注解
     * @return 方法返回值
     * @throws Throwable 业务异常
     */
    @Around("@annotation(track)")
    public Object around(ProceedingJoinPoint joinPoint, RedisHotKeyTrack track) throws Throwable {
        try {
            String key = resolveKey(joinPoint, track);
            hotKeyCollector.record(key);
        } catch (RuntimeException ex) {
            // 监控失败绝不能影响主流程
            log.debug("热 Key 打点失败，biz={}, 原因={}", track.value(), ex.getMessage());
        }
        return joinPoint.proceed();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RedisHotKeyTrack track) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        String prefix = track.keyPrefix();
        String value = String.valueOf(args[0]);
        if (prefix.isEmpty()) {
            return value;
        }
        return prefix + value;
    }
}
