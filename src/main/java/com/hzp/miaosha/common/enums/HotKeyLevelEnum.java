package com.hzp.miaosha.common.enums;

import lombok.Getter;

/**
 * 热 Key 告警级别。
 *
 * @author hzp
 */
@Getter
public enum HotKeyLevelEnum {

    /**
     * 正常，无需处理。
     */
    NORMAL("正常"),

    /**
     * 关注：已进入本地缓存。
     */
    YELLOW("关注"),

    /**
     * 告警：需要打散 + 本地缓存。
     */
    RED("告警");

    private final String desc;

    HotKeyLevelEnum(String desc) {
        this.desc = desc;
    }
}
