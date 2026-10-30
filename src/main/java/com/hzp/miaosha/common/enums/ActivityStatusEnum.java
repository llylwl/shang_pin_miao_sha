package com.hzp.miaosha.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * 秒杀活动状态枚举。
 *
 * @author hzp
 */
@Getter
public enum ActivityStatusEnum {

    /**
     * 未开始。
     */
    NOT_STARTED(0, "未开始"),

    /**
     * 进行中。
     */
    RUNNING(1, "进行中"),

    /**
     * 已结束。
     */
    ENDED(2, "已结束"),

    /**
     * 已取消。
     */
    CANCELED(3, "已取消");

    private final int code;

    private final String desc;

    ActivityStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据 code 查找枚举。
     *
     * @param code 状态码
     * @return 枚举；未匹配返回 null
     */
    public static ActivityStatusEnum of(Integer code) {
        if (Objects.isNull(code)) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(null);
    }
}
