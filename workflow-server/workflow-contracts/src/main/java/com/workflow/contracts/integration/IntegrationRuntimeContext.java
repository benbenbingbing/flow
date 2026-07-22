package com.workflow.contracts.integration;

public record IntegrationRuntimeContext(
        String sourceId,
        String usage,
        String configType,
        String configId,
        String releaseId,
        Integer releaseVersion,
        String entityId,
        String entityCode,
        String listKey,
        String userId,
        String username,
        String tenantId,
        String organizationId,
        String departmentId) {
}
