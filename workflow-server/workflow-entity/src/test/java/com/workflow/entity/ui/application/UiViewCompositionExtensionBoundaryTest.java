package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.application.validation.UiViewCompositionConfigValidator;
import com.workflow.entity.ui.application.validation.UiViewCompositionExtensionPolicy;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 普通关系与独立扩展页面的保存及运行时接口授权边界。 */
class UiViewCompositionExtensionBoundaryTest {

    private final UiViewCompositionConfigValidator validator = new UiViewCompositionConfigValidator();
    private final ObjectMapper mapper = new ObjectMapper();
    private final UiDataSourceBindingMatcher matcher = new UiDataSourceBindingMatcher(
            mock(UiEventBindingMapper.class), new JsonDocumentCodec(mapper), mapper);
    private final Map<String, Object> binding = Map.of("actionKey", "calculateRisk", "extensionId", "risk-interface");

    @ParameterizedTest
    @ValueSource(strings = {"ENTITY_RELATION", "REFERENCE_FIELD", "REVERSE_REFERENCE", "FIELD_MATCH"})
    void ordinaryRelationsRejectInterfaceModesAndHiddenActionBindings(String relationType) {
        for (String mode : List.of("NONE", "CUSTOM_COMPONENT", "INTERFACE_SERVICE", "BOTH")) {
            Map<String, Object> config = config(relationType);
            config.put("specialHandling", Map.of("mode", mode, "actionServices", List.of(binding),
                    "customComponent", component()));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> validator.validate(config));
            assertTrue(error.getMessage().contains("独立扩展页面"));
        }
        for (String mode : List.of("INTERFACE_SERVICE", "BOTH")) {
            Map<String, Object> config = config(relationType);
            config.put("specialHandling", Map.of("mode", mode, "actionServices", List.of()));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        }
        Map<String, Object> config = config(relationType);
        config.put("specialHandling", Map.of("mode", "CUSTOM_COMPONENT", "customComponent", component(),
                "interfaceService", Map.of("extensionId", "hidden-interface")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENTITY_RELATION", "REFERENCE_FIELD", "REVERSE_REFERENCE", "FIELD_MATCH"})
    void ordinaryRelationsRemoveRetiredPresentationWithoutMutatingOriginal(String relationType) {
        Map<String, Object> config = config(relationType);
        Map<String, Object> originalSpecial = Map.of("mode", "CUSTOM_COMPONENT", "customComponent",
                Map.of("name", "deleted-component", "version", -1, "props", Map.of("url", "https://retired.example")),
                "failurePolicy", "HIDE", "actionServices", List.of(),
                "interfaceService", Map.of("extensionId", "", "operationCode", ""));
        config.put("specialHandling", originalSpecial);

        Map<?, ?> special = (Map<?, ?>) validator.validate(config).normalizedConfig().get("specialHandling");

        assertEquals("NONE", special.get("mode"));
        assertEquals("ERROR", special.get("failurePolicy"));
        assertFalse(special.containsKey("customComponent"));
        assertFalse(special.containsKey("actionServices"));
        assertFalse(special.containsKey("interfaceService"));
        assertEquals("CUSTOM_COMPONENT", originalSpecial.get("mode"));
        assertEquals("HIDE", originalSpecial.get("failurePolicy"));
        assertTrue(originalSpecial.containsKey("customComponent"));
        assertEquals(originalSpecial, config.get("specialHandling"));
        Map<String, Object> both = Map.of("mode", "BOTH", "customComponent", component(),
                "actionServices", List.of(binding));
        Map<String, Object> cleaned = UiViewCompositionExtensionPolicy.normalizePresentationOverride(
                config.get("relation"), both);
        assertEquals("INTERFACE_SERVICE", cleaned.get("mode"));
        assertEquals(both.get("actionServices"), cleaned.get("actionServices"));
        assertFalse(cleaned.containsKey("customComponent"));
        assertEquals("ERROR", UiViewCompositionExtensionPolicy.normalizePresentationOverride(config.get("relation"),
                Map.of("mode", "NONE", "failurePolicy", "PLACEHOLDER")).get("failurePolicy"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAME_RECORD", "INTERFACE_SERVICE"})
    void extensionPagesKeepActionConfigurationAndExactRuntimeBinding(String relationType) {
        for (String contentType : List.of("FORM", "LIST")) {
            Map<String, Object> config = config(relationType);
            config.put("target", Map.of("entityId", "target", "contentId", "target-page", "contentType", contentType));
            Map<String, Object> special = new LinkedHashMap<>();
            special.put("mode", "BOTH");
            special.put("failurePolicy", "PLACEHOLDER");
            special.put("customComponent", component());
            special.put("actionServices", List.of(binding));
            special.put("interfaceService", Map.of("extensionId", "resolve-interface"));
            config.put("specialHandling", special);
            Map<String, Object> normalized = validator.validate(config).normalizedConfig();
            Map<?, ?> savedSpecial = (Map<?, ?>) normalized.get("specialHandling");
            assertEquals("BOTH", savedSpecial.get("mode"));
            assertEquals("PLACEHOLDER", savedSpecial.get("failurePolicy"));
            assertEquals("demo.details", ((Map<?, ?>) savedSpecial.get("customComponent")).get("name"));
            assertEquals("risk-interface", ((Map<?, ?>) ((List<?>) savedSpecial.get("actionServices")).get(0)).get("extensionId"));
            assertEquals("$.release.viewCompositions[0].config.specialHandling.actionServices[0]",
                    matcher.findPublished(contentType, snapshot(normalized), "RELATED_CONTENT_ACTION",
                            "COMPOSITION_ACTION", "details::CALCULATERISK", "risk-interface", null));
            assertEquals("$.release.viewCompositions[0].config.specialHandling.interfaceService",
                    matcher.findPublished(contentType, snapshot(normalized), "RELATED_CONTENT_RESOLVE",
                            "COMPOSITION", "details", "resolve-interface", null));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENTITY_RELATION", "REFERENCE_FIELD", "REVERSE_REFERENCE", "FIELD_MATCH", ""})
    void genericInterfaceEndpointCannotAuthorizeHistoricalOrdinaryRelationInterfaces(String relationType) {
        Map<String, Object> config = config(relationType);
        config.put("specialHandling", Map.of("mode", "INTERFACE_SERVICE", "actionServices", List.of(binding),
                "interfaceService", Map.of("extensionId", "resolve-interface")));
        for (String ownerType : List.of("FORM", "LIST")) {
            assertNull(matcher.findPublished(ownerType, snapshot(config), "RELATED_CONTENT_ACTION",
                    "COMPOSITION_ACTION", "details::CALCULATERISK", "risk-interface", null));
            assertNull(matcher.findPublished(ownerType, snapshot(config), "RELATED_CONTENT_RESOLVE",
                    "COMPOSITION", "details", "resolve-interface", null));
        }
    }

    private Map<String, Object> config(String relationType) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("target", Map.of("entityId", "target", "contentId", "target-page", "contentType", "FORM"));
        config.put("presentation", Map.of("position", "INLINE"));
        config.put("relation", Map.of("type", relationType, "relationCode", "details",
                "sourceField", "childId", "targetField", "parentId"));
        config.put("actions", List.of("VIEW"));
        return config;
    }

    private Map<String, Object> component() {
        return Map.of("name", "demo.details", "version", 1);
    }

    private Map<String, Object> snapshot(Map<String, Object> config) {
        return Map.of("viewCompositions", List.of(Map.of("compositionKey", "details", "config", config)));
    }
}
