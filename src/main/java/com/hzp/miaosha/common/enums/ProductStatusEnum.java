package com.hzp.miaosha.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 商品状态枚举。
 *
 * @author hzp
 */
@Getter
public enum ProductStatusEnum {

    /**
     * 已下架（逻辑删除/后台下架）。
     */
    OFF_SHELF(0, "已下架"),

    /**
     * 在售。
     */
    ON_SALE(1, "在售"),

    /**
     * 已售罄。
     */
    SOLD_OUT(2, "已售罄"),

    /**
     * 已结束（活动结束）。
     */
    ENDED(3, "已结束");

    private final int code;

    private final String desc;

    ProductStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据 code 查找枚举。
     *
     * @param code 状态码
     * @return 枚举；未匹配返回 null
     */
    public static ProductStatusEnum of(Integer code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否可售。
     *
     * @param code 状态码
     * @return 是否在售
     */
    public static boolean sellable(Integer code) {
        return Objects.equals(ON_SALE.code, code);
    }
}
