package org.cloudback.common.result;

import lombok.Getter;

/**
 * 统一响应码枚举，定义系统所有错误码和提示信息。
 * 分类: 1xxx 认证, 2xxx 商品, 3xxx 订单, 4xxx 支付
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Getter
public enum ResultCode {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),
    /** 操作失败（通用） */
    FAIL(500, "操作失败"),

    /** 未登录或 Token 已过期 */
    UNAUTHORIZED(401, "未登录或Token已过期"),
    /** 无访问权限 */
    FORBIDDEN(403, "没有访问权限"),
    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 参数校验失败 */
    PARAM_ERROR(400, "参数错误"),
    /** 用户名或密码错误 */
    USERNAME_OR_PASSWORD_ERROR(1001, "用户名或密码错误"),
    /** 用户不存在 */
    USER_NOT_EXIST(1002, "用户不存在"),
    /** 用户已存在 */
    USER_ALREADY_EXIST(1003, "用户已存在"),
    /** Token 已过期 */
    TOKEN_EXPIRED(1004, "Token已过期"),
    /** Token 无效 */
    TOKEN_INVALID(1005, "Token无效"),

    /** 商品不存在 */
    PRODUCT_NOT_EXIST(2001, "商品不存在"),
    /** 库存不足 */
    STOCK_INSUFFICIENT(2002, "库存不足"),

    /** 仅卖家可操作 */
    SELLER_ONLY(1006, "仅卖家可执行此操作"),
    /** 仅管理员可操作 */
    ADMIN_ONLY(1007, "仅管理员可执行此操作"),
    /** 只能操作自己的商品 */
    NOT_YOUR_PRODUCT(1008, "只能操作自己的商品"),

    /** 订单不存在 */
    ORDER_NOT_EXIST(3001, "订单不存在"),
    /** 订单状态异常 */
    ORDER_STATUS_ERROR(3002, "订单状态异常"),

    /** 支付失败 */
    PAYMENT_ERROR(4001, "支付失败"),

    /** 服务不可用（熔断降级） */
    SERVICE_UNAVAILABLE(503, "服务暂时不可用，请稍后重试");


    /** 错误码 */
    private final Integer code;
    /** 错误信息 */
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
