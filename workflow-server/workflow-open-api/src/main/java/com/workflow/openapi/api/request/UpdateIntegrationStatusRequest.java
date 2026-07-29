package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateIntegrationStatusRequest(
        @NotBlank String status,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
