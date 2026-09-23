package com.workflow.embed.infrastructure.persistence.record;

/**
 * Locked identity-provider security state.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param status 状态标识，决定后续嵌入式提供者锁定行采用的处理分支
 * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedProviderLockRow(String id, String status, long securityVersion) {
}
