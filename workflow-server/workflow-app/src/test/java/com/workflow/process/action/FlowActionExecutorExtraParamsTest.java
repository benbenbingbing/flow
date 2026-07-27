package com.workflow.process.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.FlowAction;
import com.workflow.service.FlowActionExecutionService;
import com.workflow.service.FlowActionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowActionExecutorExtraParamsTest {

    @Test
    void exposesResolvedParamsThroughNewAndLegacyNames() {
        FlowActionService actionService = mock(FlowActionService.class);
        ApplicationContext applicationContext =
                mock(ApplicationContext.class);
        FlowActionHelper helper = mock(FlowActionHelper.class);
        FlowActionExecutionService executionService =
                mock(FlowActionExecutionService.class);
        AtomicReference<FlowActionContext> captured =
                new AtomicReference<>();
        FlowActionHandler handler = captured::set;
        when(applicationContext.getBean("sampleAction"))
                .thenReturn(handler);

        FlowActionExecutor executor = new FlowActionExecutor(
                actionService,
                applicationContext,
                helper,
                executionService,
                new ObjectMapper());
        FlowAction action = new FlowAction();
        action.setId("action-1");
        action.setActionName("示例动作");
        action.setInterfaceName("sampleAction");
        action.setParamsJson("""
                {
                  "fixed": "value",
                  "fromVariable": "${departmentCode}"
                }
                """);
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();
        event.setProcessInstanceId("process-1");
        event.setVariables(Map.of("departmentCode", "D001"));

        FlowActionContext result = executor.executeAction(
                action,
                event,
                "idempotency-1");

        assertSame(result, captured.get());
        assertSame(result.getExtraParams(), result.getCustomParams());
        assertEquals(
                Map.of(
                        "fixed", "value",
                        "fromVariable", "D001"),
                result.getExtraParams());
    }
}
