package com.hzp.miaosha.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Redis 读操作，用于客户端侧热 Key 统计切面。
 * <p>被标记的方法会自动把「键前缀 + 第 1 个参数」记录到客户端本地计数器
 * （{@code SlidingWindowCounter}），实现离 Key 最近、零网络开销的热 Key 监控。</p>
 *
 * @author hzp
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisHotKeyTrack {

    /**
     * 业务类型，用于告警分类与统计。
     *
     * @return 业务类型
     */
    String value() default "UNKNOWN";

    /**
     * Redis Key 前缀，例如 {@code product:}；为空时直接把第 1 个参数当作完整 Key。
     *
     * @return Key 前缀
     */
    String keyPrefix() default "";
}
