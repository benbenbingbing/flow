package com.workflow.contracts.embed;

import java.util.Objects;

/** Stable Embed view identity selected for an issued launch. */
public record EmbedLaunchView(
        String key,
        String surfaceType) {

    public EmbedLaunchView {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(surfaceType, "surfaceType");
    }
}
