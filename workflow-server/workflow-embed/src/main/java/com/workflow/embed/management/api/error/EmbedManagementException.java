package com.workflow.embed.management.api.error;

/** 管理端稳定业务异常；由局部 Advice 映射为统一错误包络。 */
public class EmbedManagementException extends RuntimeException {

    private final int status;
    private final String errorCode;
    private final Object data;

    /**
     * 初始化嵌入式管理异常，保存构造参数供后续方法使用。
     *
     * @param status 状态标识，决定后续嵌入式管理异常采用的处理分支
     * @param errorCode 错误编码，后续用于初始化嵌入式管理异常时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public EmbedManagementException(int status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    /**
     * 初始化嵌入式管理异常，保存构造参数供后续方法使用。
     *
     * @param status 状态依赖，保存到当前对象供后续业务方法调用
     * @param errorCode 错误编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param data 数据依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedManagementException(
            int status, String errorCode, String message, Object data) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.data = data;
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
     * 生成错误编码文本，供后续匹配或展示。
     *
     * @return 处理后的错误编码文本，供调用方比较或展示
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * 处理数据，并将结果传给后续步骤。
     *
     * @return 处理后的数据结果，供调用方继续处理
     */
    public Object data() {
        return data;
    }
}
