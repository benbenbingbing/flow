package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplayWebhookDeliveryRequest(
        @NotBlank @Size(max = 256) String reason) {
}
