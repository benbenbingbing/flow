package com.workflow.entity.ui.application.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CascaderFieldConfigurationPolicyTest {
    @Test
    void omittedConfigurationRemainsStaticAndEmpty() {
        assertEquals(Map.of(), validate(Map.of(), Map.of()));
    }

    @Test
    void staticTreeAllowsRepeatedCodesAcrossBranchesAndPreservesCodeTypes() {
        assertDoesNotThrow(() -> validate(Map.of("cascaderOptions", List.of(
                node("province-1", List.of(leaf(1), leaf("1"))),
                node("province-2", List.of(leaf(1))))), Map.of()));
    }

    @Test
    void rejectsDuplicateSiblingCodes() {
        assertThrows(IllegalArgumentException.class, () -> validate(
                Map.of("cascaderOptions", List.of(leaf(1), leaf(1.0))), Map.of()));
    }

    @Test
    void rejectsStringifiedOptions() {
        assertThrows(IllegalArgumentException.class, () -> validate(
                Map.of("cascaderOptions", "[]"), Map.of()));
    }

    @Test
    void rejectsContradictoryLeafAndChildren() {
        assertThrows(IllegalArgumentException.class, () -> validate(
                Map.of("cascaderOptions", List.of(Map.of(
                        "value", "a", "label", "A", "leaf", false))), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> validate(
                Map.of("cascaderOptions", List.of(Map.of(
                        "value", "a", "label", "A", "leaf", true,
                        "children", List.of(leaf("b"))))), Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cascaderSource", "cascaderLoadMode"})
    void rejectsInvalidMode(String key) {
        assertThrows(IllegalArgumentException.class, () -> validate(Map.of(key, "OTHER"), Map.of()));
    }

    @Test
    void interfaceRequiresExactlyOneEnabledBinding() {
        Map<String, Object> props = Map.of("cascaderSource", "INTERFACE");
        Map<String, Object> binding = Map.of("extensionId", "regions");
        assertEquals(binding, validate(props, Map.of("FIELD_OPTIONS", List.of(
                binding, Map.of("extensionId", "old", "enabled", false)))));
        for (Object configured : List.of(List.of(), List.of(binding, binding),
                Map.of("extensionId", "regions", "enabled", false), Map.of("extensionId", ""))) {
            assertThrows(IllegalArgumentException.class,
                    () -> validate(props, Map.of("FIELD_OPTIONS", configured)));
        }
        assertThrows(IllegalArgumentException.class, () -> validate(props, Map.of()));
    }

    @Test
    void permitsCompleteLegacyBindingAndModeRestriction() {
        Map<String, Object> binding = Map.of("serviceId", "old-service", "operationCode", "children",
                "enabledModes", List.of("create", "edit"));
        assertEquals(binding, validate(Map.of("cascaderSource", "INTERFACE", "cascaderLoadMode", "CHILDREN"),
                Map.of("FIELD_OPTIONS", binding)));
    }

    @Test
    void enforcesDepthAndTotalCountBounds() {
        List<?> tree = List.of(leaf("leaf"));
        for (int index = 0; index < 32; index++) tree = List.of(node("branch-" + index, tree));
        List<?> tooDeep = tree;
        assertThrows(IllegalArgumentException.class, () -> validate(Map.of("cascaderOptions", tooDeep), Map.of()));
        List<?> tooWide = java.util.stream.IntStream.range(0, 10001).mapToObj(index -> leaf(index)).toList();
        assertThrows(IllegalArgumentException.class, () -> validate(Map.of("cascaderOptions", tooWide), Map.of()));
    }

    private Map<?, ?> validate(Map<?, ?> props, Map<?, ?> bindings) {
        return CascaderFieldConfigurationPolicy.validate(props, bindings, "$.nodes[0]");
    }

    private Map<String, Object> node(Object value, List<?> children) {
        return Map.of("value", value, "label", "节点", "children", children);
    }

    private Map<String, Object> leaf(Object value) {
        return Map.of("value", value, "label", "选项");
    }
}
