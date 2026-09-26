package com.workflow.admin.auth.infrastructure.security;

import java.time.Instant;

/**
 * JWT 解析和签名校验结果。
 *
 * @param status 状态标识，决定后续{@code jwt}令牌{@code inspection}采用的处理分支
 * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param username 用户名称，后续用于身份匹配或操作展示
 * @param tokenVersion 令牌版本，保存在对象中供后续校验、查询或展示
 * @param sessionId 会话ID，后续用于处理{@code jwt}令牌{@code inspection}时定位或关联目标
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
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

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @return 处理后的无效结果，供调用方继续处理
     */
    public static JwtTokenInspection invalid() {
        return new JwtTokenInspection(
                Status.INVALID, null, null, null, null, null);
    }
}
