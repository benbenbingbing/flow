package com.workflow.openapi.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RotateIntegrationSecretRequest(
        @NotNull @Min(1) Long expectedSecretVersion,
        @Size(min = 8, max = 65536) String secretValue) {
}
