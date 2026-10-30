package com.hzp.miaosha.common.util;

import java.util.Objects;

/**
 * 字符串工具类。
 *
 * @author hzp
 */
public final class StrUtil {

    private StrUtil() {
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 字符串
     * @return 是否为 null 或空白
     */
    public static boolean isBlank(String value) {
        return Objects.isNull(value) || value.isEmpty() || value.matches("^\\s+$");
    }

    /**
     * 判断字符串是否非空白。
     *
     * @param value 字符串
     * @return 是否非空白
     */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /**
     * 判空默认值。
     *
     * @param value        值
     * @param defaultValue 默认值
     * @return 原值或默认值
     */
    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    /**
     * 从 Redis Key 中解析商品ID：product:1 -> 1，hot-product:1:3 -> 1。
     *
     * @param redisKey Redis 键
     * @return 商品ID；无法解析返回 null
     */
    public static Long parseProductId(String redisKey) {
        if (isBlank(redisKey)) {
            return null;
        }
        String[] parts = redisKey.split(":");
        for (int i = 1; i < parts.length; i++) {
            try {
                return Long.parseLong(parts[i]);
            } catch (NumberFormatException ignored) {
                // 继续尝试下一段
            }
        }
        return null;
    }
}
