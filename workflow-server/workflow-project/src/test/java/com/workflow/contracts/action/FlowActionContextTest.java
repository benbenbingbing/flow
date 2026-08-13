package com.workflow.contracts.action;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FlowActionContextTest {

    @Test
    void writesProcessVariablesAndRefreshesSnapshot() {
        FlowActionRuntimeAccess runtimeAccess =
                mock(FlowActionRuntimeAccess.class);
        FlowActionContext context =
                new FlowActionContext();
        context.setProcessInstanceId("PROC-1");
        context.setRuntimeAccess(runtimeAccess);
        context.setVariablesSnapshot(
                Map.of("existing", "value"));
        Map<String, Object> routeVariables =
                new LinkedHashMap<>();
        routeVariables.put(
                "access_review_required_flag", true);
        routeVariables.put(
                "security_review_required_flag", false);

        context.setProcessVariables(routeVariables);
        context.setProcessVariable(
                "handover_required_flag", true);

        verify(runtimeAccess).setVariables(
                "PROC-1", routeVariables);
        verify(runtimeAccess).setVariable(
                "PROC-1",
                "handover_required_flag",
                true);
        assertEquals(
                true,
                context.getVariable(
                        "access_review_required_flag"));
        assertEquals(
                false,
                context.getVariable(
                        "security_review_required_flag"));
        assertEquals(
                true,
                context.getVariable(
                        "handover_required_flag"));
        assertEquals(
                "value",
                context.getVariable("existing"));
    }

    @Test
    void keepsExecutionTraceMapShapeStable() {
        FlowActionContext context =
                new FlowActionContext();

        context.addExecutionTrace(
                "VALIDATED",
                "校验完成",
                Map.of("count", 2));

        Map<String, Object> trace =
                context.getExecutionTrace().get(0);
        assertEquals(
                "VALIDATED",
                trace.get(FlowActionTraceFields.STAGE));
        assertEquals(
                "校验完成",
                trace.get(FlowActionTraceFields.MESSAGE));
        assertEquals(
                Map.of("count", 2),
                trace.get(FlowActionTraceFields.DETAILS));
        assertFalse(trace.containsKey("time"));
    }
}
