package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * Session data required to lock the counter before locking and terminating the Session row.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param applicationId 应用ID，后续用于处理嵌入式会话终止行时定位或关联目标
 * @param grantId 授权ID，后续用于处理嵌入式会话终止行时定位或关联目标
 * @param viewId 视图ID，后续用于处理嵌入式会话终止行时定位或关联目标
 * @param flowUserId 流程用户ID，后续用于处理嵌入式会话终止行时定位或关联目标
 * @param status 状态标识，决定后续嵌入式会话终止行采用的处理分支
 * @param slotReleased {@code slot}{@code released}，保存在对象中供后续校验、查询或展示
 * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record EmbedSessionTerminationRow(
        String id,
        String applicationId,
        String grantId,
        String viewId,
        String flowUserId,
        String status,
        boolean slotReleased,
        LocalDateTime idleExpiresAt,
        LocalDateTime absoluteExpiresAt) {
}
