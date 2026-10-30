package com.hzp.miaosha.service.seckill;

import com.hzp.miaosha.common.constant.RedisKeyConstants;
import com.hzp.miaosha.common.enums.ActivityStatusEnum;
import com.hzp.miaosha.common.enums.ProductStatusEnum;
import com.hzp.miaosha.common.exception.BizException;
import com.hzp.miaosha.common.result.ResultCode;
import com.hzp.miaosha.domain.dto.SeckillRequest;
import com.hzp.miaosha.domain.po.ProductPo;
import com.hzp.miaosha.domain.po.SeckillActivityPo;
import com.hzp.miaosha.domain.po.SeckillOrderPo;
import com.hzp.miaosha.domain.vo.SeckillResultVo;
import com.hzp.miaosha.mapper.ProductMapper;
import com.hzp.miaosha.mapper.SeckillActivityMapper;
import com.hzp.miaosha.mapper.SeckillOrderMapper;
import com.hzp.miaosha.service.cache.StockCacheService;
import com.hzp.miaosha.service.hotkey.HotKeyCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 秒杀核心服务。
 * <p>
 * 库存处理分两层：
 * <ol>
 *   <li><b>本地配额预扣减</b>（主路径）：每实例从 Redis 领 100 份库存到内存，
 *       抢购时本地 CAS 扣减，Redis QPS 从「每单 1 次」降为「每 100 单 1 次」；</li>
 *   <li><b>Redis Lua 原子扣减</b>（兜底）：本地配额耗尽或回源时，用 Lua 保证「判断 + 扣减」原子性。</li>
 * </ol>
 * 注意：库存 key <b>不能打散</b>，否则多副本独立扣减必然超卖。
 * </p>
 *
 * @author hzp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final AtomicLong ORDER_SEQ = new AtomicLong(0L);

    private static final int ORDER_NO_RANDOM_BOUND = 10000;

    private final ProductMapper productMapper;

    private final SeckillActivityMapper seckillActivityMapper;

    private final SeckillOrderMapper seckillOrderMapper;

    private final StockCacheService stockCacheService;

    private final LocalStockManager localStockManager;

    private final HotKeyCollector hotKeyCollector;

    /**
     * 执行秒杀。
     *
     * @param request      请求
     * @param productId    商品ID（由活动关联，这里显式传入便于服务端校验）
     * @param activityPo   活动
     * @return 秒杀结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SeckillResultVo execute(SeckillRequest request, Long productId, SeckillActivityPo activityPo) {
        String activityKey = buildSeckillDedupeKey(request.getActivityId());
        // ① 热 Key 打点：去重 key 也是热点（同一活动所有请求都访问它）
        hotKeyCollector.record(activityKey);
        checkActivity(activityPo);

        // ② 幂等：同一用户同一活动只允许一次
        int exists = seckillOrderMapper.countValidOrder(request.getActivityId(), request.getUserId());
        if (exists > 0) {
            throw BizException.of(ResultCode.REPEAT_SUBMIT.getCode(), ResultCode.REPEAT_SUBMIT.getMessage());
        }

        ProductPo productPo = productMapper.selectById(productId);
        if (Objects.isNull(productPo) || Objects.equals(1, productPo.getDeleted())) {
            throw BizException.of(ResultCode.PRODUCT_NOT_FOUND.getCode(), ResultCode.PRODUCT_NOT_FOUND.getMessage());
        }
        if (!ProductStatusEnum.sellable(productPo.getStatus())) {
            throw BizException.of(ResultCode.PRODUCT_OFF_SHELF.getCode(),
                    "商品当前状态为「" + ProductStatusEnum.of(productPo.getStatus()).getDesc() + "」，无法抢购");
        }

        // ③ 本地配额扣减（主路径），配额不足再回源 Redis 领取
        String stockKey = RedisKeyConstants.STOCK_PREFIX + request.getActivityId();
        String stockSource = "LOCAL";
        boolean consumedLocal = localStockManager.tryConsume(stockKey, request.getQuantity());
        if (!consumedLocal) {
            boolean refilled = refillFromRedis(stockKey);
            if (!refilled || !localStockManager.tryConsume(stockKey, request.getQuantity())) {
                throw BizException.of(ResultCode.STOCK_NOT_ENOUGH.getCode(), ResultCode.STOCK_NOT_ENOUGH.getMessage());
            }
            stockSource = "REDIS";
        }

        // ④ 落库：扣减活动库存 + 写订单
        int activityUpdated = seckillActivityMapper.decreaseRemainStock(request.getActivityId(), request.getQuantity());
        if (activityUpdated <= 0) {
            // 活动库存已不足：归还本地配额并抛错，触发事务回滚
            localStockManager.giveBack(stockKey, request.getQuantity());
            throw BizException.of(ResultCode.STOCK_NOT_ENOUGH.getCode(), ResultCode.STOCK_NOT_ENOUGH.getMessage());
        }

        BigDecimal amount = productPo.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        SeckillOrderPo order = new SeckillOrderPo();
        order.setOrderNo(generateOrderNo());
        order.setActivityId(request.getActivityId());
        order.setProductId(productId);
        order.setUserId(request.getUserId());
        order.setQuantity(request.getQuantity());
        order.setAmount(amount);
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        seckillOrderMapper.insert(order);

        log.info("秒杀成功，orderNo={}, userId={}, activityId={}, stockSource={}",
                order.getOrderNo(), request.getUserId(), request.getActivityId(), stockSource);
        return SeckillResultVo.builder()
                .success(Boolean.TRUE)
                .orderNo(order.getOrderNo())
                .activityId(request.getActivityId())
                .productId(productId)
                .productName(productPo.getProductName())
                .amount(amount.toPlainString())
                .stockSource(stockSource)
                .message("抢购成功，请尽快支付")
                .build();
    }

    /**
     * 查询活动内商品信息。
     *
     * @param activityId 活动ID
     * @return 商品列表
     */
    public List<ProductPo> listActivityProducts(Long activityId) {
        return productMapper.selectByActivityId(activityId);
    }

    /**
     * 从 Redis 领取一批库存到本地。
     *
     * @param stockKey 库存 key
     * @return 是否领取成功
     */
    public boolean refillFromRedis(String stockKey) {
        if (!localStockManager.allowRefill()) {
            return false;
        }
        if (localStockManager.available(stockKey) >= localStockManager.maxLocalStock()) {
            log.warn("本地配额已达上限，拒绝继续领取，stockKey={}", stockKey);
            return false;
        }
        int batchSize = localStockManager.batchSize();
        try {
            long fetched = stockCacheService.fetchBatch(stockKey, batchSize);
            if (fetched <= 0L) {
                log.info("Redis 库存已耗尽，stockKey={}", stockKey);
                return false;
            }
            localStockManager.deposit(stockKey, (int) fetched);
            log.info("从 Redis 领取库存，stockKey={}, fetched={}, localAvailable={}",
                    stockKey, fetched, localStockManager.available(stockKey));
            return true;
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis 不可用，无法领取库存，stockKey={}", stockKey);
            return false;
        }
    }

    /**
     * 初始化活动库存到 Redis（活动预热）。
     *
     * @param activityPo 活动
     * @return 是否初始化成功
     */
    public boolean initActivityStock(SeckillActivityPo activityPo) {
        String stockKey = RedisKeyConstants.STOCK_PREFIX + activityPo.getId();
        boolean created = stockCacheService.initStockIfAbsent(stockKey, activityPo.getRemainStock());
        log.info("初始化活动库存，stockKey={}, stock={}, created={}",
                stockKey, activityPo.getRemainStock(), created);
        return created;
    }

    /**
     * 查询 Redis 中的剩余库存。
     *
     * @param activityId 活动ID
     * @return 库存
     */
    public long redisStock(Long activityId) {
        return stockCacheService.getStock(RedisKeyConstants.STOCK_PREFIX + activityId);
    }

    /**
     * 查询本地配额。
     *
     * @param activityId 活动ID
     * @return 本地配额
     */
    public int localStock(Long activityId) {
        return localStockManager.available(RedisKeyConstants.STOCK_PREFIX + activityId);
    }

    /**
     * 归还本地配额到 Redis（优雅下线）。
     */
    public void releaseLocalStock() {
        localStockManager.releaseAll().forEach((stockKey, quantity) -> {
            try {
                long remain = stockCacheService.giveBack(stockKey, quantity);
                log.info("归还本地配额，stockKey={}, quantity={}, redisRemain={}", stockKey, quantity, remain);
            } catch (RedisConnectionFailureException ex) {
                log.error("归还算力失败，stockKey={}, quantity={}", stockKey, quantity, ex);
            }
        });
    }

    /**
     * 校验活动状态。
     *
     * @param activityPo 活动
     */
    private void checkActivity(SeckillActivityPo activityPo) {
        if (Objects.isNull(activityPo)) {
            throw BizException.of(ResultCode.ACTIVITY_CANCELED.getCode(), "活动不存在");
        }
        ActivityStatusEnum status = ActivityStatusEnum.of(activityPo.getStatus());
        if (ActivityStatusEnum.CANCELED == status) {
            throw BizException.of(ResultCode.ACTIVITY_CANCELED.getCode(), ResultCode.ACTIVITY_CANCELED.getMessage());
        }
        if (ActivityStatusEnum.ENDED == status) {
            throw BizException.of(ResultCode.ACTIVITY_ENDED.getCode(), ResultCode.ACTIVITY_ENDED.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activityPo.getStartTime())) {
            throw BizException.of(ResultCode.ACTIVITY_NOT_STARTED.getCode(), ResultCode.ACTIVITY_NOT_STARTED.getMessage());
        }
        if (now.isAfter(activityPo.getEndTime())) {
            throw BizException.of(ResultCode.ACTIVITY_ENDED.getCode(), ResultCode.ACTIVITY_ENDED.getMessage());
        }
    }

    /**
     * 生成订单号：时间戳 + 线程号 + 自增序号 + 随机数。
     *
     * @return 订单号
     */
    private static String generateOrderNo() {
        return "SK" + LocalDateTime.now().format(ORDER_TIME_FORMAT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(ORDER_NO_RANDOM_BOUND))
                + String.format("%06d", ORDER_SEQ.incrementAndGet());
    }

    /**
     * 去重缓存 key。
     *
     * @param activityId 活动ID
     * @return key
     */
    private static String buildSeckillDedupeKey(Long activityId) {
        return RedisKeyConstants.SECKILL_DEDUP_PREFIX + activityId;
    }
}
