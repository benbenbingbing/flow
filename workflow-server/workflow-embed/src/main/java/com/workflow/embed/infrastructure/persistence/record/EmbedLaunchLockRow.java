package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** Locked one-time Launch state checked immediately before conditional consumption. */
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
