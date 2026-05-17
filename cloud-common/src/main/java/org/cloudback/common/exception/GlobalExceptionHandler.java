package org.cloudback.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，拦截所有 Controller 层抛出的异常并返回统一响应格式。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常 */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 处理参数校验异常 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("参数校验异常: {}", message);
        return R.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /** 兜底处理未知系统异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统内部错误，请稍后重试");
    }
}
