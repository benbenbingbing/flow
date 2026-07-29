package com.workflow.openapi.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RevokeIntegrationSecretRequest(
        @NotNull @Min(1) Long expectedSecretVersion) {
}
