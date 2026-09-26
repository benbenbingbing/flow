package com.workflow.admin.auth.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * 数据库中的浏览器刷新会话记录。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param refreshTokenHash 刷新令牌哈希，保存在对象中供后续校验、查询或展示
 * @param tokenVersion 令牌版本，保存在对象中供后续校验、查询或展示
 * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
 * @param lastUsedAt 最后{@code used}时间，后续用于判断有效期或展示该事件的发生时间
 * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
 * @param revokedReason 已撤销原因，保存在对象中供后续校验、查询或展示
 * @param username 用户名称，后续用于身份匹配或操作展示
 * @param userStatus 用户状态标识，决定后续认证刷新会话记录采用的处理分支
 * @param userDeleted 用户已删除，保存在对象中供后续校验、查询或展示
 * @param userTokenVersion 用户令牌版本，保存在对象中供后续校验、查询或展示
 * @param passwordResetRequired 密码重置必填，保存在对象中供后续校验、查询或展示
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
