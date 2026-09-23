package com.workflow.embed.domain;

import java.time.Instant;

/**
 * Immutable data written for a newly issued launch. No plaintext credential is present.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param applicationId 应用ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param grantId 授权ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param viewId 视图ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param viewReleaseId 视图发布版本ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param identityProviderId 身份提供者ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param providerSecurityVersion 提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param applicationVersion 应用版本，保存在对象中供后续校验、查询或展示
 * @param grantSecurityVersion 授权安全版本，保存在对象中供后续校验、查询或展示
 * @param viewSecurityVersion 视图安全版本，保存在对象中供后续校验、查询或展示
 * @param flowUserId 流程用户ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param identityBindingId 身份绑定ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
 * @param subjectDigest 主体摘要，保存在对象中供后续校验、查询或展示
 * @param subjectDigestKeyVersion 主体摘要键版本，保存在对象中供后续校验、查询或展示
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param entryMode 入口模式标识，决定后续已持久化嵌入式启动记录采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param context 执行上下文，向后续已持久化嵌入式启动记录步骤传递身份、配置或状态
 * @param uiLocale 界面{@code locale}，保存在对象中供后续校验、查询或展示
 * @param uiTheme 界面{@code theme}，保存在对象中供后续校验、查询或展示
 * @param uiFormPresentation 界面表单展示，保存在对象中供后续校验、查询或展示
 * @param launchCodeDigest 启动记录编码摘要，保存在对象中供后续校验、查询或展示
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param traceId 追踪ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param requestId 请求ID，后续用于处理已持久化嵌入式启动记录时定位或关联目标
 * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
 */
public record PersistedEmbedLaunch(
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
        ProtectedContext context,
        String uiLocale,
        String uiTheme,
        String uiFormPresentation,
        String launchCodeDigest,
        Instant expiresAt,
        String traceId,
        String requestId,
        Instant createTime) {
}
