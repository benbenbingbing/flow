package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** 不完整结构化条件不能抢占旧表达式，三个字段状态默认值也必须保持原语义。 */
    @Test
    void fallsBackToLegacyRuleWhenStructuredConditionIsIncomplete() {
        Map<String, Object> incomplete = condition(
                "status",
                "==",
                "   ");

        assertTrue(evaluator.evaluate(
                incomplete,
                "${status} == 'OPEN'",
                Map.of("status", "OPEN"),
                false));
        assertFalse(evaluator.evaluate(
                incomplete,
                "${status} == 'CLOSED'",
                Map.of("status", "OPEN"),
                true));
        assertTrue(evaluator.evaluate(
                incomplete,
                null,
                Map.of(),
                true));
        assertFalse(evaluator.evaluate(
                incomplete,
                null,
                Map.of(),
                false));
        assertFalse(evaluator.evaluateStructured(
                incomplete,
                Map.of("status", "OPEN")));
        assertTrue(evaluator.evaluate(
                Map.of("version", 1),
                "${status} == 'OPEN'",
                Map.of("status", "OPEN"),
                false));
    }

    /** 历史快照继续采用浏览器的容错归一化，发布校验负责阻断此类新配置。 */
    @Test
    void normalizesHistoricalStructuredConditionLikeBrowserRuntime() {
        Map<String, Object> historical = Map.of(
                "version", 1,
                "root", Map.of(
                        "type", "GROUP",
                        "logic", "UNKNOWN",
                        "children", java.util.List.of(
                                Map.of("unknown", true),
                                Map.of(
                                        "type", "CONDITION",
                                        "property", "status",
                                        "operator", "UNKNOWN",
                                        "value", "OPEN"))));

        assertTrue(evaluator.evaluateStructured(
                historical,
                Map.of("status", "OPEN")));
    }

    /** 数字字段的非法比较值等同浏览器 NaN，所有大小比较均返回 false。 */
    @Test
    void rejectsRelationalComparisonAgainstNonNumericExpectedValue() {
        for (String operator : java.util.List.of(">", "<", ">=", "<=")) {
            assertFalse(evaluator.evaluateStructured(
                    condition("amount", operator, "abc"),
                    Map.of("amount", 10)));
            assertFalse(evaluator.evaluate(
                    null,
                    "${amount} " + operator + " abc",
                    Map.of("amount", 10),
                    true));
        }
    }

    /** 缺键对应 JS undefined，只有显式存在且值为 null 才能严格等于 null。 */
    @Test
    void distinguishesMissingPropertyFromExplicitNull() {
        Map<String, Object> explicitNull = new LinkedHashMap<>();
        explicitNull.put("optional", null);

        assertFalse(evaluator.evaluateStructured(
                condition("optional", "==", "null"),
                Map.of()));
        assertTrue(evaluator.evaluateStructured(
                condition("optional", "!=", "null"),
                Map.of()));
        assertTrue(evaluator.evaluateStructured(
                condition("optional", "==", "null"),
                explicitNull));
        assertFalse(evaluator.evaluateStructured(
                condition("optional", "!=", "null"),
                explicitNull));

        assertFalse(evaluator.evaluate(
                null,
                "${optional} == null",
                Map.of(),
                true));
        assertTrue(evaluator.evaluate(
                null,
                "${optional} != null",
                Map.of(),
                false));
        assertTrue(evaluator.evaluate(
                null,
                "${optional} == null",
                explicitNull,
                false));
    }

    /** MULTI_SELECT/CHECKBOX contains 必须采用 JS Array.toString 的逗号连接语义。 */
    @Test
    void evaluatesArrayContainsUsingJavascriptStringSemantics() {
        Map<String, Object> record = Map.of(
                "multiSelect", List.of("alpha", "beta"),
                "checkbox", new Object[]{null, null, "checked"});

        assertTrue(evaluator.evaluateStructured(
                condition("multiSelect", "contains", "alpha,beta"),
                record));
        assertFalse(evaluator.evaluateStructured(
                condition("multiSelect", "contains", "[alpha"),
                record));
        assertTrue(evaluator.evaluateStructured(
                condition("checkbox", "contains", ",,checked"),
                record));

        assertTrue(evaluator.evaluate(
                null,
                "multiSelect.contains('alpha,beta')",
                record,
                false));
        assertFalse(evaluator.evaluate(
                null,
                "multiSelect.includes('[alpha')",
                record,
                true));
        assertTrue(evaluator.evaluate(
                null,
                "checkbox.contains(',,checked')",
                record,
                false));
    }

    /** 三类字段状态均可到达缺键/null/数组的同态反例，不能只修孤立求值入口。 */
    @Test
    void preservesBrowserSemanticsForVisibilityDisabledAndRequiredRules() {
        Map<String, Object> explicitNull = new LinkedHashMap<>();
        explicitNull.put("tags", List.of("RED", "BLUE"));
        explicitNull.put("approval", null);

        Map<String, Map<String, Object>> structuredRules = Map.of(
                "visibilityConditionConfig",
                condition("tags", "contains", "RED,BLUE"),
                "disabledConditionConfig",
                condition("missingApproval", "==", "null"),
                "requiredConditionConfig",
                condition("approval", "==", "null"));
        assertTrue(evaluator.evaluate(
                structuredRules.get("visibilityConditionConfig"),
                null, explicitNull, true));
        assertFalse(evaluator.evaluate(
                structuredRules.get("disabledConditionConfig"),
                null, explicitNull, false));
        assertTrue(evaluator.evaluate(
                structuredRules.get("requiredConditionConfig"),
                null, explicitNull, false));

        assertTrue(evaluator.evaluate(
                null,
                "tags.contains('RED,BLUE')",
                explicitNull,
                true));
        assertFalse(evaluator.evaluate(
                null,
                "missingApproval == null",
                explicitNull,
                false));
        assertTrue(evaluator.evaluate(
                null,
                "approval == null",
                explicitNull,
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
