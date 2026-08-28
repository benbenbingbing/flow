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
                        issued.view().surfaceType(),
                        issued.view().revision()),
                issued.protocolVersion());
    }

    /**
     * Pinned release metadata that is safe to expose to the host backend.
     */
    public record View(
            String key,
            String surfaceType,
            long revision) {
    }
}
