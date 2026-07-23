package com.workflow.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果
 */
@Data
public class Result<T> implements Serializable {
    
    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;
    
    /**
     * 响应码
     */
    private int code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 默认构造方法。
     */
    public Result() {
    }

    /**
     * 全参构造方法。
     *
     * @param code    响应码
     * @param message 响应消息
     * @param data    响应数据
     */
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    /**
     * 成功响应
     *
     * @return 不携带数据的成功结果
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @return 携带数据的成功结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 成功响应（带消息和数据）
     *
     * @param message 响应消息
     * @param data    响应数据
     * @return 携带自定义消息和数据的成功结果
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /**
     * 失败响应
     *
     * @param message 失败消息
     * @return 默认 500 状态码的失败结果
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 失败响应（带状态码）
     *
     * @param code    失败状态码
     * @param message 失败消息
     * @return 携带指定状态码的失败结果
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 参数错误
     *
     * @param message 错误消息
     * @return 400 状态码的失败结果
     */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /**
     * 未授权
     *
     * @param message 错误消息
     * @return 401 状态码的失败结果
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /**
     * 禁止访问
     *
     * @param message 错误消息
     * @return 403 状态码的失败结果
     */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /**
     * 资源不存在
     *
     * @param message 错误消息
     * @return 404 状态码的失败结果
     */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }
}
