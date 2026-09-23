package com.workflow.embed.domain;

import java.time.Instant;

/**
 * All values required by the atomic launch-to-session transaction.
 *
 * @param candidate 候选人，后续用于判断有效期或展示该事件的发生时间
 * @param sessionId 会话ID，后续用于处理嵌入式会话交换方案时定位或关联目标
 * @param sessionTokenDigest 会话令牌摘要，保存在对象中供后续校验、查询或展示
 * @param parentNonceDigest 父级{@code nonce}摘要，保存在对象中供后续校验、查询或展示
 * @param childNonceDigest 子级{@code nonce}摘要，保存在对象中供后续校验、查询或展示
 * @param sessionContext 执行上下文，向后续嵌入式会话交换方案步骤传递身份、配置或状态
 * @param capabilitySnapshotJson 能力快照JSON，保存在对象中供后续校验、查询或展示
 * @param issuedAt 已签发时间，后续用于判断有效期或展示该事件的发生时间
 * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record EmbedSessionExchangePlan(
        EmbedLaunchExchangeCandidate candidate,
        String sessionId,
        String sessionTokenDigest,
        String parentNonceDigest,
        String childNonceDigest,
        ProtectedContext sessionContext,
        String capabilitySnapshotJson,
        Instant issuedAt,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt) {
}
