package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "businessStage", "MEMBER_EFFECTIVE"));

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
}
