package com.workflow.openapi.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateWebhookEndpointRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 128) String endpointName,
        @NotBlank @Size(max = 2048) String endpointUrl,
        @NotBlank String status,
        @NotEmpty @Size(max = 6) Set<
                @NotBlank @Size(max = 128) String> eventTypes) {
}
