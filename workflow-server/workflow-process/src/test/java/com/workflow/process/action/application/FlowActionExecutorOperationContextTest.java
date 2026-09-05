package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import com.workflow.contracts.audit.OperationContext;
import com.workflow.contracts.audit.OperationContextHolder;
import com.workflow.process.action.domain.FlowActionTriggerEvent;
import com.workflow.process.action.infrastructure.flowable.FlowActionRuntimeAdapter;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowActionExecutorOperationContextTest {

    @Test
    void restoresPersistedOperationContextAroundAsyncHandler() {
        AtomicReference<OperationContext> observed =
                new AtomicReference<>();
        // 已发布的旧动作 Handler 必须仍能注入到 canonical SPI 消费端。
        FlowActionHandler handler = new com.workflow.contracts.process.action.spi.FlowActionHandler() {
            @Override
            public void execute(FlowActionContext context) {
                observed.set(OperationContextHolder.current()
                        .orElseThrow());
            }
        };
        ApplicationContext applicationContext =
                mock(ApplicationContext.class);
        when(applicationContext.getBean("projectHandler"))
                .thenReturn(handler);
        FlowActionExecutor executor = new FlowActionExecutor(
                mock(FlowActionService.class),
                applicationContext,
                mock(FlowActionRuntimeAdapter.class),
                mock(FlowActionExecutionService.class),
                new ObjectMapper().findAndRegisterModules());

        FlowAction action = new FlowAction();
        action.setId("flow-action-1");
        action.setActionName("同步项目状态");
        action.setInterfaceName("projectHandler");
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();
        event.setOperationId("persisted-operation-1");
        event.setTraceId("persisted-trace-1");
        event.setParentOperationId("parent-operation-1");

        executor.executeAction(
                action, event, "idempotency-1");

        assertEquals("persisted-operation-1",
                observed.get().operationId());
        assertEquals("persisted-trace-1",
                observed.get().traceId());
        assertEquals("parent-operation-1",
                observed.get().parentOperationId());
        assertEquals("FLOW_ACTION",
                observed.get().sourcePointer().sourceType());
        assertEquals("flow-action-1",
                observed.get().sourcePointer().sourceId());
        assertTrue(OperationContextHolder.current().isEmpty());
    }
}
