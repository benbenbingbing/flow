package com.workflow.embed.infrastructure.persistence.record;

/**
 * Locked Grant/user active-session counter.
 *
 * @param grantId 授权ID，后续用于处理嵌入式会话计数器行时定位或关联目标
 * @param flowUserId 流程用户ID，后续用于处理嵌入式会话计数器行时定位或关联目标
 * @param activeCount 活动数量，保存在对象中供后续校验、查询或展示
 * @param lockVersion 锁定版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedSessionCounterRow(String grantId, String flowUserId, int activeCount, long lockVersion) {
}
