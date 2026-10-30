package com.hzp.miaosha.config;

import com.hzp.miaosha.common.constant.RedisKeyConstants;
import com.hzp.miaosha.service.cache.CacheInvalidateBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.Executors;

/**
 * Redis Pub/Sub 监听配置（用于多实例本地缓存失效广播）。
 * <p>
 * 设计要点：该容器是 {@code SmartLifecycle}，默认随应用上下文启动，
 * 会在启动阶段强依赖 Redis 连接；Redis 不可用时会直接导致应用启动失败，
 * 与「Redis 宕机走本地内存」的降级目标矛盾。
 * <p>
 * 因此：
 * <ol>
 *   <li>通过 {@link ConditionalOnProperty} 由配置开关控制，默认关闭；</li>
 *   <li>开启时延迟启动，并在独立线程中 start()，失败只打日志不阻塞启动。</li>
 * </ol>
 * 需要多实例缓存一致性时，设置 {@code miao-sha.redis.pubsub-enabled=true} 并确保 Redis 可用。
 *
 * @author hzp
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "miao-sha.redis.pubsub-enabled", havingValue = "true")
public class RedisPubSubConfig {

    /**
     * 监听容器（lazy：避免启动阶段提前初始化）。
     *
     * @param connectionFactory 连接工厂
     * @return 容器
     */
    @Bean
    @Lazy
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(5_000L);
        container.setTaskExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "redis-pubsub-listener");
            thread.setDaemon(true);
            return thread;
        }));
        return container;
    }

    /**
     * 异步启动监听容器，Redis 不可用时降级为「仅本地缓存」模式。
     *
     * @param container   容器
     * @param broadcaster 广播器（作为监听者）
     * @return 启动器
     */
    @Bean
    public ApplicationRunner redisPubSubStarter(RedisMessageListenerContainer container,
                                                CacheInvalidateBroadcaster broadcaster) {
        return args -> {
            container.addMessageListener(broadcaster,
                    new ChannelTopic(RedisKeyConstants.CACHE_INVALIDATE_CHANNEL));
            Thread starter = new Thread(() -> {
                try {
                    container.start();
                    log.info("Redis Pub/Sub 监听已启动，channel={}", RedisKeyConstants.CACHE_INVALIDATE_CHANNEL);
                } catch (RuntimeException ex) {
                    log.warn("Redis Pub/Sub 监听启动失败（Redis 不可用），多实例本地缓存失效广播已停用：{}",
                            ex.getMessage());
                }
            }, "redis-pubsub-starter");
            starter.setDaemon(true);
            starter.start();
        };
    }
}
