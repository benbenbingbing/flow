package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * Flat Launch plus current security projection used before the atomic exchange.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param applicationId 应用ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param grantId 授权ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param viewId 视图ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param viewReleaseId 视图发布版本ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param identityProviderId 身份提供者ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param providerSecurityVersion 提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param applicationVersion 应用版本，保存在对象中供后续校验、查询或展示
 * @param grantSecurityVersion 授权安全版本，保存在对象中供后续校验、查询或展示
 * @param viewSecurityVersion 视图安全版本，保存在对象中供后续校验、查询或展示
 * @param flowUserId 流程用户ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param identityBindingId 身份绑定ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
 * @param subjectDigest 主体摘要，保存在对象中供后续校验、查询或展示
 * @param subjectDigestKeyVersion 主体摘要键版本，保存在对象中供后续校验、查询或展示
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param entryMode 入口模式标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param contextCiphertext 上下文{@code ciphertext}，保存在对象中供后续校验、查询或展示
 * @param contextCipherKeyVersion 上下文{@code cipher}键版本，保存在对象中供后续校验、查询或展示
 * @param contextDigest 上下文摘要，保存在对象中供后续校验、查询或展示
 * @param contextDigestKeyVersion 上下文摘要键版本，保存在对象中供后续校验、查询或展示
 * @param uiLocale 界面{@code locale}，保存在对象中供后续校验、查询或展示
 * @param uiTheme 界面{@code theme}，保存在对象中供后续校验、查询或展示
 * @param uiFormPresentation 界面表单展示，保存在对象中供后续校验、查询或展示
 * @param launchCodeDigest 启动记录编码摘要，保存在对象中供后续校验、查询或展示
 * @param launchStatus 启动记录状态标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param consumedAt {@code consumed}时间，后续用于判断有效期或展示该事件的发生时间
 * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
 * @param traceId 追踪ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param requestId 请求ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
 * @param maxActiveSessionsPerUser 最大活动会话每用户，保存在对象中供后续校验、查询或展示
 * @param maxSessionSeconds 最大会话秒数，保存在对象中供后续校验、查询或展示
 * @param launchLimitPerMinute 启动记录上限每分钟，保存在对象中供后续校验、查询或展示
 * @param runtimeLimitPerMinute 运行时上限每分钟，保存在对象中供后续校验、查询或展示
 * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
 * @param releaseCapabilitiesJson 发布版本能力集合JSON，保存在对象中供后续校验、查询或展示
 * @param grantCapabilitiesJson 授权能力集合JSON，保存在对象中供后续校验、查询或展示
 * @param applicationStatus 应用状态标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param applicationExpiresAt 应用过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param currentApplicationVersion 当前应用版本，保存在对象中供后续校验、查询或展示
 * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
 * @param viewSurfaceType 视图界面类型标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param viewStatus 视图状态标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param currentViewSecurityVersion 当前视图安全版本，保存在对象中供后续校验、查询或展示
 * @param grantStatus 授权状态标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param grantExpiresAt 授权过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param currentGrantSecurityVersion 当前授权安全版本，保存在对象中供后续校验、查询或展示
 * @param trustedSubjectAssertion 可信主体断言，保存在对象中供后续校验、查询或展示
 * @param providerType 提供者类型标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param providerStatus 提供者状态标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param providerIssuer 提供者签发方，保存在对象中供后续校验、查询或展示
 * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
 * @param audiencesJson {@code audiences}JSON，保存在对象中供后续校验、查询或展示
 * @param algorithmsJson {@code algorithms}JSON，保存在对象中供后续校验、查询或展示
 * @param jwksMode JWKS模式标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param jwksJson JWKSJSON，保存在对象中供后续校验、查询或展示
 * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
 * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
 * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
 * @param providerKeyVersion 提供者键版本，保存在对象中供后续校验、查询或展示
 * @param currentProviderSecurityVersion 当前提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param bindingApplicationId 绑定应用ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param bindingProviderId 绑定提供者ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param bindingSubjectDigest 绑定主体摘要，保存在对象中供后续校验、查询或展示
 * @param bindingSubjectDigestKeyVersion 绑定主体摘要键版本，保存在对象中供后续校验、查询或展示
 * @param bindingFlowUserId 绑定流程用户ID，后续用于处理嵌入式启动记录交换行时定位或关联目标
 * @param bindingStatus 绑定状态标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param currentBindingVersion 当前绑定版本，保存在对象中供后续校验、查询或展示
 * @param bindingEffectiveAt 绑定有效时间，后续用于判断有效期或展示该事件的发生时间
 * @param bindingExpiresAt 绑定过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param flowUsername 流程用户名，后续用于处理嵌入式启动记录交换行时匹配或展示
 * @param flowUserStatus 流程用户状态标识，决定后续嵌入式启动记录交换行采用的处理分支
 * @param flowUserDeleted 流程用户已删除，保存在对象中供后续校验、查询或展示
 * @param flowUserPasswordResetRequired 流程用户密码重置必填，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchExchangeRow(
        String id,
        String applicationId,
        String grantId,
        String viewId,
        String viewReleaseId,
        String identityProviderId,
        long providerSecurityVersion,
        long applicationVersion,
        long grantSecurityVersion,
        long viewSecurityVersion,
        String flowUserId,
        String identityBindingId,
        long bindingVersion,
        String subjectDigest,
        String subjectDigestKeyVersion,
        String parentOrigin,
        String channelId,
        String entryMode,
        String recordId,
        String contextCiphertext,
        String contextCipherKeyVersion,
        String contextDigest,
        String contextDigestKeyVersion,
        String uiLocale,
        String uiTheme,
        String uiFormPresentation,
        String launchCodeDigest,
        String launchStatus,
        LocalDateTime expiresAt,
        LocalDateTime consumedAt,
        LocalDateTime revokedAt,
        String traceId,
        String requestId,
        LocalDateTime createTime,
        int maxActiveSessionsPerUser,
        int maxSessionSeconds,
        int launchLimitPerMinute,
        int runtimeLimitPerMinute,
        int maxConcurrency,
        String releaseCapabilitiesJson,
        String grantCapabilitiesJson,
        String applicationStatus,
        LocalDateTime applicationExpiresAt,
        long currentApplicationVersion,
        String viewKey,
        String viewSurfaceType,
        String viewStatus,
        long currentViewSecurityVersion,
        String grantStatus,
        LocalDateTime grantExpiresAt,
        long currentGrantSecurityVersion,
        boolean trustedSubjectAssertion,
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
        long currentProviderSecurityVersion,
        String bindingApplicationId,
        String bindingProviderId,
        String bindingSubjectDigest,
        String bindingSubjectDigestKeyVersion,
        String bindingFlowUserId,
        String bindingStatus,
        long currentBindingVersion,
        LocalDateTime bindingEffectiveAt,
        LocalDateTime bindingExpiresAt,
        String flowUsername,
        String flowUserStatus,
        int flowUserDeleted,
        int flowUserPasswordResetRequired) {
}
