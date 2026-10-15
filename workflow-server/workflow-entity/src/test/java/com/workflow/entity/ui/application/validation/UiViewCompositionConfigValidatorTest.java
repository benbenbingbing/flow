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
    void builtInAppearanceSurvivesNormalizationWithoutChangingExistingActions() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> presentation = mutableChild(config, "presentation");
        assertFalse(((Map<?, ?>) validator.validate(config).normalizedConfig()
                .get("presentation")).containsKey("actionBar"));
        Map<String, Object> listBar = Map.of("position", "BOTTOM", "align", "RIGHT", "gap", 16,
                "buttons", Map.of("refresh", Map.of("label", "刷新验收数据", "buttonAppearance", "TEXT"),
                        "open", Map.of("size", "large", "icon", "View")));
        presentation.put("actionBar", listBar);
        Map<String, Object> normalizedList = validator.validate(config).normalizedConfig();
        assertEquals(listBar, ((Map<?, ?>) normalizedList.get("presentation")).get("actionBar"));
        assertEquals(config.get("actions"), normalizedList.get("actions"));

        mutableChild(config, "target").put("contentType", "FORM");
        config.put("actions", List.of("VIEW", "EDIT"));
        Map<String, Object> formBar = Map.of("buttons", Map.of("save", Map.of("label", "保存验收信息"),
                "open", Map.of("buttonType", "primary")));
        presentation.put("actionBar", formBar);
        Map<String, Object> normalizedForm = validator.validate(config).normalizedConfig();
        assertEquals(formBar, ((Map<?, ?>) normalizedForm.get("presentation")).get("actionBar"));
        assertEquals(config.get("actions"), normalizedForm.get("actions"));
    }

    @Test
    void builtInAppearanceRejectsButtonsFromAnotherContentTypeAndBehaviorOverrides() {
        Map<String, Object> config = frontendDraft();
        Map<String, Object> presentation = mutableChild(config, "presentation");
        presentation.put("actionBar", Map.of("buttons", Map.of("save", Map.of("label", "保存"))));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        mutableChild(config, "target").put("contentType", "FORM");
        for (Object invalid : List.of(Map.of("buttons", Map.of("refresh", Map.of())),
                Map.of("buttons", Map.of("save", Map.of("visible", false))), Map.of("gap", 41), "invalid")) {
            presentation.put("actionBar", invalid);
            assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        }
        presentation.put("actionBar", null);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

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
    void acceptsOnlyCreateInitialMappings() {
        Map<String, Object> config = frontendDraft();
        config.put("actions", List.of("CREATE"));
        Map<String, Object> create = Map.of("initialMappings", List.of(Map.of(
                "source", "projectId", "target", "projectId")));
        config.put("actionSettings", Map.of("create", create));

        Map<?, ?> settings = (Map<?, ?>) validator.validate(config)
                .normalizedConfig().get("actionSettings");

        assertEquals(1, settings.size());
        assertEquals(1, ((List<?>) ((Map<?, ?>) settings.get("create")).get("initialMappings")).size());
    }

    @Test
    void rejectsRemovedActionsAndSettingsInsteadOfPreservingThem() {
        for (String action : List.of("SELECT", "LINK", "UNLINK", "SAVE_WITH_FORM")) {
            Map<String, Object> config = frontendDraft();
            config.put("actions", List.of("VIEW", action));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(config), action);
        }
        Map<String, Object> selection = frontendDraft();
        selection.put("actionSettings", Map.of("select", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(selection));

        // 连关闭值也不能继续持久化：移除的设置已经不属于配置契约。
        for (boolean enabled : List.of(false, true)) {
            Map<String, Object> create = frontendDraft();
            create.put("actions", List.of("CREATE"));
            create.put("actionSettings", Map.of("create", Map.of("associateAfterCreate", enabled)));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(create));
        }
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
        config.put("relation", Map.of("type", "SAME_RECORD"));
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
        config.put("relation", Map.of("type", "SAME_RECORD"));
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
