package com.workflow.contracts.embed;

/**
 * Optional presentation preferences for an Embed launch.
 */
public record EmbedLaunchUi(
        String locale,
        String theme,
        String formPresentation) {

    /**
     * Preserves source compatibility for callers that do not select a form presentation.
     * The launch service applies the canonical seamless default at the security boundary.
     */
    public EmbedLaunchUi(String locale, String theme) {
        this(locale, theme, null);
    }
}
