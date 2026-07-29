package com.workflow.openapi.api.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CreateIntegrationApplicationRequest(
        @NotBlank @Size(max = 128) String applicationName,
        @Size(max = 500) String description,
        @Size(max = 64) String ownerOrganizationId,
        @NotEmpty @Size(max = 5) Set<@NotBlank String> scopes,
        @Size(max = 100) Set<@NotBlank @Size(max = 100) String> processKeys,
        @Min(1) @Max(10_000) Integer rateLimitPerMinute,
        @Min(1) @Max(1_000) Integer maxConcurrency,
        @Size(max = 32) List<@NotBlank @Size(max = 64) String> allowedSourceCidrs,
        @Future Instant expiresAt) {
}
