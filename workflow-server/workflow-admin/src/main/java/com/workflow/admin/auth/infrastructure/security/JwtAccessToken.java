package com.workflow.admin.auth.infrastructure.security;

import java.time.Instant;

/**
 * 已签发的短期 Access Token。
 *
 * @param value 待处理{@code jwt}访问令牌的原始输入，结果供调用方继续使用
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record JwtAccessToken(
        /** JWT 字符串。 */
        String value,
        /** JWT 的绝对过期时间。 */
        Instant expiresAt) {
}
