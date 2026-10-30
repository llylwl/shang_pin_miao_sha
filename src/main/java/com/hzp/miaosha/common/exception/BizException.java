package com.hzp.miaosha.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常，携带错误码。
 * <p>阿里规约：业务可预期错误由该异常承载，由全局异常处理器统一转换为 HTTP 响应；
 * 不滥用受检异常，也不吞异常。</p>
 *
 * @author hzp
 */
@Getter
public final class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码。
     */
    private final int code;

    private BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常。
     *
     * @param code    错误码
     * @param message 错误信息
     * @return 业务异常
     */
    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // 业务异常属于流程控制，不需要堆栈，减少性能损耗
        return this;
    }
}
