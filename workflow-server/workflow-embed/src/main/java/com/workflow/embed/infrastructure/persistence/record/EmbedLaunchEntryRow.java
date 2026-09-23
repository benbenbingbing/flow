package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * MyBatis flat row for the anonymous dynamic Entry lookup.
 *
 * @param launchId 启动记录ID，后续用于处理嵌入式启动记录入口行时定位或关联目标
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理嵌入式启动记录入口行时定位或关联目标
 * @param launchStatus 启动记录状态标识，决定后续嵌入式启动记录入口行采用的处理分支
 * @param launchExpiresAt 启动记录过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param applicationVersion 应用版本，保存在对象中供后续校验、查询或展示
 * @param currentApplicationVersion 当前应用版本，保存在对象中供后续校验、查询或展示
 * @param applicationStatus 应用状态标识，决定后续嵌入式启动记录入口行采用的处理分支
 * @param applicationExpiresAt 应用过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param grantSecurityVersion 授权安全版本，保存在对象中供后续校验、查询或展示
 * @param currentGrantSecurityVersion 当前授权安全版本，保存在对象中供后续校验、查询或展示
 * @param grantStatus 授权状态标识，决定后续嵌入式启动记录入口行采用的处理分支
 * @param grantExpiresAt 授权过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param viewSecurityVersion 视图安全版本，保存在对象中供后续校验、查询或展示
 * @param currentViewSecurityVersion 当前视图安全版本，保存在对象中供后续校验、查询或展示
 * @param viewStatus 视图状态标识，决定后续嵌入式启动记录入口行采用的处理分支
 * @param providerSecurityVersion 提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param currentProviderSecurityVersion 当前提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param providerStatus 提供者状态标识，决定后续嵌入式启动记录入口行采用的处理分支
 * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
 * @param currentBindingVersion 当前绑定版本，保存在对象中供后续校验、查询或展示
 * @param bindingStatus 绑定状态标识，决定后续嵌入式启动记录入口行采用的处理分支
 * @param bindingEffectiveAt 绑定有效时间，后续用于判断有效期或展示该事件的发生时间
 * @param bindingExpiresAt 绑定过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param flowUserStatus 流程用户状态标识，决定后续嵌入式启动记录入口行采用的处理分支
 * @param flowUserDeleted 流程用户已删除，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchEntryRow(
        String launchId,
        String parentOrigin,
        String channelId,
        String launchStatus,
        LocalDateTime launchExpiresAt,
        long applicationVersion,
        long currentApplicationVersion,
        String applicationStatus,
        LocalDateTime applicationExpiresAt,
        long grantSecurityVersion,
        long currentGrantSecurityVersion,
        String grantStatus,
        LocalDateTime grantExpiresAt,
        long viewSecurityVersion,
        long currentViewSecurityVersion,
        String viewStatus,
        long providerSecurityVersion,
        long currentProviderSecurityVersion,
        String providerStatus,
        long bindingVersion,
        long currentBindingVersion,
        String bindingStatus,
        LocalDateTime bindingEffectiveAt,
        LocalDateTime bindingExpiresAt,
        String flowUserStatus,
        boolean flowUserDeleted) {
}
