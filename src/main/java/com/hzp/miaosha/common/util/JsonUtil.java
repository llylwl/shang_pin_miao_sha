package com.hzp.miaosha.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * JSON 工具类。
 * <p>阿里规约：工具类构造器私有；统一使用一个 ObjectMapper 实例（线程安全）。</p>
 *
 * @author hzp
 */
@Slf4j
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {
    }

    /**
     * 序列化。
     *
     * @param obj 对象
     * @return JSON 字符串；失败返回 null
     */
    public static String toJson(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.error("JSON 序列化失败，type={}", obj.getClass().getName(), ex);
            return null;
        }
    }

    /**
     * 反序列化。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   类型
     * @return 对象；失败返回 null
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (Objects.isNull(json) || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException ex) {
            log.warn("JSON 反序列化失败，json={}, class={}", json, clazz.getName());
            return null;
        }
    }
}
