package com.workflow.openapi.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record IntegrationProcessContractRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{0,99}$")
        String processKey,
        @NotNull JsonNode inputSchema,
        @NotNull
        @Size(max = 100)
        Set<
                @NotBlank
                @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{0,127}$")
                String> allowedMessageKeys) {
}
