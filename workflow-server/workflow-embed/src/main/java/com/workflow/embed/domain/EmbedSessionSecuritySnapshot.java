package com.workflow.embed.domain;

import java.time.Instant;

/**
 * Session plus live security state used on every authenticated runtime request.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param tokenDigest 令牌摘要，保存在对象中供后续校验、查询或展示
 * @param applicationId 应用ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param grantId 授权ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param viewId 视图ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param viewReleaseId 视图发布版本ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param identityProviderId 身份提供者ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param flowUserId 流程用户ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param flowUsername 流程用户名，后续用于处理嵌入式会话安全快照时匹配或展示
 * @param identityBindingId 身份绑定ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param entryMode 入口模式标识，决定后续嵌入式会话安全快照采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param contextCiphertext 上下文{@code ciphertext}，保存在对象中供后续校验、查询或展示
 * @param contextCipherKeyVersion 上下文{@code cipher}键版本，保存在对象中供后续校验、查询或展示
 * @param capabilitySnapshotJson 能力快照JSON，保存在对象中供后续校验、查询或展示
 * @param applicationVersion 应用版本，保存在对象中供后续校验、查询或展示
 * @param grantSecurityVersion 授权安全版本，保存在对象中供后续校验、查询或展示
 * @param viewSecurityVersion 视图安全版本，保存在对象中供后续校验、查询或展示
 * @param providerSecurityVersion 提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
 * @param status 状态标识，决定后续嵌入式会话安全快照采用的处理分支
 * @param slotReleased {@code slot}{@code released}，保存在对象中供后续校验、查询或展示
 * @param lastSeenAt 最后已见时间，后续用于判断有效期或展示该事件的发生时间
 * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param applicationStatus 应用状态标识，决定后续嵌入式会话安全快照采用的处理分支
 * @param applicationExpiresAt 应用过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param currentApplicationVersion 当前应用版本，保存在对象中供后续校验、查询或展示
 * @param grantStatus 授权状态标识，决定后续嵌入式会话安全快照采用的处理分支
 * @param grantExpiresAt 授权过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param currentGrantSecurityVersion 当前授权安全版本，保存在对象中供后续校验、查询或展示
 * @param viewStatus 视图状态标识，决定后续嵌入式会话安全快照采用的处理分支
 * @param currentViewSecurityVersion 当前视图安全版本，保存在对象中供后续校验、查询或展示
 * @param providerStatus 提供者状态标识，决定后续嵌入式会话安全快照采用的处理分支
 * @param currentProviderSecurityVersion 当前提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param bindingStatus 绑定状态标识，决定后续嵌入式会话安全快照采用的处理分支
 * @param currentBindingApplicationId 当前绑定应用ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param currentBindingIdentityProviderId 当前绑定身份提供者ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param currentBindingFlowUserId 当前绑定流程用户ID，后续用于处理嵌入式会话安全快照时定位或关联目标
 * @param bindingEffectiveAt 绑定有效时间，后续用于判断有效期或展示该事件的发生时间
 * @param bindingExpiresAt 绑定过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param currentBindingVersion 当前绑定版本，保存在对象中供后续校验、查询或展示
 * @param flowUserEnabled 流程用户启用，保存在对象中供后续校验、查询或展示
 * @param flowUserDeleted 流程用户已删除，保存在对象中供后续校验、查询或展示
 * @param flowUserPasswordResetRequired 流程用户密码重置必填，保存在对象中供后续校验、查询或展示
 */
public record EmbedSessionSecuritySnapshot(
        String id,
        String tokenDigest,
        String applicationId,
        String grantId,
        String viewId,
        String viewReleaseId,
        String identityProviderId,
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
        long providerSecurityVersion,
        long bindingVersion,
        String status,
        boolean slotReleased,
        Instant lastSeenAt,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt,
        String applicationStatus,
        Instant applicationExpiresAt,
        long currentApplicationVersion,
        String grantStatus,
        Instant grantExpiresAt,
        long currentGrantSecurityVersion,
        String viewStatus,
        long currentViewSecurityVersion,
        String providerStatus,
        long currentProviderSecurityVersion,
        String bindingStatus,
        String currentBindingApplicationId,
        String currentBindingIdentityProviderId,
        String currentBindingFlowUserId,
        Instant bindingEffectiveAt,
        Instant bindingExpiresAt,
        long currentBindingVersion,
        boolean flowUserEnabled,
        boolean flowUserDeleted,
        boolean flowUserPasswordResetRequired) {

    /**
     * Checks every mutable revocation source against the session's immutable security snapshot.
     *
     * @param now 当前时间，作为 {@code bindingEffectiveAt.isAfter} 的输入影响后续处理
     * @return 安全快照{@code still}有效条件成立时为 true，否则为 false
     */
    public boolean securitySnapshotStillValid(Instant now) {
        return "ACTIVE".equals(applicationStatus)
                && (applicationExpiresAt == null || applicationExpiresAt.isAfter(now))
                && currentApplicationVersion == applicationVersion
                && "ACTIVE".equals(grantStatus)
                && (grantExpiresAt == null || grantExpiresAt.isAfter(now))
                && currentGrantSecurityVersion == grantSecurityVersion
                && "ACTIVE".equals(viewStatus)
                && currentViewSecurityVersion == viewSecurityVersion
                && "ACTIVE".equals(providerStatus)
                && currentProviderSecurityVersion == providerSecurityVersion
                && "ACTIVE".equals(bindingStatus)
                // Binding 是外部 Subject 到 Flow 用户的唯一身份根。即使底层数据被
                // 非标准路径改写且遗漏版本递增，也不能让既有 Session 换人执行。
                && applicationId.equals(currentBindingApplicationId)
                && identityProviderId.equals(currentBindingIdentityProviderId)
                && flowUserId.equals(currentBindingFlowUserId)
                && !bindingEffectiveAt.isAfter(now)
                && (bindingExpiresAt == null || bindingExpiresAt.isAfter(now))
                && currentBindingVersion == bindingVersion
                && flowUserEnabled
                && !flowUserDeleted
                && !flowUserPasswordResetRequired;
    }
}
