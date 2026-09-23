package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * Locked Grant security state and active-session limit.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param status 状态标识，决定后续嵌入式授权锁定行采用的处理分支
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
 * @param maxActiveSessionsPerUser 最大活动会话每用户，保存在对象中供后续校验、查询或展示
 * @param maxSessionSeconds 最大会话秒数，保存在对象中供后续校验、查询或展示
 */
public record EmbedGrantLockRow(
        String id,
        String status,
        LocalDateTime expiresAt,
        long securityVersion,
        int maxActiveSessionsPerUser,
        int maxSessionSeconds) {
}
