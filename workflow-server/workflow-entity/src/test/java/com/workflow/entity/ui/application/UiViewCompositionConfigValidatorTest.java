package com.workflow.entity.ui.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiViewCompositionConfigValidatorTest {

    private final UiViewCompositionConfigValidator validator =
            new UiViewCompositionConfigValidator();

    @Test
    void acceptsFrontendDraftShapeAndDropsInactiveFallbackSkeletons() {
        Map<String, Object> config = frontendDraft();

        UiViewCompositionConfigValidator.ValidationResult result =
                validator.validate(config);

        assertEquals(1, result.normalizedConfig().get("schemaVersion"));
        assertEquals("项目需求", result.normalizedConfig().get("name"));
        Map<?, ?> source = (Map<?, ?>) result.normalizedConfig().get("source");
        assertEquals("项目", source.get("entityName"));
        Map<?, ?> special = (Map<?, ?>) result.normalizedConfig()
                .get("specialHandling");
        assertEquals("NONE", special.get("mode"));
        assertFalse(special.containsKey("interfaceService"));
        assertFalse(special.containsKey("customComponent"));
        assertTrue(result.summary().contains("需求列表"));
    }

    @Test
    void fieldMatchAcceptsTheGuidedSourceAndTargetFieldPair() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> relation = mutableChild(config, "relation");
        relation.put("type", "FIELD_MATCH");
        relation.put("sourceField", "projectCode");
        relation.put("targetField", "projectCode");

        Map<?, ?> normalizedRelation = (Map<?, ?>) validator.validate(config)
                .normalizedConfig().get("relation");

        assertEquals("FIELD_MATCH", normalizedRelation.get("type"));
        assertEquals("projectCode", normalizedRelation.get("sourceField"));
        assertEquals("projectCode", normalizedRelation.get("targetField"));
    }

    @Test
    void acceptsSingleSelectionWithWhitelistedFieldMappings() {
        Map<String, Object> config = frontendDraft();
        config.put("actions", List.of("SELECT"));
        config.put("actionSettings", Map.of(
                "select", Map.of(
                        "mode", "SINGLE",
                        "result", "FILL_FIELDS",
                        "mappings", List.of(Map.of(
                                "source", "managerId",
                                "target", "projectManagerId"))),
                "create", Map.of(
                        "associateAfterCreate", false,
                        "initialMappings", List.of())));

        Map<?, ?> settings = (Map<?, ?>) validator.validate(config)
                .normalizedConfig().get("actionSettings");
        Map<?, ?> select = (Map<?, ?>) settings.get("select");

        assertEquals("SINGLE", select.get("mode"));
        assertEquals("FILL_FIELDS", select.get("result"));
        assertEquals(1, ((List<?>) select.get("mappings")).size());
    }

    @Test
    void rejectsActionCombinationsThatDoNotHaveSafeRuntimeSemantics() {
        Map<String, Object> aggregate = frontendDraft();
        mutableChild(aggregate, "target").put("contentType", "FORM");
        mutableChild(aggregate, "relation").put("type", "ENTITY_RELATION");
        mutableChild(aggregate, "relation").put(
                "relationCode", "project_requirements");
        aggregate.put("actions", List.of("SAVE_WITH_FORM", "EDIT"));

        IllegalArgumentException aggregateError = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(aggregate));
        assertTrue(aggregateError.getMessage().contains("子表单或重复器"));

        Map<String, Object> selection = frontendDraft();
        selection.put("actions", List.of("SELECT"));
        selection.put("actionSettings", Map.of(
                "select", Map.of(
                        "mode", "MULTIPLE",
                        "result", "FILL_FIELDS",
                        "mappings", List.of(Map.of(
                                "source", "id",
                                "target", "requirementId")))));

        IllegalArgumentException selectionError = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(selection));
        assertTrue(selectionError.getMessage().contains("只能选择一条"));

        Map<String, Object> createAndLink = frontendDraft();
        mutableChild(createAndLink, "target").put("contentType", "FORM");
        createAndLink.put("actions", List.of("CREATE"));
        createAndLink.put("actionSettings", Map.of(
                "create", Map.of(
                        "associateAfterCreate", true,
                        "initialMappings", List.of())));
        IllegalArgumentException createError = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(createAndLink));
        assertTrue(createError.getMessage().contains("同一事务"));
    }

    @Test
    void rejectsScriptSqlUrlClassAndBeanFieldsAtEveryDepth() {
        for (String forbidden : List.of(
                "script", "sql", "url", "className", "beanName")) {
            Map<String, Object> config = frontendDraft();
            mutableChild(config, "target").put(forbidden, "unsafe");

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> validator.validate(config),
                    forbidden);

            assertTrue(error.getMessage().contains("禁止使用字段"),
                    error::getMessage);
        }
    }

    @Test
    void rejectsDynamicUrlsEvenInsideRegisteredComponentProps() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> special = mutableChild(config, "specialHandling");
        special.put("mode", "CUSTOM_COMPONENT");
        special.put("customComponent", new LinkedHashMap<>(Map.of(
                "name", "project.timeline",
                "displayName", "项目时间线",
                "version", 1,
                "props", Map.of("endpoint", "https://untrusted.example"))));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(config));

        assertTrue(error.getMessage().contains("动态地址"));
    }

    @Test
    void normalizesRegisteredComponentArtifactDigest() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> special = mutableChild(
                config, "specialHandling");
        special.put("mode", "CUSTOM_COMPONENT");
        special.put("customComponent", new LinkedHashMap<>(Map.of(
                "name", "project.timeline",
                "displayName", "项目时间线",
                "version", 1,
                "artifactDigest", "A".repeat(64),
                "props", Map.of())));

        UiViewCompositionConfigValidator.ValidationResult result =
                validator.validate(config);
        Map<?, ?> normalizedSpecial = (Map<?, ?>)
                result.normalizedConfig().get("specialHandling");
        Map<?, ?> component = (Map<?, ?>)
                normalizedSpecial.get("customComponent");

        assertEquals("a".repeat(64),
                component.get("artifactDigest"));
    }

    @Test
    void rejectsMalformedRegisteredComponentArtifactDigest() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> special = mutableChild(
                config, "specialHandling");
        special.put("mode", "CUSTOM_COMPONENT");
        special.put("customComponent", new LinkedHashMap<>(Map.of(
                "name", "project.timeline",
                "version", 1,
                "artifactDigest", "not-a-digest",
                "props", Map.of())));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(config));

        assertTrue(error.getMessage().contains("制品摘要"));
    }

    @Test
    void acceptsActionOnlyInterfaceServiceWithoutDataResolver() {
        Map<String, Object> config = frontendDraft();
        config.put("specialHandling", new LinkedHashMap<>(Map.of(
                "mode", "INTERFACE_SERVICE",
                "actionServices", List.of(Map.of(
                        "actionKey", "calculateRisk",
                        "serviceId", "risk-service",
                        "operationCode", "calculateRisk",
                        "inputMappings", List.of(Map.of(
                                "source", "source.riskLevel",
                                "target", "riskLevel")),
                        "outputMappings", List.of(),
                        "failurePolicy", "ERROR")),
                "failurePolicy", "ERROR")));

        Map<?, ?> special = (Map<?, ?>) validator.validate(config)
                .normalizedConfig().get("specialHandling");

        assertFalse(special.containsKey("interfaceService"));
        assertEquals(1, ((List<?>) special.get("actionServices")).size());
    }

    @Test
    void rejectsDuplicateActionServiceKeys() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> binding = Map.of(
                "actionKey", "calculateRisk",
                "serviceId", "risk-service",
                "operationCode", "calculateRisk",
                "inputMappings", List.of(),
                "outputMappings", List.of(),
                "failurePolicy", "ERROR");
        config.put("specialHandling", new LinkedHashMap<>(Map.of(
                "mode", "INTERFACE_SERVICE",
                "actionServices", List.of(binding, binding),
                "failurePolicy", "ERROR")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(config));

        assertTrue(error.getMessage().contains("只能绑定一个接口服务"));
    }

    private Map<String, Object> frontendDraft() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("schemaVersion", 1);
        config.put("name", "项目需求");
        config.put("enabled", true);
        config.put("source", new LinkedHashMap<>(Map.of(
                "entityId", "project-entity",
                "entityCode", "project",
                "entityName", "项目")));
        config.put("target", new LinkedHashMap<>(Map.of(
                "entityId", "requirement-entity",
                "entityCode", "requirement",
                "entityName", "需求",
                "contentType", "LIST",
                "contentId", "requirement-list",
                "contentKey", "project_requirements",
                "contentName", "需求列表")));
        config.put("presentation", new LinkedHashMap<>(Map.of(
                "position", "TAB",
                "loadMode", "ON_DEMAND")));
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("type", "REVERSE_REFERENCE");
        relation.put("relationCode", "");
        relation.put("relationName", "");
        relation.put("sourceField", "");
        relation.put("sourceFieldName", "");
        relation.put("targetField", "projectId");
        relation.put("targetFieldName", "所属项目");
        relation.put("mappings", List.of());
        config.put("relation", relation);
        config.put("actions", List.of("VIEW"));
        Map<String, Object> special = new LinkedHashMap<>();
        special.put("mode", "NONE");
        special.put("interfaceService", new LinkedHashMap<>(Map.of(
                "serviceId", "",
                "serviceName", "",
                "operationCode", "",
                "operationName", "",
                "inputMappings", List.of(),
                "outputMappings", List.of())));
        special.put("customComponent", new LinkedHashMap<>(Map.of(
                "name", "",
                "displayName", "",
                "version", 1,
                "props", Map.of())));
        special.put("failurePolicy", "ERROR");
        config.put("specialHandling", special);
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableChild(
            Map<String, Object> parent,
            String key) {
        return (Map<String, Object>) parent.get(key);
    }
}
