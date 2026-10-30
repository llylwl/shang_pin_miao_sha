package com.hzp.miaosha.service.seckill;

import com.hzp.miaosha.service.cache.ProductQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * 应用优雅下线：归还本地库存配额到 Redis，避免库存「虚扣」。
 * <p>秒杀场景中，实例进程被杀会丢失本地配额；除了归还，还必须依赖活动结束后的 DB 对账。</p>
 *
 * @author hzp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockGracefulShutdownListener implements ApplicationListener<ContextClosedEvent> {

    private final SeckillService seckillService;

    private final ProductQueryService productQueryService;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("应用即将关闭，归还本地库存配额……");
        try {
            seckillService.releaseLocalStock();
        } catch (RuntimeException ex) {
            log.error("归还本地库存配额失败，请人工对账", ex);
        }
        log.info("关闭前统计：Redis 访问次数={}, 降级次数={}",
                productQueryService.getRedisAccessCount(), productQueryService.getDegradeCount());
    }
}
