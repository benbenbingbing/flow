package com.workflow.admin.auth.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.time.Instant;

/**
 * 登录、刷新和改密后返回的完整浏览器会话令牌。
 */
public record AuthTokenBundle(
        /** 当前用户。 */
        SysUser user,
        /** 用于调用受保护接口的短期 Access Token。 */
        String accessToken,
        /** Access Token 的绝对过期时间。 */
        Instant accessTokenExpiresAt,
        /** 只写入 HttpOnly Cookie 的不透明 Refresh Token。 */
        String refreshToken,
        /** Refresh Token 对应的服务端会话 ID。 */
        String sessionId,
        /** Refresh Session 的绝对过期时间。 */
        Instant sessionAbsoluteExpiresAt) {
}
