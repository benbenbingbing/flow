package com.workflow.entity.form.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityFormTabDefaultPolicyTest {

    @Test
    void tabSetAcceptsStableDefaultTabKeyAndPersistsItAsActiveProperty() {
        EntityFormNodePropertyPolicy.NormalizedProps normalized =
                EntityFormNodePropertyPolicy.normalizeProps(
                        "TAB_SET",
                        Map.of("tabPosition", "left", "defaultActiveTabKey", "tab-details"),
                        false);

        assertEquals("tab-details", normalized.active().get("defaultActiveTabKey"));
        assertEquals("left", normalized.active().get("tabPosition"));
    }

    @Test
    void tabSetStillRejectsUnknownExecutableProperties() {
        assertThrows(IllegalArgumentException.class, () ->
                EntityFormNodePropertyPolicy.normalizeProps(
                        "TAB_SET", Map.of("script", "alert(1)"), false));
    }
}
