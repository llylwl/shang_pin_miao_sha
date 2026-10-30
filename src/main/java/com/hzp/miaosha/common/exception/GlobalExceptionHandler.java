package com.hzp.miaosha.common.exception;

import com.hzp.miaosha.common.result.Result;
import com.hzp.miaosha.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 全局异常处理。
 *
 * @author hzp
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.info("业务异常，code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.of(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintException(Exception ex) {
        return Result.fail(ResultCode.PARAM_INVALID, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = Objects.isNull(fieldError) ? ResultCode.PARAM_INVALID.getMessage() : fieldError.getDefaultMessage();
        return Result.fail(ResultCode.PARAM_INVALID, message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = Objects.isNull(fieldError) ? ResultCode.PARAM_INVALID.getMessage() : fieldError.getDefaultMessage();
        return Result.fail(ResultCode.PARAM_INVALID, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return Result.fail(ResultCode.PARAM_INVALID, "缺少必填参数：" + ex.getParameterName());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("未预期异常，uri={}", request.getRequestURI(), ex);
        return Result.fail(ResultCode.SYSTEM_BUSY);
    }
}
