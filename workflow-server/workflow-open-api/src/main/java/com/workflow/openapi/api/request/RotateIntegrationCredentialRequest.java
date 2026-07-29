package com.workflow.openapi.api.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record RotateIntegrationCredentialRequest(
        @Future Instant expiresAt,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
