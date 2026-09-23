package com.workflow.embed.domain;

import java.time.Instant;

/**
 * Persisted launch and current release/grant data needed to prepare an exchange.
 *
 * @param launch 启动记录，保存在对象中供后续校验、查询或展示
 * @param status 状态标识，决定后续嵌入式启动记录交换候选人采用的处理分支
 * @param consumedAt {@code consumed}时间，后续用于判断有效期或展示该事件的发生时间
 * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
 * @param maxActiveSessionsPerUser 最大活动会话每用户，保存在对象中供后续校验、查询或展示
 * @param maxSessionSeconds 最大会话秒数，保存在对象中供后续校验、查询或展示
 * @param releaseCapabilitiesJson 发布版本能力集合JSON，保存在对象中供后续校验、查询或展示
 * @param grantCapabilitiesJson 授权能力集合JSON，保存在对象中供后续校验、查询或展示
 * @param currentApplication 当前应用，保存在对象中供后续校验、查询或展示
 * @param currentView 当前视图，保存在对象中供后续校验、查询或展示
 * @param currentGrant 当前授权，保存在对象中供后续校验、查询或展示
 * @param currentBinding 当前绑定，保存在对象中供后续校验、查询或展示
 * @param currentFlowUser 当前流程用户，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchExchangeCandidate(
        PersistedEmbedLaunch launch,
        String status,
        Instant consumedAt,
        Instant revokedAt,
        int maxActiveSessionsPerUser,
        int maxSessionSeconds,
        String releaseCapabilitiesJson,
        String grantCapabilitiesJson,
        EmbedApplicationSnapshot currentApplication,
        EmbedViewSnapshot currentView,
        EmbedGrantSnapshot currentGrant,
        EmbedExternalIdentityBinding currentBinding,
        EmbedFlowUser currentFlowUser) {
}
