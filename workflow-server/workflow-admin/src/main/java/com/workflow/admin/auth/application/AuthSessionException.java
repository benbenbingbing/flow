package com.workflow.admin.auth.application;

/**
 * 携带稳定错误编码的认证会话异常。
 */
public class AuthSessionException extends RuntimeException {

    /** 供客户端判断是否可以刷新或必须重新登录的错误编码。 */
    private final String errorCode;

    /**
     * 初始化认证会话异常，保存构造参数供后续方法使用。
     *
     * @param errorCode 错误编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public AuthSessionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 读取错误编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的错误编码文本，供调用方比较或展示
     */
    public String getErrorCode() {
        return errorCode;
    }
}
