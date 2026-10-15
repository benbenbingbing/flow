package com.workflow.entity.ui.application.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuiltInButtonAppearancePolicyTest {

    @Test
    void acceptsAllAppearanceFieldsWithoutAddingBehaviorOrDefaults() {
        Map<String, Object> button = Map.of(
                "label", "保存验收信息", "icon", "Check", "buttonType", "primary",
                "buttonAppearance", "PLAIN", "size", "small", "textColor", "#ABC",
                "backgroundColor", "#AbCd", "borderColor", "#11223344", "borderRadius", 0, "sort", 1000);
        Map<String, Object> source = Map.of("position", "BOTH", "align", "RIGHT", "gap", 40,
                "buttons", Map.of("save", button));

        Map<String, Object> normalized = normalize(source);

        assertEquals(source, normalized);
        assertNotSame(source, normalized);
        assertNotSame(source.get("buttons"), normalized.get("buttons"));
        assertNotSame(button, ((Map<?, ?>) normalized.get("buttons")).get("save"));
        assertEquals(Map.of(), normalize(Map.of()));
        assertEquals(Map.of("buttons", Map.of("save", Map.of("label", "保存"))),
                normalize(Map.of("buttons", Map.of("save", Map.of("label", "保存")))));
        assertFalse(normalize(Map.of("gap", 0)).containsKey("buttons"));
    }

    @Test
    void acceptsSupportedAppearanceEnumsAndExplicitEmptyIcon() {
        for (String position : List.of("DEFAULT", "TOP", "BOTTOM", "BOTH")) {
            assertEquals(position, normalize(Map.of("position", position)).get("position"));
        }
        for (String align : List.of("DEFAULT", "LEFT", "CENTER", "RIGHT")) {
            assertEquals(align, normalize(Map.of("align", align)).get("align"));
        }
        for (String appearance : List.of("DEFAULT", "PLAIN", "TEXT", "LINK", "ROUND", "CIRCLE")) {
            Map<String, Object> source = Map.of("buttons", Map.of("save", Map.of("buttonAppearance", appearance, "icon", "")));
            assertEquals(source, normalize(source));
        }
        for (String color : List.of("#abc", "#aBcD", "#123aBc", "#123aBc09")) {
            Map<String, Object> source = Map.of("buttons", Map.of("save", Map.of("backgroundColor", color)));
            assertEquals(source, normalize(source));
        }
    }

    @Test
    void rejectsBehaviorFieldsAndUnknownActionsAtEveryLevel() {
        for (String key : List.of("enabled", "visible", "action", "handler", "permission", "loading", "disabled", "style")) {
            assertThrows(IllegalArgumentException.class, () -> normalize(Map.of(key, false)), key);
            assertThrows(IllegalArgumentException.class,
                    () -> normalize(Map.of("buttons", Map.of("save", Map.of(key, false)))), key);
        }
        assertThrows(IllegalArgumentException.class, () -> normalize(Map.of("buttons", Map.of("delete", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> normalize(Map.of("buttons", Map.of("refresh", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> BuiltInButtonAppearancePolicy.normalize(
                Map.of("buttons", Map.of("save", Map.of())), BuiltInButtonAppearancePolicy.COMPOSITION_BUTTONS));
        assertThrows(IllegalArgumentException.class, () -> BuiltInButtonAppearancePolicy.normalize(
                Map.of("buttons", Map.of("link", Map.of())), BuiltInButtonAppearancePolicy.RELATED_LIST_BUTTONS));
    }

    @Test
    void rejectsInvalidRootValuesAndNonObjectPayloads() {
        for (Object invalid : List.of("", List.of(), 1, false)) {
            assertThrows(IllegalArgumentException.class, () -> normalize(invalid));
            assertThrows(IllegalArgumentException.class, () -> normalize(Map.of("buttons", invalid)));
            assertThrows(IllegalArgumentException.class, () -> normalize(Map.of("buttons", Map.of("save", invalid))));
        }
        assertThrows(IllegalArgumentException.class, () -> normalize(null));
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("position", "HIDDEN"), Map.of("position", "top"), Map.of("align", "SPACE_BETWEEN"),
                Map.of("gap", -1), Map.of("gap", 41), Map.of("gap", 0.5), Map.of("gap", "8"),
                Map.of("gap", new BigDecimal("1.00000000000000000001")),
                Map.of("gap", Double.NaN), Map.of("gap", Double.POSITIVE_INFINITY))) {
            assertThrows(IllegalArgumentException.class, () -> normalize(invalid), invalid.toString());
        }
    }

    @Test
    void rejectsInvalidButtonValuesIncludingCssAndInvalidHexColors() {
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("label", " "), Map.of("label", "a".repeat(81)), Map.of("label", 42),
                Map.of("icon", "Unknown"), Map.of("buttonType", "PRIMARY"), Map.of("buttonAppearance", "SOLID"),
                Map.of("size", "huge"), Map.of("borderRadius", -1), Map.of("borderRadius", 41),
                Map.of("borderRadius", 1.5), Map.of("sort", -1), Map.of("sort", 1001), Map.of("sort", "1"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> normalize(Map.of("buttons", Map.of("save", invalid))), invalid.toString());
        }
        for (String field : List.of("textColor", "backgroundColor", "borderColor")) {
            for (String color : List.of("", "red", "#12", "#12345", "#1234567", "#123456789", "#ggg", "var(--color)", "#fff;display:none")) {
                assertThrows(IllegalArgumentException.class,
                        () -> normalize(Map.of("buttons", Map.of("save", Map.of(field, color)))), field + "=" + color);
            }
        }
        Map<String, Object> nullField = new LinkedHashMap<>();
        nullField.put("icon", null);
        assertThrows(IllegalArgumentException.class, () -> normalize(Map.of("buttons", Map.of("save", nullField))));
    }

    private Map<String, Object> normalize(Object raw) {
        return BuiltInButtonAppearancePolicy.normalize(raw, BuiltInButtonAppearancePolicy.RELATED_FORM_BUTTONS);
    }
}
