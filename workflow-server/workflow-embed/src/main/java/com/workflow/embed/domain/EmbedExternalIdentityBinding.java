package com.workflow.embed.domain;

import java.time.Instant;

/** Exact external subject to Flow user binding. */
public record EmbedExternalIdentityBinding(
        String id,
        String applicationId,
        String identityProviderId,
        String subjectDigest,
        String subjectDigestKeyVersion,
        String flowUserId,
        String status,
        long bindingVersion,
        Instant effectiveAt,
        Instant expiresAt) {

    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status)
                && !effectiveAt.isAfter(now)
                && (expiresAt == null || expiresAt.isAfter(now));
    }
}
