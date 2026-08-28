package com.workflow.embed.domain;

import java.time.Instant;

/** Claims returned only after a signed assertion has been fully verified. */
public record VerifiedExternalSubject(
        String namespace,
        String externalSubject,
        String issuer,
        String jti,
        Instant replayExpiresAt) {

    @Override
    public String toString() {
        return "VerifiedExternalSubject[namespace=" + namespace
                + ", externalSubject=<redacted>, issuer=" + issuer
                + ", jti=<redacted>, replayExpiresAt=" + replayExpiresAt + "]";
    }
}
