package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishedFormConditionEvaluatorTest {

    private final PublishedFormConditionEvaluator evaluator =
            new PublishedFormConditionEvaluator(new ObjectMapper());

    @Test
    void evaluatesNestedAndOrConditionsWithRuntimeTypes() {
        Map<String, Object> configuration = Map.of(
                "version", 1,
                "root", Map.of(
                        "type", "GROUP",
                        "logic", "AND",
                        "children", java.util.List.of(
                                Map.of(
                                        "type", "CONDITION",
                                        "property", "stage",
                                        "operator", "==",
                                        "value", "REVIEW"),
                                Map.of(
                                        "type", "GROUP",
                                        "logic", "OR",
                                        "children", java.util.List.of(
                                                Map.of(
                                                        "type", "CONDITION",
                                                        "property", "urgent",
                                                        "operator", "==",
                                                        "value", "true"),
                                                Map.of(
                                                        "type", "CONDITION",
                                                        "property", "amount",
                                                        "operator", ">=",
                                                        "value", "100"))))));

        assertTrue(evaluator.evaluateStructured(
                configuration,
                Map.of(
                        "stage", "REVIEW",
                        "urgent", false,
                        "amount", 120)));
        assertFalse(evaluator.evaluateStructured(
                configuration,
                Map.of(
                        "stage", "DRAFT",
                        "urgent", true,
                        "amount", 120)));
        assertFalse(evaluator.evaluateStructured(
                configuration,
                Map.of("stage", "REVIEW")));

        assertTrue(evaluator.evaluateStructured(
                condition("metadata", "empty", ""),
                Map.of("metadata", Map.of())));
        assertFalse(evaluator.evaluateStructured(
                condition("code", "==", "1"),
                Map.of("code", "01")));
        assertTrue(evaluator.evaluateStructured(
                condition("amount", "==", "1"),
                Map.of("amount", 1)));
    }

    @Test
    void evaluatesLegacyRulesForBackwardCompatibility() {
        assertTrue(evaluator.evaluate(
                null,
                "${amount} >= 100 && ${stage} == 'REVIEW'",
                Map.of("amount", 120, "stage", "REVIEW"),
                false));
        assertTrue(evaluator.evaluate(
                null,
                "empty(owner) || tags.contains('urgent')",
                Map.of("owner", "", "tags", "normal"),
                false));
    }

    @Test
    void validatesReferencedFieldsAndConditionCompleteness() {
        Map<String, Object> valid = condition(
                "status",
                "==",
                "OPEN");
        assertDoesNotThrow(() -> evaluator.validateStructured(
                valid,
                Set.of("status"),
                "测试条件："));

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.validateStructured(
                        condition("missing", "==", "OPEN"),
                        Set.of("status"),
                        "测试条件："));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.validateStructured(
                        condition("status", "==", ""),
                        Set.of("status"),
                        "测试条件："));
    }

    private Map<String, Object> condition(
            String property,
            String operator,
            String value) {
        return Map.of(
                "version", 1,
                "root", Map.of(
                        "type", "GROUP",
                        "logic", "AND",
                        "children", java.util.List.of(Map.of(
                                "type", "CONDITION",
                                "property", property,
                                "operator", operator,
                                "value", value))));
    }
}
