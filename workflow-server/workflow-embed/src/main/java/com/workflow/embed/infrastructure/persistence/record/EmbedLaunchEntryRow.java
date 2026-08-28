package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** MyBatis flat row for the anonymous dynamic Entry lookup. */
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
