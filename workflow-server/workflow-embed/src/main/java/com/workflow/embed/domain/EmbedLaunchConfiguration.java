package com.workflow.embed.domain;

/** Complete, read-only launch configuration resolved by application id and stable view key. */
public record EmbedLaunchConfiguration(
        EmbedApplicationSnapshot application,
        EmbedViewSnapshot view,
        EmbedReleaseSnapshot release,
        EmbedGrantSnapshot grant) {
}
