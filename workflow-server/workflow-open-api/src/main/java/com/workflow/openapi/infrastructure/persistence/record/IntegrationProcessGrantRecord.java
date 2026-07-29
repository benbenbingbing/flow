package com.workflow.openapi.infrastructure.persistence.record;

public record IntegrationProcessGrantRecord(
        String applicationId,
        String processKey,
        String inputSchemaJson,
        String allowedMessageKeys) {
}
