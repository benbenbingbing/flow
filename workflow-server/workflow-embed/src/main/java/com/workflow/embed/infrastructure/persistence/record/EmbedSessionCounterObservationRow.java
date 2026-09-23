package com.workflow.embed.infrastructure.persistence.record;

/**
 * 维护扫描使用的 Counter/真实 Session 数量投影。
 *
 * @param grantId 授权ID，后续用于处理嵌入式会话计数器观察行时定位或关联目标
 * @param flowUserId 流程用户ID，后续用于处理嵌入式会话计数器观察行时定位或关联目标
 * @param storedCount 已存储数量，保存在对象中供后续校验、查询或展示
 * @param actualCount 实际数量，保存在对象中供后续校验、查询或展示
 */
public record EmbedSessionCounterObservationRow(
        String grantId,
        String flowUserId,
        Integer storedCount,
        long actualCount) {
}
