package com.workflow.embed.domain;

import java.time.Instant;
import java.util.Set;

/** Application-to-view grant and its security limits. */
public record EmbedGrantSnapshot(
        String id,
        String applicationId,
        String viewId,
        String status,
        boolean trustedSubjectAssertion,
        String capabilityCeilingJson,
        int maxActiveSessionsPerUser,
        int maxSessionSeconds,
        int launchLimitPerMinute,
        int runtimeLimitPerMinute,
        int maxConcurrency,
        Instant expiresAt,
        long securityVersion,
        EmbedIdentityProviderSnapshot identityProvider,
        Set<String> allowedOrigins) {

    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }
}
