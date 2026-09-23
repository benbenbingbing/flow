package com.workflow.process.action.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FlowActionTraceFields;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.process.action.domain.FlowActionTriggerEvent;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionExecutionMapper;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import org.mockito.ArgumentCaptor;

class FlowActionExecutionServiceTest {

    @Test
    void persistsCurrentExtraParamsAtHandlerStartAndCapture() throws Exception {
        FlowActionExecutionMapper executionMapper = mock(FlowActionExecutionMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        FlowActionExecutionService service = new FlowActionExecutionService(
                executionMapper,
                mock(FlowActionMapper.class),
                objectMapper,
                mock(FlowActionCatalogPort.class),
                mock(SystemAuditPort.class));
        FlowActionExecution execution = new FlowActionExecution();
        FlowActionContext context = new FlowActionContext();
        context.setExtraParams(Map.of("key1", "value", "token", "private-value"));

        service.markHandlerStarted(execution, context);

        assertEquals(
                objectMapper.valueToTree(Map.of("key1", "value", "token", "******")),
                objectMapper.readTree(execution.getResolvedParamsJson()));
        assertEquals("private-value", context.getExtraParams().get("token"));

        // 模拟处理器整体替换参数，确保最终快照与处理器读取的唯一入口一致。
        context.setExtraParams(Map.of("key1", "updated"));
        service.captureContext(execution, context);

        assertEquals(
                objectMapper.valueToTree(Map.of("key1", "updated")),
                objectMapper.readTree(execution.getResolvedParamsJson()));
        verify(executionMapper, org.mockito.Mockito.times(2)).updateById(execution);
    }

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
                        mock(FlowActionCatalogPort.class),
                        mock(SystemAuditPort.class));
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

    @Test
    void creationPersistsOperationContextAndSourcePointer() {
        FlowActionExecutionMapper executionMapper =
                mock(FlowActionExecutionMapper.class);
        doAnswer(invocation -> {
            FlowActionExecution value = invocation.getArgument(0);
            value.setId("execution-1");
            return 1;
        }).when(executionMapper).insert(
                org.mockito.ArgumentMatchers.any(
                        FlowActionExecution.class));
        FlowActionCatalogPort catalog =
                mock(FlowActionCatalogPort.class);
        when(catalog.displayName("projectHandler"))
                .thenReturn("项目处理器");
        SystemAuditPort auditPort = mock(SystemAuditPort.class);
        FlowActionExecutionService service =
                new FlowActionExecutionService(
                        executionMapper,
                        mock(FlowActionMapper.class),
                        new ObjectMapper().findAndRegisterModules(),
                        catalog,
                        auditPort);
        FlowAction action = new FlowAction();
        action.setId("action-1");
        action.setActionName("同步项目状态");
        action.setInterfaceName("projectHandler");
        FlowActionTriggerEvent event =
                new FlowActionTriggerEvent();
        event.setOperationId("operation-1");
        event.setTraceId("trace-1");
        event.setProcessInstanceId("process-1");
        event.setOperatorId("user-1");
        event.setOperatorName("张三");

        FlowActionExecution execution = service.create(
                action,
                event,
                "idempotency-1",
                FlowActionExecution.Status.PENDING);

        assertEquals("execution-1", execution.getId());
        FlowActionTriggerEvent stored = service.readEvent(execution);
        assertEquals("operation-1", stored.getOperationId());
        assertEquals("trace-1", stored.getTraceId());
        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        assertEquals("operation-1", audit.getValue().operationId());
        assertEquals("PROCESS_INSTANCE", audit.getValue().targetType());
        assertEquals("process-1", audit.getValue().targetId());
        assertEquals("FLOW_ACTION_EXECUTION",
                audit.getValue().sourcePointer().sourceType());
        assertEquals("execution-1",
                audit.getValue().sourcePointer().sourceId());
    }
}
