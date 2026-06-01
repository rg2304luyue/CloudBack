package org.cloudback.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Feign 调用异常处理器，仅在 Feign 在 classpath 时加载。
 * 独立于 GlobalExceptionHandler，避免无 Feign 依赖的模块（gateway/payment）启动失败。
 *
 * @author CloudBack
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnClass(name = "feign.FeignException")
public class FeignExceptionHandler {

    /** 处理 Feign 远程调用失败（熔断降级） */
    @ExceptionHandler(feign.FeignException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public R<Void> handleFeignException(feign.FeignException e) {
        log.error("Feign 调用失败: {}", e.getMessage());
        return R.fail(ResultCode.SERVICE_UNAVAILABLE);
    }
}
