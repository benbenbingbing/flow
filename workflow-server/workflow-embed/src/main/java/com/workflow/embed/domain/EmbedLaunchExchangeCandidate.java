package com.workflow.embed.domain;

import java.time.Instant;

/** Persisted launch and current release/grant data needed to prepare an exchange. */
public record EmbedLaunchExchangeCandidate(
        PersistedEmbedLaunch launch,
        String status,
        Instant consumedAt,
        Instant revokedAt,
        int maxActiveSessionsPerUser,
        int maxSessionSeconds,
        String releaseCapabilitiesJson,
        String grantCapabilitiesJson,
        EmbedApplicationSnapshot currentApplication,
        EmbedViewSnapshot currentView,
        EmbedGrantSnapshot currentGrant,
        EmbedExternalIdentityBinding currentBinding,
        EmbedFlowUser currentFlowUser) {
}
