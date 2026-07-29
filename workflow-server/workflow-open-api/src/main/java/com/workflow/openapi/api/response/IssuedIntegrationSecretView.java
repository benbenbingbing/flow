package com.workflow.openapi.api.response;

public record IssuedIntegrationSecretView(
        IntegrationSecretView secret,
        String secretValue,
        String secretReference) {
}
