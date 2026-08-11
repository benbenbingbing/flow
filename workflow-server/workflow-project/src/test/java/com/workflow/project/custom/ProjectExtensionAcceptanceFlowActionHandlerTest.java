package com.workflow.project.custom;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.project.service.ProjectEntityMutationExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProjectExtensionAcceptanceFlowActionHandlerTest {

    @Test
    void logsAndWritesVisibleStageBackToAcceptanceEntity() {
        ProjectEntityMutationExecutor mutationExecutor =
                mock(ProjectEntityMutationExecutor.class);
        doAnswer(invocation -> {
            Supplier<?> action =
                    invocation.getArgument(3);
            return action.get();
        }).when(mutationExecutor).inSession(
                any(),
                anyString(),
                anyString(),
                any());

        ProjectExtensionAcceptanceFlowActionHandler handler =
                new ProjectExtensionAcceptanceFlowActionHandler(
                        mutationExecutor);
        FlowActionContext context =
                new FlowActionContext();
        context.setActionId("ACTION-1");
        context.setActionName("技术复核完成");
        context.setScopeType("NODE");
        context.setTriggerTiming("NODE_COMPLETED");
        context.setElementId("technical_review");
        context.setProcessInstanceId("PROC-1");
        context.setEntityCode(
                ProjectExtensionAcceptanceFlowActionHandler
                        .ENTITY_CODE);
        context.setEntityDataId("RECORD-1");
        context.setOperatorId("USER-1");
        context.setExtraParams(Map.of(
                "stage", "TECHNICAL_REVIEW",
                "visibleMessage", "技术复核扩展已执行",
                "writeBack", true));

        handler.execute(context);

        verify(mutationExecutor).update(
                eq(ProjectExtensionAcceptanceFlowActionHandler
                        .ENTITY_CODE),
                eq("RECORD-1"),
                any());
        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) context
                        .getExecutionResult();
        assertEquals(
                "TECHNICAL_REVIEW",
                result.get("stage"));
        assertEquals(true,
                result.get("writeBack"));
        assertEquals(
                List.of(
                        "extension_result",
                        "backend_trace",
                        "last_action_scope",
                        "last_action_timing",
                        "last_action_element"),
                result.get("writeBackFields"));
        assertEquals(
                "PROJECT_EXTENSION_ACCEPTANCE_TECHNICAL_REVIEW",
                context.getExecutionTrace()
                        .get(0)
                        .get("stage"));
        assertTrue(handler.retryable());
    }
}
