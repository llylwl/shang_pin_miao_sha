package com.hzp.miaosha.service.hotkey;

import com.hzp.miaosha.domain.po.ProductPo;
import com.hzp.miaosha.domain.po.SeckillActivityPo;
import com.hzp.miaosha.mapper.ProductMapper;
import com.hzp.miaosha.mapper.SeckillActivityMapper;
import com.hzp.miaosha.service.cache.ProductQueryService;
import com.hzp.miaosha.service.seckill.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 活动预热：把即将开始 / 正在进行活动中的商品提前加载到 Redis 与本地缓存，
 * 并初始化 Redis 库存（本地配额预扣减的前置条件）。
 *
 * @author hzp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityWarmUpScheduler {

    private final ProductMapper productMapper;

    private final SeckillActivityMapper seckillActivityMapper;

    private final ProductQueryService productQueryService;

    private final SeckillService seckillService;

    private final com.hzp.miaosha.config.properties.SeckillProperties seckillProperties;

    /**
     * 应用启动完成后执行一次。
     * <p>此时 MySQL 已就绪（spring.sql.init 已在启动阶段执行），Redis 若不可用只记录告警，
     * 不影响应用启动——正好演示「Redis 宕机走内存查询」的降级能力。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void onReady() {
        warmUp();
    }

    /**
     * 定时预热（默认 60 秒一次）。
     */
    @Scheduled(fixedDelayString = "${miao-sha.seckill.warm-up-scan-interval-ms:60000}", initialDelay = 60000)
    public void scheduledWarmUp() {
        warmUp();
    }

    /**
     * 预热逻辑：即将开始的活动 + 进行中的活动。
     */
    public void warmUp() {
        long aheadSeconds = seckillProperties.getWarmUpBeforeSeconds();
        try {
            for (SeckillActivityPo activityPo : seckillActivityMapper.selectAll()) {
                boolean running = Objects.equals(1, activityPo.getStatus());
                boolean upcoming = Objects.equals(0, activityPo.getStatus());
                if (!running && !upcoming) {
                    continue;
                }
                try {
                    seckillService.initActivityStock(activityPo);
                    for (ProductPo productPo : productMapper.selectByActivityId(activityPo.getId())) {
                        if (Objects.equals(1, productPo.getDeleted())) {
                            continue;
                        }
                        productQueryService.warmUp(productPo.getId());
                        log.info("活动预热，activityId={}, productId={}", activityPo.getId(), productPo.getId());
                    }
                } catch (RuntimeException ex) {
                    log.warn("活动预热失败，activityId={}, 原因={}", activityPo.getId(), ex.getMessage());
                }
            }
            log.debug("活动预热扫描完成，提前窗口={}s", aheadSeconds);
        } catch (RuntimeException ex) {
            log.error("预热任务执行异常", ex);
        }
    }
}
