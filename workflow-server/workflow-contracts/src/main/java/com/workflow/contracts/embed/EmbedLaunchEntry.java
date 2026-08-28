package com.workflow.contracts.embed;

import java.util.Objects;

/**
 * Entry point requested for an Embed launch.
 */
public record EmbedLaunchEntry(
        String mode,
        String recordId) {

    public EmbedLaunchEntry {
        Objects.requireNonNull(mode, "mode");
    }
}
