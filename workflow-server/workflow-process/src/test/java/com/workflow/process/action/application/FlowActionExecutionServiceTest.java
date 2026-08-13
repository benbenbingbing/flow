package com.workflow.process.action.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionCatalogPort;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionTraceFields;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionExecutionMapper;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FlowActionExecutionServiceTest {

    @Test
    void capturesHandlerTraceUsingStableContractFields()
            throws Exception {
        FlowActionExecutionMapper executionMapper =
                mock(FlowActionExecutionMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        FlowActionExecutionService service =
                new FlowActionExecutionService(
                        executionMapper,
                        mock(FlowActionMapper.class),
                        objectMapper,
                        mock(FlowActionCatalogPort.class));
        FlowActionExecution execution =
                new FlowActionExecution();
        FlowActionContext context =
                new FlowActionContext();
        context.addExecutionTrace(
                "HANDLER_VALIDATED",
                "处理器校验完成",
                Map.of("count", 2));

        service.captureContext(execution, context);

        verify(executionMapper).updateById(execution);
        List<Map<String, Object>> trace =
                objectMapper.readValue(
                        execution.getExecutionTraceJson(),
                        new TypeReference<>() {
                        });
        Map<String, Object> captured = trace.get(0);
        assertEquals(
                "HANDLER_VALIDATED",
                captured.get(FlowActionTraceFields.STAGE));
        assertEquals(
                "处理器校验完成",
                captured.get(FlowActionTraceFields.MESSAGE));
        assertEquals(
                Map.of("count", 2),
                captured.get(FlowActionTraceFields.DETAILS));
    }
}
