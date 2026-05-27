package org.cloudback.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 统一响应体，所有接口返回此结构。
 * 格式: { "code": 200, "message": "操作成功", "data": {...} }
 *
 * @param <T> data 的类型
 * @author CloudBack
 * @since 2025-05-17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // total 为 null 时不序列化
public class R<T> {

    /** 状态码，200 表示成功 */
    private Integer code;
    /** 提示信息 */
    private String message;
    /** 响应数据 */
    private T data;
    /** 仅分页接口使用 */
    private Integer total;

    /** 成功，无数据返回 */
    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null, null);
    }

    /** 成功，返回数据 */
    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, null);
    }

    /** 成功，自定义提示信息并返回数据 */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), message, data, null);
    }

    /** 成功，返回数据 + 分页总数 */
    public static <T> R<T> ok(T data, Integer total) {
        R<T> r = new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, null);
        r.setTotal(total);
        return r;
    }

    /** 失败，默认错误码 */
    public static <T> R<T> fail() {
        return new R<>(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMessage(), null, null);
    }

    /** 失败，自定义提示信息 */
    public static <T> R<T> fail(String message) {
        return new R<>(ResultCode.FAIL.getCode(), message, null, null);
    }

    /** 失败，自定义错误码和提示信息 */
    public static <T> R<T> fail(Integer code, String message) {
        return new R<>(code, message, null, null);
    }

    /** 失败，使用预定义错误码枚举 */
    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null, null);
    }
}
