package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * Flat MyBatis projection for the launch configuration aggregate.
 *
 * @param applicationId 应用ID，后续用于处理嵌入式启动记录配置行时定位或关联目标
 * @param applicationStatus 应用状态标识，决定后续嵌入式启动记录配置行采用的处理分支
 * @param applicationExpiresAt 应用过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param applicationVersion 应用版本，保存在对象中供后续校验、查询或展示
 * @param viewId 视图ID，后续用于处理嵌入式启动记录配置行时定位或关联目标
 * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
 * @param viewSurfaceType 视图界面类型标识，决定后续嵌入式启动记录配置行采用的处理分支
 * @param viewStatus 视图状态标识，决定后续嵌入式启动记录配置行采用的处理分支
 * @param currentConfigJson 当前配置JSON，保存在对象中供后续校验、查询或展示
 * @param viewSecurityVersion 视图安全版本，保存在对象中供后续校验、查询或展示
 * @param grantId 授权ID，后续用于处理嵌入式启动记录配置行时定位或关联目标
 * @param grantStatus 授权状态标识，决定后续嵌入式启动记录配置行采用的处理分支
 * @param trustedSubjectAssertion 可信主体断言，保存在对象中供后续校验、查询或展示
 * @param capabilityCeilingJson 能力{@code ceiling}JSON，保存在对象中供后续校验、查询或展示
 * @param maxActiveSessionsPerUser 最大活动会话每用户，保存在对象中供后续校验、查询或展示
 * @param maxSessionSeconds 最大会话秒数，保存在对象中供后续校验、查询或展示
 * @param launchLimitPerMinute 启动记录上限每分钟，保存在对象中供后续校验、查询或展示
 * @param runtimeLimitPerMinute 运行时上限每分钟，保存在对象中供后续校验、查询或展示
 * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
 * @param grantExpiresAt 授权过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param grantSecurityVersion 授权安全版本，保存在对象中供后续校验、查询或展示
 * @param providerId 提供者ID，后续用于处理嵌入式启动记录配置行时定位或关联目标
 * @param providerType 提供者类型标识，决定后续嵌入式启动记录配置行采用的处理分支
 * @param providerStatus 提供者状态标识，决定后续嵌入式启动记录配置行采用的处理分支
 * @param providerIssuer 提供者签发方，保存在对象中供后续校验、查询或展示
 * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
 * @param audiencesJson {@code audiences}JSON，保存在对象中供后续校验、查询或展示
 * @param algorithmsJson {@code algorithms}JSON，保存在对象中供后续校验、查询或展示
 * @param jwksMode JWKS模式标识，决定后续嵌入式启动记录配置行采用的处理分支
 * @param jwksJson JWKSJSON，保存在对象中供后续校验、查询或展示
 * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
 * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
 * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
 * @param providerKeyVersion 提供者键版本，保存在对象中供后续校验、查询或展示
 * @param providerSecurityVersion 提供者安全版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchConfigurationRow(
        String applicationId,
        String applicationStatus,
        LocalDateTime applicationExpiresAt,
        long applicationVersion,
        String viewId,
        String viewKey,
        String viewSurfaceType,
        String viewStatus,
        String currentConfigJson,
        long viewSecurityVersion,
        String grantId,
        String grantStatus,
        boolean trustedSubjectAssertion,
        String capabilityCeilingJson,
        int maxActiveSessionsPerUser,
        int maxSessionSeconds,
        int launchLimitPerMinute,
        int runtimeLimitPerMinute,
        int maxConcurrency,
        LocalDateTime grantExpiresAt,
        long grantSecurityVersion,
        String providerId,
        String providerType,
        String providerStatus,
        String providerIssuer,
        String subjectNamespace,
        String audiencesJson,
        String algorithmsJson,
        String jwksMode,
        String jwksJson,
        String jwksUrl,
        int clockSkewSeconds,
        int maxAssertionLifetimeSeconds,
        long providerKeyVersion,
        long providerSecurityVersion) {
}
