package com.workflow.contracts.embed;

/**
 * Issues a short-lived, one-time Embed launch for a verified application.
 */
public interface EmbedLaunchIssuePort {

    /**
     * Validates the application launch request and issues its one-time secret.
     *
     * @param application authenticated machine application identity
     * @param command stable launch request business data
     * @return launch information; the code is returned only through this result
     */
    EmbedLaunchIssued issue(
            EmbedApplicationActor application,
            EmbedLaunchCommand command);
}
