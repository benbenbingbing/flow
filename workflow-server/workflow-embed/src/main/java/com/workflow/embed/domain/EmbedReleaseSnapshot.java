package com.workflow.embed.domain;

/** Immutable release material needed during launch and exchange. */
public record EmbedReleaseSnapshot(
        String id,
        long revision,
        String surfaceType,
        String entryModesJson,
        String capabilitiesJson,
        String contextSchemaJson,
        String uiConfigJson) {
}
