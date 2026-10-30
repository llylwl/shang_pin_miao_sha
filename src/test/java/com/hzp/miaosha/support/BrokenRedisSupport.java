package com.hzp.miaosha.support;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 测试辅助：快速拿到「Redis 不可用」形态的模板。
 *
 * @author hzp
 */
public final class BrokenRedisSupport {

    private BrokenRedisSupport() {
    }

    /**
     * 构造所有操作都抛连接异常的 StringRedisTemplate 替身。
     *
     * @return 替身
     */
    public static StringRedisTemplate brokenTemplate() {
        return InMemoryRedis.create().createBrokenTemplate();
    }
}
