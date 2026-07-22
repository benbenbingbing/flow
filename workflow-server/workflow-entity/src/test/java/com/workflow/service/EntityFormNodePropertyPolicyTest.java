package com.workflow.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFormNodePropertyPolicyTest {

    @Test
    void propertyMatrixCoversAllTenNodeTypes() {
        Map<String, Map<String, Object>> validProps = Map.of(
                "SECTION", Map.of("label", "区块"),
                "GRID", Map.of("gutter", 16, "defaultSpan", 12),
                "TAB_SET", Map.of("tabPosition", "left"),
                "TAB", Map.of("label", "基本信息"),
                "COLLAPSE", Map.of(
                        "label", "高级设置",
                        "defaultExpanded", false,
                        "accordion", true),
                "TEXT", Map.of("text", "说明"),
                "FIELD", Map.of(
                        "fieldId", "f1",
                        "fieldCode", "amount",
                        "fieldType", "DECIMAL",
                        "componentType", "number",
                        "componentProps", Map.of("precision", 2)),
                "SUB_FORM", Map.of(
                        "fieldId", "r1",
                        "fieldCode", "details",
                        "fieldType", "SUB_FORM",
                        "componentType", "sub_form",
                        "componentProps", Map.of()),
                "REPEATER", Map.of(
                        "fieldId", "r2",
                        "fieldCode", "items",
                        "fieldType", "SUB_FORM_LIST",
                        "componentType", "sub_form_list",
                        "componentProps", Map.of()),
                "ACTION_SLOT", Map.of("label", "底部动作"));

        assertEquals(
                Set.of(
                        "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE",
                        "TEXT", "FIELD", "SUB_FORM", "REPEATER",
                        "ACTION_SLOT"),
                validProps.keySet());

        validProps.forEach((nodeType, props) -> {
            EntityFormNodePropertyPolicy.NormalizedProps normalized =
                    EntityFormNodePropertyPolicy.normalizeProps(
                            nodeType, props, false);
            assertEquals(props, normalized.active(), nodeType);
            assertTrue(normalized.inactive().isEmpty(), nodeType);
        });
    }

    @Test
    void structuralNodesRejectFieldPropertiesButCanMigrateHistory() {
        Map<String, Object> polluted = new LinkedHashMap<>();
        polluted.put("label", "折叠面板");
        polluted.put("fieldId", "legacy-field");
        polluted.put("componentType", "input");

        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeProps(
                        "COLLAPSE", polluted, false));

        EntityFormNodePropertyPolicy.NormalizedProps migrated =
                EntityFormNodePropertyPolicy.normalizeProps(
                        "COLLAPSE", polluted, true);
        assertEquals(Map.of("label", "折叠面板"), migrated.active());
        assertEquals(
                Set.of("fieldId", "componentType"),
                migrated.inactive().keySet());
    }

    @Test
    void legacyNestedContainerConfigIsLiftedToRuntimeShape() {
        EntityFormNodePropertyPolicy.NormalizedProps normalized =
                EntityFormNodePropertyPolicy.normalizeProps(
                        "COLLAPSE",
                        Map.of(
                                "label", "详情",
                                "componentProps", Map.of(
                                        "defaultExpanded", false,
                                        "accordion", true)),
                        true);

        assertEquals(false, normalized.active().get("defaultExpanded"));
        assertEquals(true, normalized.active().get("accordion"));
        assertFalse(normalized.active().containsKey("componentProps"));
    }

    @Test
    void emptyUnsupportedRulesNormalizeButMeaningfulRulesReject() {
        assertTrue(
                EntityFormNodePropertyPolicy.normalizeRules(
                        "COLLAPSE",
                        Map.of(
                                "validation", Map.of(),
                                "extension", Map.of()))
                        .isEmpty());

        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeRules(
                        "COLLAPSE",
                        Map.of("validation", Map.of("minLength", 2))));

        assertEquals(
                Map.of("validation", Map.of("minLength", 2)),
                EntityFormNodePropertyPolicy.normalizeRules(
                        "FIELD",
                        Map.of("validation", Map.of("minLength", 2)),
                        Map.of("fieldType", "STRING"),
                        false).active());
    }

    @Test
    void fieldValidationAndComponentCompatibilityAreTypeSpecific() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeRules(
                        "FIELD",
                        Map.of("validation", Map.of("minLength", 2)),
                        Map.of("fieldType", "DECIMAL"),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeRules(
                        "FIELD",
                        Map.of("minLength", 2),
                        Map.of("fieldType", "DECIMAL"),
                        false));
        assertEquals(
                Map.of("validation", Map.of("min", 0, "max", 100)),
                EntityFormNodePropertyPolicy.normalizeRules(
                        "FIELD",
                        Map.of(
                                "validation",
                                Map.of("min", 0, "max", 100)),
                        Map.of("fieldType", "DECIMAL"),
                        false).active());
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeRules(
                        "FIELD",
                        Map.of(
                                "validation",
                                Map.of("minLength", 10, "maxLength", 2)),
                        Map.of("fieldType", "STRING"),
                        false));

        assertDoesNotThrow(() ->
                EntityFormNodePropertyPolicy.normalizeProps(
                        "FIELD",
                        Map.of(
                                "fieldCode", "amount",
                                "fieldType", "DECIMAL",
                                "componentType", "number",
                                "gridSpan", 12),
                        false));
        assertDoesNotThrow(() ->
                EntityFormNodePropertyPolicy.normalizeProps(
                        "FIELD",
                        Map.of(
                                "fieldCode", "description",
                                "fieldType", "RICH_TEXT",
                                "componentType", "rich_text",
                                "gridSpan", 24),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeProps(
                        "FIELD",
                        Map.of(
                                "fieldCode", "amount",
                                "fieldType", "DECIMAL",
                                "componentType", "input",
                                "gridSpan", 12),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeProps(
                        "FIELD",
                        Map.of(
                                "fieldCode", "amount",
                                "fieldType", "DECIMAL",
                                "componentType", "unknown_widget",
                                "gridSpan", 12),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeProps(
                        "FIELD",
                        Map.of(
                                "fieldCode", "details",
                                "fieldType", "SUB_FORM",
                                "componentType", "sub_form",
                                "gridSpan", 24),
                        false));
    }

    @Test
    void confirmedPropertyMatrixRejectsHiddenAndUnsafeValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeProps(
                        "TAB_SET",
                        Map.of(
                                "label", "页签",
                                "defaultActiveKey", "tab-1"),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeProps(
                        "GRID",
                        Map.of("gutter", 64, "defaultSpan", 12),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeProps(
                        "TAB_SET",
                        Map.of("tabPosition", "center"),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.normalizeProps(
                        "FIELD",
                        Map.of(
                                "fieldCode", "owner",
                                "fieldType", "REFERENCE",
                                "componentType", "reference",
                                "componentProps",
                                Map.of(
                                        "refConfig",
                                        Map.of(
                                                "apiUrl",
                                                "https://example.com"))),
                        false));
    }

    @Test
    void dataSourceUsageMatrixMatchesRuntimeConsumers() {
        assertEquals(
                Set.of(
                        "FIELD_OPTIONS", "FIELD_DEFAULT", "FIELD_COMPUTE",
                        "AFTER_LOAD", "BEFORE_SUBMIT"),
                EntityFormNodePropertyPolicy.dataSourceUsages("FIELD"));
        assertEquals(
                Set.of("SUBFORM_ROWS", "AFTER_LOAD", "BEFORE_SUBMIT"),
                EntityFormNodePropertyPolicy.dataSourceUsages("SUB_FORM"));
        assertEquals(
                Set.of("SUBFORM_ROWS", "AFTER_LOAD", "BEFORE_SUBMIT"),
                EntityFormNodePropertyPolicy.dataSourceUsages("REPEATER"));

        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy
                        .normalizeDataSourceBindings(
                                "FIELD",
                                Map.of(
                                        "SUBFORM_ROWS",
                                        Map.of("sourceId", "rows"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy
                        .normalizeDataSourceBindings(
                                "SUB_FORM",
                                Map.of(
                                        "FIELD_OPTIONS",
                                        Map.of("sourceId", "options"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy
                        .normalizeDataSourceBindings(
                                "SECTION",
                                Map.of(
                                        "AFTER_LOAD",
                                        Map.of("sourceId", "processor"))));
    }

    @Test
    void bindingMatrixLocksDataSemantics() {
        assertDoesNotThrow(() ->
                EntityFormNodePropertyPolicy.validateBinding(
                        "FIELD", "ENTITY_FIELD", "amount"));
        assertDoesNotThrow(() ->
                EntityFormNodePropertyPolicy.validateBinding(
                        "SUB_FORM", "RELATION", "details_relation"));
        assertDoesNotThrow(() ->
                EntityFormNodePropertyPolicy.validateBinding(
                        "SECTION", "NONE", null));

        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.validateBinding(
                        "SECTION", "ENTITY_FIELD", "name"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.validateBinding(
                        "SUB_FORM", "ENTITY_FIELD", "details"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.validateBinding(
                        "FIELD", "NONE", "name"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.validateBinding(
                        "FIELD", "ENTITY_FIELD", null));
    }

    @Test
    void componentAndTemplateCapabilitiesAreTypeSpecific() {
        assertDoesNotThrow(() ->
                EntityFormNodePropertyPolicy.validateExtension(
                        "FIELD", "money-field", 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.validateExtension(
                        "TEXT", "text-extension", 1, 1));

        assertDoesNotThrow(() ->
                EntityFormNodePropertyPolicy.validateTemplate(
                        "REPEATER", "table-template", 2, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.validateTemplate(
                        "GRID", "grid-template", 1, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityFormNodePropertyPolicy.validateTemplate(
                        "FIELD", "field-template", null, Map.of()));
    }
}
