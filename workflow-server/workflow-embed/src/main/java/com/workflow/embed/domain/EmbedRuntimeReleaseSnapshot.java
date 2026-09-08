package com.workflow.embed.domain;

/** Immutable release and presentation material required by one authenticated runtime request. */
public record EmbedRuntimeReleaseSnapshot(
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
