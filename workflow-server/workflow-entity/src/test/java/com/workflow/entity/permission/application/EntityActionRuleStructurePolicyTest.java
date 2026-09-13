package com.workflow.entity.permission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityActionRuleStructurePolicyTest {

    @Test
    void normalizesCommaSeparatedInValuesInBothTrees() {
        Map<String, Object> normalized =
                EntityActionRuleStructurePolicy.normalizeAndValidate(Map.of(
                        "version", 2,
                        "visibleWhen", condition("IN", "DRAFT, REVIEW"),
                        "enabledWhen", condition("NOT_IN", "LOCKED, CLOSED"),
                        "disabledMessage", "当前状态不可操作"));

        assertEquals(List.of("DRAFT", "REVIEW"),
                node(normalized, "visibleWhen").get("value"));
        assertEquals(List.of("LOCKED", "CLOSED"),
                node(normalized, "enabledWhen").get("value"));
        assertEquals("当前状态不可操作", normalized.get("disabledMessage"));
    }

    @Test
    void canonicalizesBuiltInIdentifiersAndEnumsInBothTrees() {
        Map<String, Object> normalized =
                EntityActionRuleStructurePolicy.normalizeAndValidate(Map.of(
                        "version", 2,
                        "visibleWhen", Map.of(
                                "type", " group ",
                                "logic", " or ",
                                "children", List.of(Map.of(
                                        "type", " relation ",
                                        "relation", " current_user_is_creator "))),
                        "enabledWhen", Map.of(
                                "type", " status_category ",
                                "operator", " in ",
                                "value", " new, processing "),
                        "disabledMessage", "状态不允许"));

        Map<String, Object> visible = node(normalized, "visibleWhen");
        assertEquals("GROUP", visible.get("type"));
        assertEquals("OR", visible.get("logic"));
        @SuppressWarnings("unchecked")
        Map<String, Object> relation =
                ((List<Map<String, Object>>) visible.get("children")).get(0);
        assertEquals("RELATION", relation.get("type"));
        assertEquals("CURRENT_USER_IS_CREATOR", relation.get("relation"));
        Map<String, Object> enabled = node(normalized, "enabledWhen");
        assertEquals("STATUS_CATEGORY", enabled.get("type"));
        assertEquals("IN", enabled.get("operator"));
        assertEquals(List.of("NEW", "PROCESSING"), enabled.get("value"));
    }

    @Test
    void rejectsVersionOneLegacyFieldsAndEmptyGroups() {
        assertThrows(IllegalArgumentException.class,
                () -> EntityActionRuleStructurePolicy.normalizeAndValidate(
                        Map.of("version", 1)));
        assertThrows(IllegalArgumentException.class,
                () -> EntityActionRuleStructurePolicy.normalizeAndValidate(
                        Map.of(
                                "version", 2,
                                "root", condition("EQ", "DRAFT"))));
        assertThrows(IllegalArgumentException.class,
                () -> EntityActionRuleStructurePolicy.normalizeAndValidate(
                        Map.of(
                                "version", 2,
                                "visibleWhen", Map.of(
                                        "type", "GROUP",
                                        "logic", "AND",
                                        "children", List.of()))));
    }

    @Test
    void requiresMessageOnlyWhenEnabledConditionExists() {
        Map<String, Object> visibleOnly =
                EntityActionRuleStructurePolicy.normalizeAndValidate(Map.of(
                        "version", 2,
                        "visibleWhen", condition("EQ", "DRAFT"),
                        "disabledMessage", "   "));

        assertEquals("", visibleOnly.get("disabledMessage"));
        assertThrows(IllegalArgumentException.class,
                () -> EntityActionRuleStructurePolicy.normalizeAndValidate(
                        Map.of(
                                "version", 2,
                                "enabledWhen", condition("EQ", "DRAFT"),
                                "disabledMessage", "")));
    }

    @Test
    void listPolicyRejectsIncompleteOrUnknownConditionNodes() {
        EntityListActionRulePolicy policy =
                new EntityListActionRulePolicy(
                        new ObjectMapper(), List.of());

        for (Map<String, Object> node : List.<Map<String, Object>>of(
                Map.of("type", "FIELD", "operator", "EQ", "value", "x"),
                Map.of("type", "RELATION"),
                Map.of("type", "STATUS_CODE", "operator", "UNKNOWN"),
                Map.of("type", "UNKNOWN", "operator", "EQ"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> policy.read(Map.of(
                            "version", 2,
                            "visibleWhen", node,
                            "disabledMessage", "")));
        }
    }

    @Test
    void listPolicyRejectsIncompleteOrInvalidBuiltInSemantics() {
        EntityListActionRulePolicy policy =
                new EntityListActionRulePolicy(
                        new ObjectMapper(), List.of());

        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("type", "RELATION",
                        "relation", "CURRENT_USER_IS_ANYONE"),
                Map.of("type", "PROCESS_STATE", "operator", "IN",
                        "value", "RUNNING"),
                Map.of("type", "PROCESS_STATE", "operator", "EQ",
                        "value", "PAUSED"),
                Map.of("type", "STATUS_CATEGORY", "operator", "EQ",
                        "value", "UNKNOWN"),
                Map.of("type", "USER_FIELD", "field", "enabled",
                        "operator", "EQ", "value", true),
                Map.of("type", "STATUS_CODE", "operator", "IN",
                        "value", List.of()),
                Map.of("type", "FIELD", "field", "status",
                        "operator", "EQ", "value", ""))) {
            assertThrows(IllegalArgumentException.class,
                    () -> policy.read(Map.of(
                            "version", 2,
                            "visibleWhen", invalid,
                            "disabledMessage", "")));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> node(
            Map<String, Object> rule,
            String key) {
        return (Map<String, Object>) rule.get(key);
    }

    private Map<String, Object> condition(
            String operator,
            Object value) {
        return Map.of(
                "type", "STATUS_CODE",
                "operator", operator,
                "value", value);
    }
}
