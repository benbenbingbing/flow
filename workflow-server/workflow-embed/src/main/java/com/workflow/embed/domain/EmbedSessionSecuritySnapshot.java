package com.workflow.embed.domain;

import java.time.Instant;

/** Session plus live security state used on every authenticated runtime request. */
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
        Instant bindingEffectiveAt,
        Instant bindingExpiresAt,
        long currentBindingVersion,
        boolean flowUserEnabled,
        boolean flowUserDeleted,
        boolean flowUserPasswordResetRequired) {

    /** Checks every mutable revocation source against the session's immutable security snapshot. */
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
                && !bindingEffectiveAt.isAfter(now)
                && (bindingExpiresAt == null || bindingExpiresAt.isAfter(now))
                && currentBindingVersion == bindingVersion
                && flowUserEnabled
                && !flowUserDeleted
                && !flowUserPasswordResetRequired;
    }
}
