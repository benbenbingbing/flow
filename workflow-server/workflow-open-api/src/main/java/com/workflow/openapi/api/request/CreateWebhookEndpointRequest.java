package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateWebhookEndpointRequest(
        @NotBlank @Size(max = 128) String endpointName,
        @NotBlank @Size(max = 2048) String endpointUrl,
        @NotEmpty @Size(max = 6) Set<
                @NotBlank @Size(max = 128) String> eventTypes) {
}
