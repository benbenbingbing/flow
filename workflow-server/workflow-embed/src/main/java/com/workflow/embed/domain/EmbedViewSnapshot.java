package com.workflow.embed.domain;

/** Security-relevant Embed View state. */
public record EmbedViewSnapshot(
        String id,
        String viewKey,
        String surfaceType,
        String status,
        String publishedReleaseId,
        long securityVersion) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
