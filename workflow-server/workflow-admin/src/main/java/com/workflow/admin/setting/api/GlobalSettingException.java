package com.workflow.admin.setting.api;

/** 设置校验或并发失败，提供前端可识别的 HTTP 状态及稳定错误码。 */
public class GlobalSettingException extends RuntimeException {
    private final int status;
    private final String errorCode;

    public GlobalSettingException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() { return status; }
    public String errorCode() { return errorCode; }

    public static GlobalSettingException invalid(String message) {
        return new GlobalSettingException(400, "SETTING_INVALID", message);
    }

    public static GlobalSettingException conflict() {
        return new GlobalSettingException(409, "SETTING_VERSION_CONFLICT", "设置已被其他页面修改，请刷新后重试");
    }
}
