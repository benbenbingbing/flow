package com.workflow.openapi.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RotateWebhookSecretRequest(
        @NotNull @Min(0) Long expectedVersion) {
}
