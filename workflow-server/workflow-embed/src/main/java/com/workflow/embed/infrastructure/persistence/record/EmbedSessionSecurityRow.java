package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * Flat Session plus live security-source projection.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param sessionTokenDigest 会话令牌摘要，保存在对象中供后续校验、查询或展示
 * @param applicationId 应用ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param grantId 授权ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param viewId 视图ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param viewReleaseId 视图发布版本ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param identityProviderId 身份提供者ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param providerSecurityVersion 提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param flowUserId 流程用户ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param flowUsername 流程用户名，后续用于处理嵌入式会话安全行时匹配或展示
 * @param identityBindingId 身份绑定ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param entryMode 入口模式标识，决定后续嵌入式会话安全行采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param contextCiphertext 上下文{@code ciphertext}，保存在对象中供后续校验、查询或展示
 * @param contextCipherKeyVersion 上下文{@code cipher}键版本，保存在对象中供后续校验、查询或展示
 * @param capabilitySnapshotJson 能力快照JSON，保存在对象中供后续校验、查询或展示
 * @param applicationVersion 应用版本，保存在对象中供后续校验、查询或展示
 * @param grantSecurityVersion 授权安全版本，保存在对象中供后续校验、查询或展示
 * @param viewSecurityVersion 视图安全版本，保存在对象中供后续校验、查询或展示
 * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
 * @param sessionStatus 会话状态标识，决定后续嵌入式会话安全行采用的处理分支
 * @param slotReleased {@code slot}{@code released}，保存在对象中供后续校验、查询或展示
 * @param lastSeenAt 最后已见时间，后续用于判断有效期或展示该事件的发生时间
 * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param applicationStatus 应用状态标识，决定后续嵌入式会话安全行采用的处理分支
 * @param applicationExpiresAt 应用过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param currentApplicationVersion 当前应用版本，保存在对象中供后续校验、查询或展示
 * @param grantStatus 授权状态标识，决定后续嵌入式会话安全行采用的处理分支
 * @param grantExpiresAt 授权过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param currentGrantSecurityVersion 当前授权安全版本，保存在对象中供后续校验、查询或展示
 * @param viewStatus 视图状态标识，决定后续嵌入式会话安全行采用的处理分支
 * @param currentViewSecurityVersion 当前视图安全版本，保存在对象中供后续校验、查询或展示
 * @param providerStatus 提供者状态标识，决定后续嵌入式会话安全行采用的处理分支
 * @param currentProviderSecurityVersion 当前提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param bindingStatus 绑定状态标识，决定后续嵌入式会话安全行采用的处理分支
 * @param currentBindingApplicationId 当前绑定应用ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param currentBindingIdentityProviderId 当前绑定身份提供者ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param currentBindingFlowUserId 当前绑定流程用户ID，后续用于处理嵌入式会话安全行时定位或关联目标
 * @param bindingEffectiveAt 绑定有效时间，后续用于判断有效期或展示该事件的发生时间
 * @param bindingExpiresAt 绑定过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param currentBindingVersion 当前绑定版本，保存在对象中供后续校验、查询或展示
 * @param flowUserStatus 流程用户状态标识，决定后续嵌入式会话安全行采用的处理分支
 * @param flowUserDeleted 流程用户已删除，保存在对象中供后续校验、查询或展示
 * @param flowUserPasswordResetRequired 流程用户密码重置必填，保存在对象中供后续校验、查询或展示
 */
public record EmbedSessionSecurityRow(
        String id,
        String sessionTokenDigest,
        String applicationId,
        String grantId,
        String viewId,
        String viewReleaseId,
        String identityProviderId,
        long providerSecurityVersion,
        String flowUserId,
        String flowUsername,
        String identityBindingId,
        String parentOrigin,
        String channelId,
        String entryMode,
        String recordId,
        String contextCiphertext,
        String contextCipherKeyVersion,
        String capabilitySnapshotJson,
        long applicationVersion,
        long grantSecurityVersion,
        long viewSecurityVersion,
        long bindingVersion,
        String sessionStatus,
        boolean slotReleased,
        LocalDateTime lastSeenAt,
        LocalDateTime idleExpiresAt,
        LocalDateTime absoluteExpiresAt,
        String applicationStatus,
        LocalDateTime applicationExpiresAt,
        long currentApplicationVersion,
        String grantStatus,
        LocalDateTime grantExpiresAt,
        long currentGrantSecurityVersion,
        String viewStatus,
        long currentViewSecurityVersion,
        String providerStatus,
        long currentProviderSecurityVersion,
        String bindingStatus,
        String currentBindingApplicationId,
        String currentBindingIdentityProviderId,
        String currentBindingFlowUserId,
        LocalDateTime bindingEffectiveAt,
        LocalDateTime bindingExpiresAt,
        long currentBindingVersion,
        String flowUserStatus,
        int flowUserDeleted,
        int flowUserPasswordResetRequired) {
}
