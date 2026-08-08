package com.workflow.openapi.api.response;

/** Result of validating a scenario configuration without persisting it. */
public record IntegrationWorkflowScenarioValidationView(
        boolean valid,
        String scenarioKey,
        String processKey,
        Integer processDefinitionVersion,
        String configHash) {
}
