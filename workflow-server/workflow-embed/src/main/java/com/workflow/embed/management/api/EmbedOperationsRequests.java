package com.workflow.embed.management.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Launch/Session 撤销请求契约。 */
public final class EmbedOperationsRequests {

    private EmbedOperationsRequests() {
    }

    public record RevokeRequest(
            @Size(max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+")
            String reason) {
    }

    public record BulkRevokeRequest(
            @Size(max = 128)
            @Pattern(regexp = "[A-Za-z0-9._:-]+")
            String reason,
            @Size(max = 256)
            @Pattern(regexp = "[A-Za-z0-9_-]+")
            String cursor,
            @Min(1) @Max(200) Integer limit) {
    }
}
