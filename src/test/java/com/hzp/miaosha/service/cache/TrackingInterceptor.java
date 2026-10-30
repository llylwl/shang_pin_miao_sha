package com.hzp.miaosha.service.cache;

import com.hzp.miaosha.common.annotation.RedisHotKeyTrack;
import com.hzp.miaosha.service.hotkey.HotKeyCollector;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * 测试用：把 {@link RedisHotKeyTrack} 的语义用最简拦截器复现，
 * 避免在单元测试中依赖完整的 AspectJ 织入环境。
 *
 * @author hzp
 */
public final class TrackingInterceptor implements MethodInterceptor {

    private final HotKeyCollector collector;

    public TrackingInterceptor(HotKeyCollector collector) {
        this.collector = collector;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        RedisHotKeyTrack track = invocation.getMethod().getAnnotation(RedisHotKeyTrack.class);
        if (track != null) {
            Object[] args = invocation.getArguments();
            if (args.length > 0 && args[0] != null) {
                String key = track.keyPrefix().isEmpty() ? String.valueOf(args[0]) : track.keyPrefix() + args[0];
                collector.record(key);
            }
        }
        return invocation.proceed();
    }
}
