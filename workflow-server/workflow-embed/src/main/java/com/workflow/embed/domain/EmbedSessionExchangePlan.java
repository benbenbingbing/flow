package com.workflow.embed.domain;

import java.time.Instant;

/** All values required by the atomic launch-to-session transaction. */
public record EmbedSessionExchangePlan(
        EmbedLaunchExchangeCandidate candidate,
        String sessionId,
        String sessionTokenDigest,
        String parentNonceDigest,
        String childNonceDigest,
        ProtectedContext sessionContext,
        String capabilitySnapshotJson,
        Instant issuedAt,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt) {
}
