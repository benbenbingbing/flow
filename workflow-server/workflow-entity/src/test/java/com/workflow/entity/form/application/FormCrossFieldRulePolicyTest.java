package com.workflow.entity.form.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormCrossFieldRulePolicyTest {
    private final Map<String, Object> rule = Map.of("id", "end_after_start", "operator", "GE", "targetFieldCode", "startTime", "message", "结束时间不能早于开始时间");
    private final Map<String, Object> config = Map.of("version", 1, "rules", List.of(rule));

    @Test
    void matchesSharedBrowserComparisonCases() throws Exception {
        Path directory = Path.of("").toAbsolutePath();
        Path fixture = null;
        while (directory != null) {
            Path candidate = directory.resolve("docs/testing/fixtures/form-cross-field-comparisons.json");
            if (Files.isRegularFile(candidate)) { fixture = candidate; break; }
            directory = directory.getParent();
        }
        assertNotNull(fixture, "找不到前后端共用比较样例");
        List<Map<String, Object>> cases = new ObjectMapper().readValue(Files.readString(fixture), new TypeReference<>() {});
        assertEquals(32, cases.size());
        for (Map<String, Object> item : cases) {
            String type = (String) item.get("type");
            String targetType = (String) item.getOrDefault("targetType", type);
            if (Boolean.TRUE.equals(item.get("invalid"))) {
                assertThrows(IllegalArgumentException.class, () -> CrossFieldValueComparator.compare(item.get("left"), item.get("right"), type, targetType), item.toString());
            } else {
                assertEquals(item.get("expected"), CrossFieldValueComparator.compare(item.get("left"), item.get("right"), type, targetType), item.toString());
            }
        }
        assertEquals(0, CrossFieldValueComparator.compare(LocalDateTime.of(2026, 9, 15, 10, 0), "2026-09-15 10:00:00", "DATETIME", "DATETIME"));
    }

    @Test
    void checksOperatorsReferencesAndConfigurationKeys() {
        Map<String, String> fields = Map.of("startTime", "DATETIME", "endTime", "DATETIME");
        assertDoesNotThrow(() -> FormCrossFieldRulePolicy.validateReferences(config, "endTime", fields));
        assertThrows(IllegalArgumentException.class, () -> FormCrossFieldRulePolicy.validateReferences(config, "startTime", fields));
        assertThrows(IllegalArgumentException.class, () -> FormCrossFieldRulePolicy.validateReferences(config, "endTime", Map.of("endTime", "DATETIME")));
        assertThrows(IllegalArgumentException.class, () -> FormCrossFieldRulePolicy.validateReferences(config, "endTime", Map.of("endTime", "DATETIME", "startTime", "DATE")));
        assertThrows(IllegalArgumentException.class, () -> FormCrossFieldRulePolicy.parse(config, "STRING"));
        assertThrows(IllegalArgumentException.class, () -> FormCrossFieldRulePolicy.parse(Map.of("version", 2, "rules", List.of(rule)), "DATETIME"));
        for (Map<String, Object> addition : List.<Map<String, Object>>of(
                Map.of("enabled", false), Map.of("modes", List.of("edit")),
                Map.of("targetFieldCode", "data.startTime"), Map.of("message", "长".repeat(201)),
                Map.of("operator", "script"))) {
            Map<String, Object> invalid = new LinkedHashMap<>(rule);
            invalid.putAll(addition);
            assertThrows(IllegalArgumentException.class, () -> FormCrossFieldRulePolicy.parse(Map.of("version", 1, "rules", List.of(invalid)), "DATETIME"));
        }
        assertThrows(IllegalArgumentException.class, () -> FormCrossFieldRulePolicy.parse(Map.of("version", 1, "rules", List.of(rule, rule)), "DATETIME"));
        assertTrue(FormCrossFieldRulePolicy.passes("GE", 0));
        assertFalse(FormCrossFieldRulePolicy.passes("GT", 0));
        assertFalse(FormCrossFieldRulePolicy.passes("NE", 0));
    }

    @Test
    void nodeNormalizationPreservesRulesAndExplicitRemoval() {
        for (Map<String, Object> value : List.of(config, Map.<String, Object>of("version", 1, "rules", List.of()))) {
            Map<String, Object> document = Map.of("validation", Map.of("crossField", value));
            var normalized = EntityFormNodePropertyPolicy.normalizeRules("FIELD", document, Map.of("fieldType", "DATETIME"), false);
            assertEquals(document, normalized.active());
        }
        assertThrows(IllegalArgumentException.class, () -> EntityFormNodePropertyPolicy.normalizeRules("FIELD", Map.of("validation", Map.of("crossField", Map.of("version", 1, "rules", List.of(Map.of())))), Map.of("fieldType", "DATETIME"), false));
    }
}
