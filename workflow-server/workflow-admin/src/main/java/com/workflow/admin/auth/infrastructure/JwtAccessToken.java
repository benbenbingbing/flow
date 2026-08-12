package com.workflow.admin.auth.infrastructure;

import java.time.Instant;

/**
 * 已签发的短期 Access Token。
 */
public record JwtAccessToken(
        /** JWT 字符串。 */
        String value,
        /** JWT 的绝对过期时间。 */
        Instant expiresAt) {
}
