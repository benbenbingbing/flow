package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * Locked one-time Launch state checked immediately before conditional consumption.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param status 状态标识，决定后续嵌入式启动记录锁定行采用的处理分支
 * @param applicationId 应用ID，后续用于处理嵌入式启动记录锁定行时定位或关联目标
 * @param grantId 授权ID，后续用于处理嵌入式启动记录锁定行时定位或关联目标
 * @param viewId 视图ID，后续用于处理嵌入式启动记录锁定行时定位或关联目标
 * @param viewReleaseId 视图发布版本ID，后续用于处理嵌入式启动记录锁定行时定位或关联目标
 * @param identityProviderId 身份提供者ID，后续用于处理嵌入式启动记录锁定行时定位或关联目标
 * @param identityBindingId 身份绑定ID，后续用于处理嵌入式启动记录锁定行时定位或关联目标
 * @param flowUserId 流程用户ID，后续用于处理嵌入式启动记录锁定行时定位或关联目标
 * @param launchCodeDigest 启动记录编码摘要，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理嵌入式启动记录锁定行时定位或关联目标
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param applicationVersion 应用版本，保存在对象中供后续校验、查询或展示
 * @param grantSecurityVersion 授权安全版本，保存在对象中供后续校验、查询或展示
 * @param viewSecurityVersion 视图安全版本，保存在对象中供后续校验、查询或展示
 * @param providerSecurityVersion 提供者安全版本，保存在对象中供后续校验、查询或展示
 * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchLockRow(
        String id,
        String status,
        String applicationId,
        String grantId,
        String viewId,
        String viewReleaseId,
        String identityProviderId,
        String identityBindingId,
        String flowUserId,
        String launchCodeDigest,
        String channelId,
        String parentOrigin,
        LocalDateTime expiresAt,
        long applicationVersion,
        long grantSecurityVersion,
        long viewSecurityVersion,
        long providerSecurityVersion,
        long bindingVersion) {
}
