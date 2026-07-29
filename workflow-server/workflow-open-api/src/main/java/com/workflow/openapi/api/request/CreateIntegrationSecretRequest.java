package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateIntegrationSecretRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]*$")
        String secretName,
        @Size(min = 8, max = 65536)
        String secretValue) {
}
