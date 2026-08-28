package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** 配额事务中锁定的当前 Grant 服务端快照。 */
public record EmbedTrafficGrantRow(
        String id,
        String applicationId,
        String status,
        LocalDateTime expiresAt,
        int launchLimitPerMinute,
        int runtimeLimitPerMinute,
        int maxConcurrency) {
}
