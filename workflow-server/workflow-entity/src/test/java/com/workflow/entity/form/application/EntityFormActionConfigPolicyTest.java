package com.workflow.entity.form.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityFormActionConfigPolicyTest {

    private final EntityFormActionConfigPolicy policy =
            new EntityFormActionConfigPolicy();

    @Test
    void acceptsBuiltInOverridesAndBoundCustomButtons() {
        Map<String, Object> viewConfig = Map.of(
                "actionBar", Map.of(
                        "version", 1,
                        "builtInOverrides", Map.of(
                                "save", Map.of(
                                        "enabled", true,
                                        "labelByMode", Map.of(
                                                "create", "暂存",
                                                "edit", "保存修改"),
                                        "enabledModes",
                                                List.of("create", "edit"),
                                        "buttonType", "primary",
                                        "sort", 30)),
                        "customButtons", List.of(Map.of(
                                "key", "generate_report",
                                "label", "生成报告",
                                "enabled", true,
                                "modes", List.of("view", "edit"),
                                "placement", "ACTION_SLOT",
                                "slotKey", "detail_actions",
                                "perm",
                                        "entity:example:generate-report",
                                "confirm", Map.of(
                                        "enabled", true,
                                        "message", "确认生成报告？"),
                                "validateBeforeExecute", false))));

        assertDoesNotThrow(() -> policy.validate(
                viewConfig,
                false,
                Set.of("detail_actions"),
                true,
                Set.of("generate_report"),
                true));
    }

    @Test
    void requiresPublishedCustomButtonBindingAndExistingSlot() {
        Map<String, Object> viewConfig = customButtonConfig(
                "ACTION_SLOT",
                "missing_slot");

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        viewConfig,
                        false,
                        Set.of("detail_actions"),
                        true,
                        Set.of("generate_report"),
                        true));

        Map<String, Object> footerConfig =
                customButtonConfig("FOOTER", "");
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        footerConfig,
                        false,
                        Set.of(),
                        true,
                        Set.of(),
                        true));
    }

    @Test
    void rejectsSystemEntityWriteAndCustomActions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        Map.of("actionBar", Map.of(
                                "builtInOverrides", Map.of(
                                        "save", Map.of("enabled", false)))),
                        true,
                        Set.of(),
                        false,
                        Set.of(),
                        false));

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        customButtonConfig("FOOTER", ""),
                        true,
                        Set.of(),
                        false,
                        Set.of(),
                        false));
    }

    @Test
    void returnsConventionDefaultsWhenActionBarIsMissing() {
        Map<String, Object> actionBar = policy.actionBar(Map.of());

        assertEquals(1, actionBar.get("version"));
        assertEquals(Map.of(), actionBar.get("builtInOverrides"));
        assertEquals(List.of(), actionBar.get("customButtons"));
    }

    private Map<String, Object> customButtonConfig(
            String placement,
            String slotKey) {
        return Map.of(
                "actionBar", Map.of(
                        "version", 1,
                        "customButtons", List.of(Map.of(
                                "key", "generate_report",
                                "label", "生成报告",
                                "enabled", true,
                                "modes", List.of("view"),
                                "placement", placement,
                                "slotKey", slotKey,
                                "perm",
                                        "entity:example:generate-report"))));
    }
}
