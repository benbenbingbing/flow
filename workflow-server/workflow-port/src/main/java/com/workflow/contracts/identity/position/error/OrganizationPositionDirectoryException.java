package com.workflow.contracts.identity.position.error;

/**
 * 组织职务目录的结构化异常，流程运行时将其稳定码写入待处置事件。
 */
public class OrganizationPositionDirectoryException extends RuntimeException {

    private final OrganizationPositionErrorCode errorCode;

    /**
     * 初始化组织位置目录异常，保存构造参数供后续方法使用。
     *
     * @param errorCode 错误编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public OrganizationPositionDirectoryException(
            OrganizationPositionErrorCode errorCode,
            String message) {
        super(message);
        if (errorCode == null) {
            throw new IllegalArgumentException("组织职务目录错误码不能为空");
        }
        this.errorCode = errorCode;
    }

    /**
     * 初始化组织位置目录异常，保存构造参数供后续方法使用。
     *
     * @param errorCode 错误编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param cause 原因，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public OrganizationPositionDirectoryException(
            OrganizationPositionErrorCode errorCode,
            String message,
            Throwable cause) {
        super(message, cause);
        if (errorCode == null) {
            throw new IllegalArgumentException("组织职务目录错误码不能为空");
        }
        this.errorCode = errorCode;
    }

    /**
     * 处理错误编码，并将结果传给后续步骤。
     *
     * @return 处理后的错误编码结果，供调用方继续处理
     */
    public OrganizationPositionErrorCode errorCode() {
        return errorCode;
    }
}
