package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedLaunchConfiguration;
import java.time.Instant;
import java.util.Optional;

/** Reads the exact application/view/grant/release configuration used to issue a launch. */
public interface EmbedLaunchConfigurationPort {

    Optional<EmbedLaunchConfiguration> find(
            String applicationId,
            String viewKey,
            Instant now);
}
