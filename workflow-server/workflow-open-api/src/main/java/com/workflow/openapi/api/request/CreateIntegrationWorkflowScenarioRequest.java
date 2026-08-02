package com.workflow.openapi.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateIntegrationWorkflowScenarioRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{0,99}$")
        String scenarioKey,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{0,99}$")
        String processKey,
        @Min(1) Integer processDefinitionVersion,
        @NotNull JsonNode inputSchema,
        @NotNull JsonNode outcomeMapping,
        @NotNull JsonNode identityMapping,
        @NotNull @Size(max = 20) Set<@NotBlank String> eventTypes) {
}
