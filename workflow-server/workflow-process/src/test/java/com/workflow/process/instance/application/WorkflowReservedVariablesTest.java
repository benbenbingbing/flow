package com.workflow.process.instance.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowReservedVariablesTest {

    @Test
    void untrustedBoundariesProtectTheWholePlatformNamespace() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("businessField", "kept");
        source.put("_wfNextApproverOverrides_", Map.of(
                "approve", Map.of("usernames", java.util.List.of(
                        "attacker"))));
        source.put("_wfFutureSecurityContext", "forged");
        source.put("_wfInitiatorOrgSnapshotV1", "forged");
        source.put("initiator", "attacker");
        source.put("skipNodeEnabled", false);
        source.put("_FLOWABLE_SKIP_EXPRESSION_ENABLED", false);
        source.put("_ACTIVITI_SKIP_EXPRESSION_ENABLED", false);

        Map<String, Object> sanitized =
                WorkflowReservedVariables.sanitizeRuntimeMutation(source);

        assertEquals(Map.of("businessField", "kept"), sanitized);
        // 边界过滤不得改写请求对象，服务端内部仍可在自己的变量容器写入。
        assertEquals("forged", source.get("_wfFutureSecurityContext"));
    }

    @Test
    void externalProjectionHidesEveryInternalVariableButKeepsBusinessData() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("businessField", 42);
        variables.put("_wfNextApproverOverrides_", "internal");
        variables.put("_wf_mi_approved_count_task", 1);
        variables.put("skipNodeEnabled", true);
        variables.put("_FLOWABLE_SKIP_EXPRESSION_ENABLED", true);
        variables.put("_ACTIVITI_SKIP_EXPRESSION_ENABLED", true);

        WorkflowReservedVariables.removeInternalVariables(variables);

        assertEquals(Map.of("businessField", 42), variables);
        assertFalse(WorkflowReservedVariables.isInternalVariable(
                "businessField"));
    }

    @Test
    void nativeSkipSwitchIsWrittenOnlyByTheTrustedBoundary() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("businessField", "kept");
        variables.put(
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE,
                false);

        WorkflowReservedVariables.enableNativeSkipExpressions(variables);

        assertEquals("kept", variables.get("businessField"));
        assertEquals(
                true,
                variables.get(WorkflowReservedVariables
                        .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE));
        assertEquals(
                true,
                variables.get(WorkflowReservedVariables
                        .LEGACY_SKIP_NODE_ENABLED_VARIABLE));
        assertFalse(variables.containsKey(WorkflowReservedVariables
                .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE));
        assertTrue(WorkflowReservedVariables.isProtectedContextVariable(
                WorkflowReservedVariables
                        .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE));
    }
}
