package com.workflow.openapi.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateIntegrationWorkflowScenarioRequest(
        @NotNull @Positive Long expectedRevision,
        @NotNull @Valid CreateIntegrationWorkflowScenarioRequest configuration) {
}
