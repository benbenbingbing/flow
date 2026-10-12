package com.workflow.entity.ui.application.validation;

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
    void childListSettingsKeepOuterLayoutSeparateAndRejectInvalidOptions() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> presentation = mutableChild(config, "presentation");
        presentation.put("layout", Map.of("height", 600, "width", 75, "padding", 0));
        presentation.put("listOptions", Map.of("showSearch", false, "showPagination", false,
                "showToolbar", false, "showRowActions", true, "pageSize", 37, "maxHeight", 240));
        presentation.put("visibleModes", Map.of("create", false, "view", true));
        presentation.put("visibilityCondition", Map.of("version", 1, "root", Map.of("type", "GROUP",
                "logic", "AND", "children", List.of(Map.of("type", "CONDITION", "property", "name",
                "operator", "==", "value", "可见")))));
        Map<?, ?> normalized = (Map<?, ?>) validator.validate(config).normalizedConfig().get("presentation");
        assertEquals(600, ((Map<?, ?>) normalized.get("layout")).get("height"));
        assertEquals(240, ((Map<?, ?>) normalized.get("listOptions")).get("maxHeight"));
        assertEquals(37, ((Map<?, ?>) normalized.get("listOptions")).get("pageSize"));
        assertEquals(false, ((Map<?, ?>) normalized.get("listOptions")).get("showSearch"));
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(Map.of("pageSize", 201),
                Map.of("pageSize", 1.5), Map.of("maxHeight", 0), Map.of("showToolbar", "false"), Map.of("sql", "select"))) {
            presentation.put("listOptions", invalid);
            assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        }
        presentation.remove("listOptions");
        presentation.put("visibilityCondition", Map.of("version", 1, "root", Map.of("type", "GROUP", "logic", "AND", "children", List.of())));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void borderVisibilitySurvivesSaveAndRejectsNonBooleanValues() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> presentation = mutableChild(config, "presentation");
        assertFalse(((Map<?, ?>) validator.validate(config).normalizedConfig()
                .get("presentation")).containsKey("showBorder"));
        for (boolean visible : List.of(false, true)) {
            presentation.put("showBorder", visible);
            assertEquals(visible, ((Map<?, ?>) validator.validate(config).normalizedConfig()
                    .get("presentation")).get("showBorder"));
        }
        presentation.put("anchorSide", "BEFORE");
        assertEquals("BEFORE", ((Map<?, ?>) validator.validate(config).normalizedConfig().get("presentation")).get("anchorSide"));
        presentation.put("showBorder", "false");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void displayTitleSettingsPreserveIndependentSaveActions() {
        Map<String, Object> config = frontendDraft();
        config.put("actions", List.of("EDIT"));
        Map<String, Object> presentation = mutableChild(config, "presentation");
        presentation.put("title", "需求详情");
        presentation.put("showTitle", false);

        Map<String, Object> normalized = validator.validate(config).normalizedConfig();
        assertEquals("需求详情", ((Map<?, ?>) normalized.get("presentation")).get("title"));
        assertEquals(false, ((Map<?, ?>) normalized.get("presentation")).get("showTitle"));
        assertEquals(List.of("EDIT"), normalized.get("actions"));

        presentation.put("showTitle", true);
        assertEquals(true, ((Map<?, ?>) validator.validate(config).normalizedConfig()
                .get("presentation")).get("showTitle"));
        presentation.put("showTitle", "false");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

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
    void acceptsCreateAndEditForListTarget() {
        // 列表目标下 CREATE/EDIT 表示开放目标列表自带的新增/编辑按钮，配置校验应放行。
        Map<String, Object> config = frontendDraft();
        config.put("actions", List.of("VIEW", "CREATE", "EDIT"));

        Map<?, ?> normalized = validator.validate(config).normalizedConfig();

        assertEquals(
                List.of("VIEW", "CREATE", "EDIT"),
                normalized.get("actions"));
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

        assertTrue(error.getMessage().contains("只能绑定一个接口扩展"));
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
