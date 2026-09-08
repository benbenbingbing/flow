package com.workflow.migration.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 验证旧配置包的流程动作绑定仅在导入边界转换为规范字段。 */
class ConfigMigrationFlowActionImportSupportTest {

    @Test
    void convertsLegacySequenceFlowAndProcessBindings() {
        Map<String, Object> sequenceFlow = legacyAction("Flow_legacy");
        Map<String, Object> process = legacyAction("__PROCESS__");

        Map<String, Object> normalizedSequence =
                ConfigMigrationImportApplyService.normalizeFlowActionBinding(
                        sequenceFlow);
        Map<String, Object> normalizedProcess =
                ConfigMigrationImportApplyService.normalizeFlowActionBinding(
                        process);

        assertEquals("SEQUENCE_FLOW", normalizedSequence.get("scopeType"));
        assertEquals("Flow_legacy", normalizedSequence.get("elementId"));
        assertEquals("PROCESS", normalizedProcess.get("scopeType"));
        assertNull(normalizedProcess.get("elementId"));
        assertFalse(normalizedSequence.containsKey("sequenceFlowId"));
        assertFalse(normalizedSequence.containsKey("methodName"));
    }

    @Test
    void keepsCanonicalBindingWhenLegacyKeysAreAlsoPresent() {
        Map<String, Object> action = legacyAction("Flow_obsolete");
        action.put("scopeType", "NODE");
        action.put("elementId", "Activity_current");

        Map<String, Object> normalized =
                ConfigMigrationImportApplyService.normalizeFlowActionBinding(
                        action);

        assertEquals("NODE", normalized.get("scopeType"));
        assertEquals("Activity_current", normalized.get("elementId"));
        assertFalse(normalized.containsKey("sequenceFlowId"));
        assertFalse(normalized.containsKey("methodName"));
    }

    private Map<String, Object> legacyAction(String sequenceFlowId) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("sequenceFlowId", sequenceFlowId);
        action.put("methodName", "execute");
        return action;
    }
}
