package com.workflow.entity.ui.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UI 扩展引用类型策略测试。
 */
class UiExtensionReferencePolicyTest {

    @Test
    void resolvesExplicitFieldComponentWithoutGuessingByName() {
        assertEquals(
                UiExtensionReferencePolicy.FIELD,
                UiExtensionReferencePolicy.resolveNodeExtensionType(
                        "FIELD",
                        Map.of("componentExtensionType", "field")));
        assertEquals(
                UiExtensionReferencePolicy.NODE,
                UiExtensionReferencePolicy.resolveNodeExtensionType(
                        "FIELD",
                        Map.of()));
        assertEquals(
                UiExtensionReferencePolicy.NODE,
                UiExtensionReferencePolicy.resolveNodeExtensionType(
                        "SECTION",
                        Map.of("componentExtensionType", "FIELD")));
    }
}
