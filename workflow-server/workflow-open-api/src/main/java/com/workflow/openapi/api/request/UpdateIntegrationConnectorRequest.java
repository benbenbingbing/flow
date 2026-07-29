package com.workflow.openapi.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateIntegrationConnectorRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 128) String configName,
        @NotBlank
        @Pattern(regexp = "^(?i:ACTIVE|DISABLED)$")
        String status,
        @NotNull JsonNode configuration,
        @NotEmpty @Size(max = 100)
        List<@NotBlank @Size(max = 253) String> allowedHosts) {
}
