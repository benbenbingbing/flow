package com.workflow.contracts.embed;

import java.util.Objects;

/**
 * Immutable view release information pinned to an issued launch.
 */
public record EmbedLaunchView(
        String key,
        String surfaceType,
        long revision) {

    public EmbedLaunchView {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(surfaceType, "surfaceType");
    }
}
