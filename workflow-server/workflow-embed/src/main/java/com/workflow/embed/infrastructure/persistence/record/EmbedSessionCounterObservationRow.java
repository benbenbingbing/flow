package com.workflow.embed.infrastructure.persistence.record;

/** 维护扫描使用的 Counter/真实 Session 数量投影。 */
public record EmbedSessionCounterObservationRow(
        String grantId,
        String flowUserId,
        Integer storedCount,
        long actualCount) {
}
