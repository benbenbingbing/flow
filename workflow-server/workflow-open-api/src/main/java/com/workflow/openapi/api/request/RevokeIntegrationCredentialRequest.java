package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RevokeIntegrationCredentialRequest(
        @NotNull @PositiveOrZero Long expectedVersion) {
}
