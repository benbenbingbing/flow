package com.workflow.dto;

import lombok.Data;

/**
 * 统一API响应包装类
 * 
 * @description 所有API接口返回的统一格式
 *              包含状态码、消息提示和数据内容
 * @param <T> 返回数据的类型
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
public class ApiResponse<T> {

    /**
     * 响应状态码
     * 200: 成功
     * 500: 服务器错误
     * 其他自定义错误码
     */
    private int code;

    /**
     * 响应消息
     * 成功或错误的描述信息
     */
    private String message;

    /**
     * 稳定的业务错误编码，成功响应时为空。
     */
    private String errorCode;

    /**
     * 响应数据
     * 成功时返回的具体数据
     */
    private T data;

    /**
     * 创建成功响应（带数据）
     * 
     * @param data 返回的数据
     * @param <T> 数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    /**
     * 创建成功响应（无数据）
     * 
     * @param <T> 数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    /**
     * 创建错误响应
     * 
     * @param message 错误消息
     * @param <T> 数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }

    /**
     * 创建错误响应（带自定义状态码）
     * 
     * @param code 错误状态码
     * @param message 错误消息
     * @param <T> 数据类型
     * @return ApiResponse对象
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String errorCode, String message) {
        ApiResponse<T> response = error(code, message);
        response.setErrorCode(errorCode);
        return response;
    }
}
