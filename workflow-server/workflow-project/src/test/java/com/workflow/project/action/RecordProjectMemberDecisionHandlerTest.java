package com.workflow.project.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionRuntimeAccess;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectMemberChangeService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordProjectMemberDecisionHandlerTest {

    @Test
    void convertsLegacyMapParametersAndRecordsDecision() {
        ProjectMemberChangeService service =
                mock(ProjectMemberChangeService.class);
        RecordProjectMemberDecisionHandler handler =
                new RecordProjectMemberDecisionHandler(
                        service);
        EntityDataDTO request = new EntityDataDTO();
        request.setId("REQUEST-1");
        FlowActionContext context =
                new FlowActionContext();
        context.setExtraParams(Map.of(
                "decision", "APPROVE",
                "legacyField", "ignored"));
        FlowActionRuntimeAccess runtimeAccess =
                mock(FlowActionRuntimeAccess.class);
        when(runtimeAccess.convertParams(
                anyMap(),
                eq(RecordProjectMemberDecisionHandler
                        .Parameters.class)))
                .thenAnswer(invocation ->
                        new ObjectMapper().convertValue(
                                invocation.getArgument(0),
                                RecordProjectMemberDecisionHandler
                                        .Parameters.class));
        when(runtimeAccess.getEntityData(null, null))
                .thenReturn(request);
        context.setRuntimeAccess(runtimeAccess);
        when(service.recordDecision(
                request,
                context,
                "APPROVE"))
                .thenReturn(Map.of(
                        "decision",
                        "APPROVE"));

        handler.execute(context);

        verify(service).recordDecision(
                request,
                context,
                "APPROVE");
        assertEquals(
                Map.of("decision", "APPROVE"),
                context.getExecutionResult());
        assertEquals(
                "FINAL_DECISION_RECORDED",
                context.getExecutionTrace()
                        .get(0)
                        .get("stage"));
    }
}
