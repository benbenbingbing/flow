package com.workflow.contracts.embed;

import java.time.Instant;
import java.util.Objects;

/**
 * One-time launch credentials returned after a launch is successfully issued.
 */
public record EmbedLaunchIssued(
        String launchId,
        String embedUrl,
        String launchCode,
        Instant expiresAt,
        EmbedLaunchView view,
        String protocolVersion) {

    public EmbedLaunchIssued {
        Objects.requireNonNull(launchId, "launchId");
        Objects.requireNonNull(embedUrl, "embedUrl");
        Objects.requireNonNull(launchCode, "launchCode");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
    }

    @Override
    public String toString() {
        return "EmbedLaunchIssued[launchId=" + launchId
                + ", embedUrl=" + embedUrl
                + ", launchCode=<redacted>"
                + ", expiresAt=" + expiresAt
                + ", view=" + view
                + ", protocolVersion=" + protocolVersion + "]";
    }
}
