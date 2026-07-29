package com.workflow.openapi.api.response;

public record IssuedWebhookSecretView(
        WebhookEndpointView endpoint,
        String signingSecret) {
}
