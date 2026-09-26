package com.workflow.admin.identity.position.api.error;

/**
 * 携带 HTTP 状态与稳定错误码的职务管理异常。
 */
public class PositionManagementException extends RuntimeException {

    private final int status;
    private final PositionErrorCode errorCode;

    /**
     * 初始化位置管理异常，保存构造参数供后续方法使用。
     *
     * @param status 状态依赖，保存到当前对象供后续业务方法调用
     * @param errorCode 错误编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public PositionManagementException(
            int status,
            PositionErrorCode errorCode,
            String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @return 处理后的状态结果，供调用方继续处理
     */
    public int status() {
        return status;
    }

    /**
     * 处理错误编码，并将结果传给后续步骤。
     *
     * @return 处理后的错误编码结果，供调用方继续处理
     */
    public PositionErrorCode errorCode() {
        return errorCode;
    }
}
