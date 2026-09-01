package com.workflow.embed.domain;

/** Current launch configuration resolved by application id and stable view key. */
public record EmbedLaunchConfiguration(
        EmbedApplicationSnapshot application,
        EmbedViewSnapshot view,
        String currentConfigJson,
        EmbedGrantSnapshot grant) {
}
