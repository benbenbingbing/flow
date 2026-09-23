package com.workflow.admin.auth.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.time.Instant;

/**
 * 登录或刷新后返回的完整浏览器会话令牌。
 *
 * @param user 当前登录用户，后续用于构建登录响应和展示身份
 * @param accessToken 短期访问令牌，后续用于调用受保护接口
 * @param accessTokenExpiresAt 访问令牌的绝对过期时间，客户端据此决定何时刷新
 * @param refreshToken 刷新令牌，后续写入 HttpOnly Cookie 以续期会话
 * @param sessionId 服务端会话 ID，后续用于关联刷新令牌和撤销会话
 * @param sessionAbsoluteExpiresAt 会话绝对过期时间，到期后不再允许刷新
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
