package com.hzp.miaosha.service.cache;

import com.hzp.miaosha.common.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 本地缓存失效广播器 / 监听器。
 * <p>
 * 多实例部署时，任一实例更新商品后，通过 Redis Pub/Sub 通知所有实例清空本地缓存，
 * 避免「A 实例已更新、B 实例本地缓存仍是旧值」的数据不一致。
 * </p>
 *
 * @author hzp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidateBroadcaster implements MessageListener {

    private final StringRedisTemplate stringRedisTemplate;

    private final LocalProductCache localProductCache;

    /**
     * 广播失效消息。
     *
     * @param productId 商品ID
     */
    public void broadcast(Long productId) {
        String message = String.valueOf(productId);
        try {
            stringRedisTemplate.convertAndSend(RedisKeyConstants.CACHE_INVALIDATE_CHANNEL, message);
            log.info("广播本地缓存失效，productId={}", productId);
        } catch (RuntimeException ex) {
            // Redis 不可用时只记录日志：本地缓存 TTL 仍是最终兜底
            log.warn("广播本地缓存失效失败，productId={}, 原因={}", productId, ex.getMessage());
        }
    }

    /**
     * 收到广播后清理本实例缓存。
     * <p>注意：Pub/Sub 消息不保证顺序与可达，因此本地缓存仍必须设置 TTL 作为最终兜底。</p>
     *
     * @param message 消息
     * @param pattern 匹配模式
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            Long productId = Long.valueOf(body.trim());
            localProductCache.invalidate(productId);
            log.info("收到失效广播，已清理本地缓存，productId={}", productId);
        } catch (NumberFormatException ex) {
            log.warn("收到非法失效广播消息，已忽略：{}", body);
        }
    }
}
