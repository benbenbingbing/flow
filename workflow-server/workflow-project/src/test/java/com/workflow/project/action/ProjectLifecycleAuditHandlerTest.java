package com.workflow.project.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionRuntimeAccess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectLifecycleAuditHandlerTest {

    @Test
    void exposesGlobalAfterCommitCapabilitiesAndAuditResult() {
        ProjectLifecycleAuditHandler handler =
                new ProjectLifecycleAuditHandler();
        FlowActionContext context =
                new FlowActionContext();
        context.setEntityCode(
                "project_member_change_request");
        context.setEntityDataId("PMCR-1");
        context.setProcessInstanceId("PROC-1");
        context.setOperatorId("1");
        context.setVariablesSnapshot(
                Map.of("approved", "approve"));
        context.setExtraParams(Map.of(
                "auditCode", "F07_MEMBER_CHANGE",
                "businessStage", "MEMBER_EFFECTIVE",
                "legacyField", "ignored"));
        FlowActionRuntimeAccess runtimeAccess =
                mock(FlowActionRuntimeAccess.class);
        when(runtimeAccess.convertParams(
                anyMap(),
                eq(ProjectLifecycleAuditHandler
                        .Parameters.class)))
                .thenAnswer(invocation ->
                        new ObjectMapper().convertValue(
                                invocation.getArgument(0),
                                ProjectLifecycleAuditHandler
                                        .Parameters.class));
        context.setRuntimeAccess(runtimeAccess);

        handler.execute(context);

        assertEquals(
                "AFTER_COMMIT",
                handler.recommendedExecutionMode());
        assertEquals(
                List.of(
                        "auditCode",
                        "businessStage"),
                handler.extraParamSchema()
                        .get("required"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) context
                        .getExecutionResult();
        assertEquals(
                "F07_MEMBER_CHANGE",
                result.get("auditCode"));
        assertEquals(
                "MEMBER_EFFECTIVE",
                result.get("businessStage"));
        assertEquals(
                "approve",
                result.get("approved"));
        assertEquals(
                "PROJECT_LIFECYCLE_AUDITED",
                context.getExecutionTrace()
                        .get(0)
                        .get("stage"));
        assertTrue(handler.retryable());
    }

    @Test
    void preservesDefaultsForMissingTypedParameters() {
        ProjectLifecycleAuditHandler handler =
                new ProjectLifecycleAuditHandler();
        FlowActionContext context =
                new FlowActionContext();
        FlowActionRuntimeAccess runtimeAccess =
                mock(FlowActionRuntimeAccess.class);
        when(runtimeAccess.convertParams(
                anyMap(),
                eq(ProjectLifecycleAuditHandler
                        .Parameters.class)))
                .thenReturn(
                        new ProjectLifecycleAuditHandler
                                .Parameters(null, null));
        context.setRuntimeAccess(runtimeAccess);
        context.setExtraParams(Map.of());

        handler.execute(context);

        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) context
                        .getExecutionResult();
        assertEquals(
                "PROJECT_LIFECYCLE",
                result.get("auditCode"));
        assertEquals(
                "UNKNOWN",
                result.get("businessStage"));
    }
}
