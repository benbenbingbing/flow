package com.workflow.embed.domain;

import java.time.Instant;

/** 非敏感 Entry 元数据及其当前撤销状态；不包含 code、用户或 Context。 */
public record EmbedLaunchEntrySnapshot(
        String launchId,
        String parentOrigin,
        String channelId,
        String launchStatus,
        Instant launchExpiresAt,
        long applicationVersion,
        long currentApplicationVersion,
        String applicationStatus,
        Instant applicationExpiresAt,
        long grantSecurityVersion,
        long currentGrantSecurityVersion,
        String grantStatus,
        Instant grantExpiresAt,
        long viewSecurityVersion,
        long currentViewSecurityVersion,
        String viewStatus,
        long providerSecurityVersion,
        long currentProviderSecurityVersion,
        String providerStatus,
        long bindingVersion,
        long currentBindingVersion,
        String bindingStatus,
        Instant bindingEffectiveAt,
        Instant bindingExpiresAt,
        String flowUserStatus,
        boolean flowUserDeleted) {
}
