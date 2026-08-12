package com.workflow.admin.auth.application;

/**
 * 浏览器认证流程使用的稳定错误编码。
 */
public final class AuthErrorCode {

    /** Access Token 已过期，客户端可以尝试刷新。 */
    public static final String ACCESS_EXPIRED = "AUTH_ACCESS_EXPIRED";
    /** Access Token 缺失、格式错误或签名无效。 */
    public static final String ACCESS_INVALID = "AUTH_ACCESS_INVALID";
    /** Access Token 关联的刷新会话已撤销或不存在。 */
    public static final String SESSION_REVOKED = "AUTH_SESSION_REVOKED";
    /** 用户账号不存在或已禁用。 */
    public static final String ACCOUNT_DISABLED = "AUTH_ACCOUNT_DISABLED";
    /** Refresh Token Cookie 不存在。 */
    public static final String REFRESH_MISSING = "AUTH_REFRESH_MISSING";
    /** Refresh Token 不合法或找不到对应会话。 */
    public static final String REFRESH_INVALID = "AUTH_REFRESH_INVALID";
    /** 刷新会话超过空闲期限。 */
    public static final String REFRESH_IDLE_EXPIRED = "AUTH_REFRESH_IDLE_EXPIRED";
    /** 刷新会话超过绝对期限。 */
    public static final String REFRESH_ABSOLUTE_EXPIRED =
            "AUTH_REFRESH_ABSOLUTE_EXPIRED";

    private AuthErrorCode() {
    }
}
