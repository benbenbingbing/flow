package com.workflow.embed.infrastructure.persistence.record;

/** Flat database projection for runtime release bootstrap and schema rendering. */
public record EmbedRuntimeReleaseRow(
        String releaseId,
        String viewId,
        String viewKey,
        String viewName,
        long revision,
        String surfaceType,
        String entityCode,
        String listKey,
        String listReleaseId,
        Integer listReleaseVersion,
        String formReleaseId,
        Integer formReleaseVersion,
        String capabilitiesJson,
        String fieldPolicyJson,
        String actionPolicyJson,
        String contextBindingsJson,
        String uiConfigJson,
        String configJson,
        String actorDisplayName,
        String uiLocale,
        String uiTheme,
        String uiFormPresentation) {
}
