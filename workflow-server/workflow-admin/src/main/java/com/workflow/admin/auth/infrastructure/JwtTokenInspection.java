package com.workflow.admin.auth.infrastructure;

import java.time.Instant;

/**
 * JWT 解析和签名校验结果。
 */
public record JwtTokenInspection(
        /** JWT 校验状态。 */
        Status status,
        /** 用户 ID。 */
        String userId,
        /** 用户名。 */
        String username,
        /** 用户全局令牌版本。 */
        Long tokenVersion,
        /** 刷新会话 ID。 */
        String sessionId,
        /** JWT 过期时间。 */
        Instant expiresAt) {

    /**
     * JWT 校验状态。
     */
    public enum Status {
        /** JWT 签名、声明和有效期均有效。 */
        VALID,
        /** JWT 签名有效但已经过期。 */
        EXPIRED,
        /** JWT 格式、签名或必要声明无效。 */
        INVALID
    }

    public static JwtTokenInspection invalid() {
        return new JwtTokenInspection(
                Status.INVALID, null, null, null, null, null);
    }
}
