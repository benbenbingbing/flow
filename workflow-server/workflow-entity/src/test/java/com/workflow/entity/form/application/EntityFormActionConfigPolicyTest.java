package com.workflow.entity.form.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

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

    @Test
    void normalizesCustomButtonAppearanceAndDefaultsMissingValue() {
        Map<String, Object> defaulted = policy.normalizeAndValidate(
                customButtonAppearanceConfig(null, "Document"),
                false, Set.of(), false, Set.of(), false);
        Map<String, Object> normalized = policy.normalizeAndValidate(
                customButtonAppearanceConfig(" plain ", "Document"),
                false, Set.of(), false, Set.of(), false);

        assertEquals("DEFAULT", customButton(defaulted)
                .get("buttonAppearance"));
        assertEquals("PLAIN", customButton(normalized)
                .get("buttonAppearance"));
    }

    @Test
    void rejectsInvalidOrUnrenderableCircleAppearance() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalizeAndValidate(
                        customButtonAppearanceConfig("square", "Document"),
                        false, Set.of(), false, Set.of(), false));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalizeAndValidate(
                        customButtonAppearanceConfig("CIRCLE", "  "),
                        false, Set.of(), false, Set.of(), false));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalizeAndValidate(
                        customButtonAppearanceConfig(
                                "CIRCLE", "UnknownIcon"),
                        false, Set.of(), false, Set.of(), false));
        assertDoesNotThrow(() -> policy.normalizeAndValidate(
                customButtonAppearanceConfig("CIRCLE", "Document"),
                false, Set.of(), false, Set.of(), false));
    }

    @Test
    void rejectsAppearanceOnBuiltInButtonOverride() {
        Map<String, Object> viewConfig = Map.of(
                "actionBar", Map.of(
                        "version", 1,
                        "builtInOverrides", Map.of(
                                "save", Map.of(
                                        "buttonAppearance", "ROUND")),
                        "customButtons", List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> policy.normalizeAndValidate(
                        viewConfig,
                        false, Set.of(), false, Set.of(), false));
    }

    @Test
    void acceptsIndependentVisibleAndEnabledConditions() {
        Map<String, Object> rule = Map.of(
                "version", 2,
                "visibleWhen", condition("STATUS_CATEGORY", "NEW"),
                "enabledWhen", condition("STATUS_CODE", "DRAFT"),
                "disabledMessage", "当前状态不可操作");

        assertDoesNotThrow(() -> policy.validateAvailabilityRule(rule));
    }

    @Test
    void normalizesRulesWithoutMutatingCaller() {
        Map<String, Object> source = Map.of(
                "actionBar", Map.of(
                        "builtInOverrides", Map.of(
                                "save", Map.of(
                                        "availabilityRule", Map.of(
                                                "version", 2,
                                                "visibleWhen", Map.of(
                                                        "type", " status_category ",
                                                        "operator", " eq ",
                                                        "value", " new "),
                                                "disabledMessage", ""))),
                        "customButtons", List.of()));

        Map<String, Object> normalized = policy.normalizeAndValidate(
                source, false, Set.of(), false, Set.of(), false);

        @SuppressWarnings("unchecked")
        Map<String, Object> actionBar =
                (Map<String, Object>) normalized.get("actionBar");
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides =
                (Map<String, Object>) actionBar.get("builtInOverrides");
        @SuppressWarnings("unchecked")
        Map<String, Object> save =
                (Map<String, Object>) overrides.get("save");
        @SuppressWarnings("unchecked")
        Map<String, Object> rule =
                (Map<String, Object>) save.get("availabilityRule");
        @SuppressWarnings("unchecked")
        Map<String, Object> visible =
                (Map<String, Object>) rule.get("visibleWhen");
        assertEquals("STATUS_CATEGORY", visible.get("type"));
        assertEquals("EQ", visible.get("operator"));
        assertEquals("NEW", visible.get("value"));
        Map<?, ?> originalActionBar =
                (Map<?, ?>) source.get("actionBar");
        Map<?, ?> originalOverrides =
                (Map<?, ?>) originalActionBar.get("builtInOverrides");
        Map<?, ?> originalSave =
                (Map<?, ?>) originalOverrides.get("save");
        Map<?, ?> originalRule =
                (Map<?, ?>) originalSave.get("availabilityRule");
        Map<?, ?> originalVisible =
                (Map<?, ?>) originalRule.get("visibleWhen");
        assertEquals(" status_category ", originalVisible.get("type"));
    }

    @Test
    void rejectsVersionOneAndLegacyBehaviorFields() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAvailabilityRule(Map.of(
                        "version", 1,
                        "visibleWhen", condition("STATUS_CODE", "DRAFT"))));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAvailabilityRule(Map.of(
                        "version", 2,
                        "unavailableBehavior", "HIDE")));
    }

    @Test
    void visibleAndEnabledTreesShareNodeLimit() {
        Map<String, Object> maximum = Map.of(
                "version", 2,
                "visibleWhen", groupWithConditions(49),
                "enabledWhen", groupWithConditions(49),
                "disabledMessage", "不可用");
        Map<String, Object> tooLarge = Map.of(
                "version", 2,
                "visibleWhen", groupWithConditions(50),
                "enabledWhen", groupWithConditions(50),
                "disabledMessage", "不可用");

        assertDoesNotThrow(() -> policy.validateAvailabilityRule(maximum));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAvailabilityRule(tooLarge));
    }

    @Test
    void rejectsIncompleteBuiltInConditions() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAvailabilityRule(Map.of(
                        "version", 2,
                        "visibleWhen", Map.of(
                                "type", "STATUS_CODE",
                                "operator", "IN",
                                "value", List.of()),
                        "disabledMessage", "")));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAvailabilityRule(Map.of(
                        "version", 2,
                        "visibleWhen", Map.of(
                                "type", "STATUS_CATEGORY",
                                "operator", "EQ",
                                "value", "UNKNOWN"),
                        "disabledMessage", "")));
    }

    private Map<String, Object> groupWithConditions(int size) {
        return Map.of(
                "type", "GROUP",
                "logic", "AND",
                "children", IntStream.range(0, size)
                        .mapToObj(index -> condition(
                                "STATUS_CODE", "S" + index))
                        .toList());
    }

    private Map<String, Object> condition(String type, String value) {
        return Map.of(
                "type", type,
                "operator", "EQ",
                "value", value);
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

    private Map<String, Object> customButtonAppearanceConfig(
            String appearance,
            String icon) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("key", "generate_report");
        button.put("label", "生成报告");
        button.put("enabled", true);
        button.put("modes", List.of("view"));
        button.put("placement", "FOOTER");
        button.put("perm", "entity:example:generate-report");
        if (appearance != null) {
            button.put("buttonAppearance", appearance);
        }
        if (icon != null) {
            button.put("icon", icon);
        }
        return Map.of(
                "actionBar", Map.of(
                        "version", 1,
                        "customButtons", List.of(button)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> customButton(
            Map<String, Object> viewConfig) {
        Map<String, Object> actionBar =
                (Map<String, Object>) viewConfig.get("actionBar");
        return (Map<String, Object>) ((List<?>) actionBar
                .get("customButtons")).get(0);
    }
}
