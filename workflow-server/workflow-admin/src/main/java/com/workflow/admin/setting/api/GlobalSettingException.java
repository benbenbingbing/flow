package com.workflow.admin.setting.api;

/** 设置校验或并发失败，提供前端可识别的 HTTP 状态及稳定错误码。 */
public class GlobalSettingException extends RuntimeException {
    private final int status;
    private final String errorCode;

    /**
     * 初始化全局设置异常，保存构造参数供后续方法使用。
     *
     * @param status 状态依赖，保存到当前对象供后续业务方法调用
     * @param errorCode 错误编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public GlobalSettingException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @return 处理后的状态结果，供调用方继续处理
     */
    public int status() { return status; }
    /**
     * 生成错误编码文本，供后续匹配或展示。
     *
     * @return 处理后的错误编码文本，供调用方比较或展示
     */
    public String errorCode() { return errorCode; }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param message 消息，作为 {@code GlobalSettingException} 的输入影响后续处理
     * @return 处理后的无效结果，供调用方继续处理
     */
    public static GlobalSettingException invalid(String message) {
        return new GlobalSettingException(400, "SETTING_INVALID", message);
    }

    /**
     * 构造业务冲突异常，供调用方刷新或重试。
     *
     * @return 处理后的冲突结果，供调用方继续处理
     */
    public static GlobalSettingException conflict() {
        return new GlobalSettingException(409, "SETTING_VERSION_CONFLICT", "设置已被其他页面修改，请刷新后重试");
    }
}
