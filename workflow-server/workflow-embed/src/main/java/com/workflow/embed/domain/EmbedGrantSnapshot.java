package com.workflow.embed.domain;

import java.time.Instant;
import java.util.Set;

/**
 * Application-to-view grant and its security limits.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param applicationId 应用ID，后续用于处理嵌入式授权快照时定位或关联目标
 * @param viewId 视图ID，后续用于处理嵌入式授权快照时定位或关联目标
 * @param status 状态标识，决定后续嵌入式授权快照采用的处理分支
 * @param trustedSubjectAssertion 可信主体断言，保存在对象中供后续校验、查询或展示
 * @param capabilityCeilingJson 能力{@code ceiling}JSON，保存在对象中供后续校验、查询或展示
 * @param maxActiveSessionsPerUser 最大活动会话每用户，保存在对象中供后续校验、查询或展示
 * @param maxSessionSeconds 最大会话秒数，保存在对象中供后续校验、查询或展示
 * @param launchLimitPerMinute 启动记录上限每分钟，保存在对象中供后续校验、查询或展示
 * @param runtimeLimitPerMinute 运行时上限每分钟，保存在对象中供后续校验、查询或展示
 * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
 * @param identityProvider 身份提供者，保存在对象中供后续校验、查询或展示
 * @param allowedOrigins 允许来源，保存在对象中供后续校验、查询或展示
 */
public record EmbedGrantSnapshot(
        String id,
        String applicationId,
        String viewId,
        String status,
        boolean trustedSubjectAssertion,
        String capabilityCeilingJson,
        int maxActiveSessionsPerUser,
        int maxSessionSeconds,
        int launchLimitPerMinute,
        int runtimeLimitPerMinute,
        int maxConcurrency,
        Instant expiresAt,
        long securityVersion,
        EmbedIdentityProviderSnapshot identityProvider,
        Set<String> allowedOrigins) {

    /**
     * 判断是否活动时间；判断结果决定调用方的后续分支。
     *
     * @param now 当前时间，供本方法判断是否活动时间时使用
     * @return 活动时间条件成立时为 true，否则为 false
     */
    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }
}
