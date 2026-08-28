package com.workflow.embed.domain;

import java.time.Instant;

/** Immutable data written for a newly issued launch. No plaintext credential is present. */
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
        String launchCodeDigest,
        Instant expiresAt,
        String traceId,
        String requestId,
        Instant createTime) {
}
