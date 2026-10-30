package com.hzp.miaosha.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务错误码。
 * <p>阿里规约：错误码使用枚举，禁止直接返回魔法数字。</p>
 *
 * @author hzp
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /**
     * 成功。
     */
    SUCCESS(200, "操作成功"),

    /**
     * 参数错误。
     */
    PARAM_INVALID(400, "参数不合法"),

    /**
     * 商品不存在。
     */
    PRODUCT_NOT_FOUND(404, "商品不存在"),

    /**
     * 商品已下架。
     */
    PRODUCT_OFF_SHELF(410, "商品已下架"),

    /**
     * 活动未开始。
     */
    ACTIVITY_NOT_STARTED(420, "活动尚未开始"),

    /**
     * 活动已结束。
     */
    ACTIVITY_ENDED(421, "活动已结束"),

    /**
     * 活动已取消。
     */
    ACTIVITY_CANCELED(422, "活动已取消"),

    /**
     * 库存不足。
     */
    STOCK_NOT_ENOUGH(430, "库存不足，手慢啦"),

    /**
     * 重复下单。
     */
    REPEAT_SUBMIT(431, "您已参与过该活动，请勿重复下单"),

    /**
     * 系统繁忙（兜底）。
     */
    SYSTEM_BUSY(500, "系统繁忙，请稍后重试"),

    /**
     * 降级中（Redis 不可用）。
     */
    DEGRADED(503, "当前为降级模式，数据可能存在短暂延迟");

    private final int code;

    private final String message;
}
