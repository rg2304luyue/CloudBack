package org.cloudback.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "资源不存在"),

    PARAM_ERROR(400, "参数错误"),
    USERNAME_OR_PASSWORD_ERROR(1001, "用户名或密码错误"),
    USER_NOT_EXIST(1002, "用户不存在"),
    USER_ALREADY_EXIST(1003, "用户已存在"),
    TOKEN_EXPIRED(1004, "Token已过期"),
    TOKEN_INVALID(1005, "Token无效"),

    PRODUCT_NOT_EXIST(2001, "商品不存在"),
    STOCK_INSUFFICIENT(2002, "库存不足"),

    ORDER_NOT_EXIST(3001, "订单不存在"),
    ORDER_STATUS_ERROR(3002, "订单状态异常"),

    PAYMENT_ERROR(4001, "支付失败");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
