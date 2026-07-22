package com.workflow.contracts.ui;

import java.util.Map;

public record UiDataSourceContext(
        String usage,
        String entityCode,
        String listKey,
        String userId,
        Map<String, Object> runtimeContext,
        String username,
        String tenantId,
        String organizationId,
        String departmentId,
        String configType,
        String configId,
        String releaseId,
        Integer releaseVersion) {

    public UiDataSourceContext(
            String usage,
            String entityCode,
            String listKey,
            String userId,
            Map<String, Object> runtimeContext) {
        this(
                usage,
                entityCode,
                listKey,
                userId,
                runtimeContext,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
