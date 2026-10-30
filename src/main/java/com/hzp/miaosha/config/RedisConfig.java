package com.hzp.miaosha.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Redis 配置。
 *
 * @author hzp
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    /**
     * 通用 RedisTemplate：String key + JSON value。
     *
     * @param connectionFactory 连接工厂
     * @param objectMapper      Jackson 实例
     * @return 模板
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        ObjectMapper copy = objectMapper.copy();
        copy.registerModule(new JavaTimeModule());
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(copy);
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 字符串模板：库存、计数器等纯文本场景使用，降低序列化开销。
     *
     * @param connectionFactory 连接工厂
     * @return 模板
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Lettuce 客户端参数：连接超时 + 自动重连 + 空闲保活。
     *
     * @return 定制器
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .pingBeforeActivateConnection(false)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofMillis(1500))
                        .keepAlive(true)
                        .build())
                .build());
    }
}
