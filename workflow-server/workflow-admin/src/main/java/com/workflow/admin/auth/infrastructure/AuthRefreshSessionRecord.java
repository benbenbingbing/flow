package com.workflow.admin.auth.infrastructure;

import java.time.LocalDateTime;

/**
 * 数据库中的浏览器刷新会话记录。
 */
public record AuthRefreshSessionRecord(
        /** 刷新会话 ID。 */
        String id,
        /** 会话所属用户 ID。 */
        String userId,
        /** Refresh Token 的 SHA-256 摘要。 */
        String refreshTokenHash,
        /** 创建会话时的用户全局令牌版本。 */
        Long tokenVersion,
        /** 会话创建时间。 */
        LocalDateTime createTime,
        /** 最近一次成功刷新时间。 */
        LocalDateTime lastUsedAt,
        /** 会话空闲过期时间。 */
        LocalDateTime idleExpiresAt,
        /** 会话绝对过期时间。 */
        LocalDateTime absoluteExpiresAt,
        /** 会话撤销时间。 */
        LocalDateTime revokedAt,
        /** 会话撤销原因。 */
        String revokedReason,
        /** 用户名。 */
        String username,
        /** 用户状态。 */
        String userStatus,
        /** 用户删除标记。 */
        Integer userDeleted,
        /** 用户当前全局令牌版本。 */
        Long userTokenVersion,
        /** 是否必须先修改密码。 */
        Boolean passwordResetRequired) {
}
