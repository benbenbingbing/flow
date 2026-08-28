package com.workflow.contracts.embed;

/**
 * Optional presentation preferences for an Embed launch.
 */
public record EmbedLaunchUi(
        String locale,
        String theme) {
}
