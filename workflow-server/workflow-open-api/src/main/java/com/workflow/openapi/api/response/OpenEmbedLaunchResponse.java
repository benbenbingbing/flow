package com.workflow.openapi.api.response;

import com.workflow.contracts.embed.EmbedLaunchIssued;
import java.time.Instant;

/**
 * HTTP representation of a newly issued, one-time Embed launch.
 */
public record OpenEmbedLaunchResponse(
        String launchId,
        String embedUrl,
        String launchCode,
        Instant expiresAt,
        View view,
        String protocolVersion) {

    /**
     * Maps the stable Embed result to the Open API response envelope payload.
     */
    public static OpenEmbedLaunchResponse from(EmbedLaunchIssued issued) {
        return new OpenEmbedLaunchResponse(
                issued.launchId(),
                issued.embedUrl(),
                issued.launchCode(),
                issued.expiresAt(),
                new View(
                        issued.view().key(),
                        issued.view().surfaceType()),
                issued.protocolVersion());
    }

    /**
     * Stable Embed view identity selected for this launch.
     *
     * <p>The internal runtime snapshot is deliberately not part of the host
     * contract: a host stores the stable view key, while Flow resolves and
     * pins the current snapshot for each newly issued launch.</p>
     */
    public record View(
            String key,
            String surfaceType) {
    }
}
