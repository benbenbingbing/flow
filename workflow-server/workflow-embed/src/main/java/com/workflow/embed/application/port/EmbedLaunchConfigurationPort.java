package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedLaunchConfiguration;
import java.time.Instant;
import java.util.Optional;

/** Reads the application/view/grant plus the View's current stable resource config. */
public interface EmbedLaunchConfigurationPort {

    /**
     * Performs the non-locking preflight read used before the independent Launch quota transaction.
     */
    Optional<EmbedLaunchConfiguration> find(
            String applicationId,
            String viewKey,
            Instant now);

    /**
     * Locks and reloads the complete security configuration used by the final Launch transaction.
     */
    Optional<EmbedLaunchConfiguration> lockForUpdate(
            String applicationId,
            String viewKey,
            Instant now);
}
