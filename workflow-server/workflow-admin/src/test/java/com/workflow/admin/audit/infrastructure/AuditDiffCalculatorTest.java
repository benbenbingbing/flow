package com.workflow.admin.audit.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuditDiffCalculatorTest {

    @Test
    void calculatesNestedFieldChanges() {
        AuditDiffCalculator calculator =
                new AuditDiffCalculator(new ObjectMapper().findAndRegisterModules());

        Map<String, Object> changes = calculator.calculate(
                Map.of("name", "旧名称", "config", Map.of("enabled", false)),
                Map.of("name", "新名称", "config", Map.of("enabled", true)));

        assertEquals(
                Map.of("before", "旧名称", "after", "新名称"),
                changes.get("name"));
        assertEquals(
                Map.of("before", false, "after", true),
                changes.get("config.enabled"));
        assertFalse(changes.containsKey("unchanged"));
    }
}
