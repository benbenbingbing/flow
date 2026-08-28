package com.workflow.embed.domain;

/** External identity provider security snapshot. */
public record EmbedIdentityProviderSnapshot(
        String id,
        String type,
        String status,
        String issuer,
        String subjectNamespace,
        String audiencesJson,
        String algorithmsJson,
        String jwksMode,
        String jwksJson,
        String jwksUrl,
        int clockSkewSeconds,
        int maxAssertionLifetimeSeconds,
        long keyVersion,
        long securityVersion) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
