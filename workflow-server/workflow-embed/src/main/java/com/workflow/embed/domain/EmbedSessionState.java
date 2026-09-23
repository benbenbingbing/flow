package com.workflow.embed.domain;

import java.time.Instant;

/**
 * Public, non-secret session state returned by GET/heartbeat.
 *
 * @param sessionId 会话ID，后续用于处理嵌入式会话状态时定位或关联目标
 * @param status 状态标识，决定后续嵌入式会话状态采用的处理分支
 * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param nextHeartbeatAfterSeconds 下一步心跳之后秒数，保存在对象中供后续校验、查询或展示
 */
public record EmbedSessionState(
        String sessionId,
        String status,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt,
        int nextHeartbeatAfterSeconds) {
}
