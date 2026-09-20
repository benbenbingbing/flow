package com.workflow.entity.ui.application;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PageParameterPolicyTest {
    private Map<String, Object> config() {
        return Map.of("inputParameterSchema", Map.of("type", "object", "properties", Map.of(
                "project", Map.of("type", "string"), "count", Map.of("type", "integer", "default", 3),
                "enabled", Map.of("type", "boolean")), "required", List.of("project")),
                "inputParameterBindings", List.of(Map.of("parameter", "project", "usage", "FILTER", "targetField", "projectId", "operator", "EQ")));
    }
    @Test void validatesAndAppliesPublishedContract() {
        PageParameterPolicy.validate(config(), "LIST", Set.of("projectId"));
        var result = PageParameterPolicy.resolve(config(), Map.of("project", "p", "enabled", "false", "injected", 1));
        assertEquals(new BigDecimal("3"), result.get("count"));
        assertEquals(false, result.get("enabled"));
        assertFalse(result.containsKey("injected"));
        assertEquals(Map.of("projectId", "p", "projectId_op", "EQ"), PageParameterPolicy.filters(config(), result));
    }
    @Test void rejectsMissingRequiredInvalidTypesAndUndeclaredFields() {
        assertThrows(IllegalArgumentException.class, () -> PageParameterPolicy.resolve(config(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> PageParameterPolicy.resolve(config(), Map.of("project", "p", "count", 1.5)));
        assertThrows(IllegalArgumentException.class, () -> PageParameterPolicy.validate(config(), "LIST", Set.of("name")));
        assertThrows(IllegalArgumentException.class, () -> PageParameterPolicy.validate(config(), "FORM", null));
    }
    @Test void rejectsDuplicateAndUnsafeMappings() {
        var mapping = Map.of("parameter", "project", "sourceType", "FIELD", "sourceField", "projectId");
        assertEquals(1, PageParameterPolicy.mappings(List.of(mapping)).size());
        assertThrows(IllegalArgumentException.class, () -> PageParameterPolicy.mappings(List.of(mapping, mapping)));
        assertThrows(IllegalArgumentException.class, () -> PageParameterPolicy.mappings(List.of(Map.of("parameter", "constructor", "sourceType", "LITERAL", "value", 1))));
    }
}
