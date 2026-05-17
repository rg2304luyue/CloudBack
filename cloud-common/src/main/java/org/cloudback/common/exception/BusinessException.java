package org.cloudback.common.exception;

import lombok.Getter;
import org.cloudback.common.result.ResultCode;

/**
 * 业务异常，抛出后由 {@link GlobalExceptionHandler} 统一捕获处理。
 * 支持自定义错误码或使用预定义枚举。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final Integer code;

    /** 使用默认错误码 */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAIL.getCode();
    }

    /** 自定义错误码和信息 */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /** 使用预定义错误码枚举 */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}
