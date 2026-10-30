package com.hzp.miaosha.common.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一返回结构。
 *
 * @param <T> 业务数据类型
 * @author hzp
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功状态码。
     */
    public static final int SUCCESS_CODE = 200;

    /**
     * 业务状态码：200 表示成功。
     */
    private final int code;

    /**
     * 提示信息。
     */
    private final String message;

    /**
     * 业务数据。
     */
    private final transient T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "操作成功", data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> of(int code, String message) {
        return new Result<>(code, message, null);
    }

    public boolean isSuccess() {
        return SUCCESS_CODE == this.code;
    }
}
