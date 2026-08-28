package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** Persistence projection for one exact external identity binding. */
public record EmbedExternalIdentityBindingRow(
        String id,
        String applicationId,
        String identityProviderId,
        String subjectDigest,
        String subjectDigestKeyVersion,
        String flowUserId,
        String status,
        long bindingVersion,
        LocalDateTime effectiveAt,
        LocalDateTime expiresAt) {
}
