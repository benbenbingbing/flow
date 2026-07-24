package com.workflow.service;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 实体表单节点属性与规则的规范化、校验策略。
 *
 * <p>集中管理各类表单节点（FIELD、SUB_FORM、REPEATER、容器节点等）允许的属性集合、
 * 数据源绑定位置、字段绑定类型、扩展组件与模板的校验规则，并提供属性迁移、
 * 校验项归一化和非法属性剥离能力。所有方法均为纯函数，便于在不同上下文复用。</p>
 */
final class EntityFormNodePropertyPolicy {

    private static final Set<String> FIELD_DATA_SOURCE_USAGES = Set.of(
            "FIELD_OPTIONS", "FIELD_DEFAULT", "FIELD_COMPUTE",
            "AFTER_LOAD", "BEFORE_SUBMIT");
    private static final Set<String> SUB_FORM_DATA_SOURCE_USAGES = Set.of(
            "SUBFORM_ROWS", "AFTER_LOAD", "BEFORE_SUBMIT");
    private static final Set<String> FIELD_BINDING_TYPES = Set.of(
            "ENTITY_FIELD", "RELATION", "COMPUTED", "CONTEXT", "NONE");
    private static final Set<String> SUB_FORM_BINDING_TYPES = Set.of(
            "RELATION", "NONE");
    private static final Set<String> EXTENSIBLE_TYPES = Set.of(
            "FIELD", "SUB_FORM", "REPEATER");
    private static final Set<String> RULE_TYPES = Set.of("FIELD");
    private static final Set<String> CHILD_FORM_TYPES = Set.of(
            "SUB_FORM", "REPEATER");
    private static final Set<String> TEMPLATE_TYPES = Set.of(
            "FIELD", "SUB_FORM", "REPEATER");
    private static final Set<String> LENGTH_VALIDATION_FIELD_TYPES = Set.of(
            "STRING", "TEXT");
    private static final Set<String> RANGE_VALIDATION_FIELD_TYPES = Set.of(
            "INTEGER", "LONG", "DECIMAL", "DOUBLE");
    private static final Set<String> FORMAT_VALIDATION_FIELD_TYPES = Set.of(
            "STRING", "TEXT");
    private static final Set<String> VALID_FORMATS = Set.of(
            "EMAIL", "PHONE", "URL");
    private static final Set<String> STRUCTURED_VALIDATION_KEYS = Set.of(
            "minLength", "maxLength", "min", "max", "format");
    private static final Map<String, Set<String>>
            BUILT_IN_COMPONENT_FIELD_TYPES = Map.ofEntries(
                    Map.entry("input", Set.of("STRING")),
                    Map.entry("textarea", Set.of("STRING", "TEXT")),
                    Map.entry("rich_text", Set.of("TEXT", "RICH_TEXT")),
                    Map.entry(
                            "number",
                            Set.of(
                                    "INTEGER", "LONG",
                                    "DECIMAL", "DOUBLE")),
                    Map.entry("date", Set.of("DATE")),
                    Map.entry("datetime", Set.of("DATETIME")),
                    Map.entry("select", Set.of("SELECT", "STRING")),
                    Map.entry(
                            "select_multiple",
                            Set.of("MULTI_SELECT")),
                    Map.entry("radio", Set.of("RADIO", "SELECT")),
                    Map.entry(
                            "checkbox",
                            Set.of("CHECKBOX", "MULTI_SELECT")),
                    Map.entry("switch", Set.of("BOOLEAN")),
                    Map.entry("file", Set.of("FILE")),
                    Map.entry("image", Set.of("IMAGE")),
                    Map.entry(
                            "cascader",
                            Set.of("STRING", "MULTI_SELECT")),
                    Map.entry(
                            "reference",
                            Set.of(
                                    "REFERENCE", "USER", "DEPT",
                                    "ROLE", "GROUP")),
                    Map.entry(
                            "multi_reference",
                            Set.of("MULTI_REFERENCE")));

    private static final Set<String> COMMON_CONTAINER_PROPS =
            Set.of("label");
    private static final Set<String> FIELD_PROPS = Set.of(
            "fieldId", "fieldCode", "fieldName", "label", "fieldType",
            "componentType", "placeholder", "defaultValue", "gridSpan",
            "required", "readonly", "hidden", "componentProps");
    private static final Set<String> SUB_FORM_PROPS = Set.of(
            "fieldId", "fieldCode", "fieldName", "label", "fieldType",
            "componentType", "gridSpan", "componentProps",
            "childFormId", "refFormId", "publishedFormId",
            "childFormReleaseId", "refFormReleaseId",
            "publishedFormReleaseId", "childFormReleaseVersion",
            "refFormReleaseVersion", "publishedFormReleaseVersion");
    private static final Map<String, Set<String>> ALLOWED_PROPS =
            buildAllowedProps();
    private static final Map<String, Set<String>> CONTAINER_CONFIG_KEYS =
            Map.of(
                    "GRID", Set.of("gutter", "defaultSpan"),
                    "TAB_SET", Set.of("tabPosition"),
                    "COLLAPSE", Set.of("defaultExpanded", "accordion"),
                    "TEXT", Set.of("text"));

    private EntityFormNodePropertyPolicy() {
    }

    /**
     * 规范化节点属性，拆分为活跃属性与不支持属性；非迁移模式下遇到不支持属性将抛出异常。
     *
     * @param nodeType           节点类型
     * @param source             原始属性 Map
     * @param migrateUnsupported 是否将不支持属性迁移到 inactive 而非抛出异常
     * @return 活跃属性与不支持属性组成的归一化结果
     * @throws IllegalArgumentException 非迁移模式存在不支持属性或属性值非法时抛出
     */
    static NormalizedProps normalizeProps(
            String nodeType,
            Map<String, Object> source,
            boolean migrateUnsupported) {
        String normalizedType = normalize(nodeType);
        Map<String, Object> input = mutableMap(source);
        Map<String, Object> active = new LinkedHashMap<>();
        Map<String, Object> inactive = new LinkedHashMap<>();
        Set<String> allowed = ALLOWED_PROPS.getOrDefault(
                normalizedType, Set.of());
        Set<String> configKeys = CONTAINER_CONFIG_KEYS.getOrDefault(
                normalizedType, Set.of());
        Map<String, Object> nestedComponentProps =
                objectMap(input.get("componentProps"));

        if (migrateUnsupported) {
            if (!input.containsKey("label")
                    && meaningful(input.get("title"))) {
                input.put("label", input.get("title"));
            }
            if ("TEXT".equals(normalizedType)
                    && !input.containsKey("text")) {
                Object legacyText = meaningful(input.get("content"))
                        ? input.get("content")
                        : nestedComponentProps.get("content");
                if (meaningful(legacyText)) {
                    input.put("text", legacyText);
                }
            }
        }
        for (String configKey : configKeys) {
            if (!input.containsKey(configKey)
                    && nestedComponentProps.containsKey(configKey)) {
                input.put(configKey, nestedComponentProps.get(configKey));
            }
        }

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (allowed.contains(key)) {
                active.put(key, copyValue(value));
            } else if (meaningful(value)) {
                inactive.put(key, copyValue(value));
            }
        }

        if (!migrateUnsupported && !inactive.isEmpty()) {
            throw new IllegalArgumentException(
                    normalizedType + " 节点不支持属性: "
                            + String.join(", ", inactive.keySet()));
        }
        validateActiveProps(normalizedType, active);
        return new NormalizedProps(active, inactive);
    }

    /**
     * 规范化节点字段规则（仅保留活跃部分）。
     *
     * @param nodeType 节点类型
     * @param source   原始规则 Map
     * @return 归一化后的规则 Map
     */
    static Map<String, Object> normalizeRules(
            String nodeType,
            Map<String, Object> source) {
        return normalizeRules(
                nodeType,
                source,
                Map.of(),
                false).active();
    }

    /**
     * 规范化节点字段规则，拆分为活跃与不支持校验项；非迁移模式下遇到不支持项将抛出异常。
     *
     * @param nodeType           节点类型
     * @param source             原始规则 Map
     * @param props              节点属性，用于读取字段类型以决定支持哪些校验
     * @param migrateUnsupported 是否将不支持校验迁移到 inactive 而非抛出异常
     * @return 活跃规则与不支持规则组成的归一化结果
     * @throws IllegalArgumentException 节点不支持规则、校验值非法或字段类型不支持时抛出
     */
    static NormalizedRules normalizeRules(
            String nodeType,
            Map<String, Object> source,
            Map<String, Object> props,
            boolean migrateUnsupported) {
        Map<String, Object> rules = pruneMap(source);
        if (rules.isEmpty()) {
            return new NormalizedRules(Map.of(), Map.of());
        }
        if (!supportsRules(nodeType)) {
            if (migrateUnsupported) {
                return new NormalizedRules(Map.of(), rules);
            }
            throw new IllegalArgumentException(
                    normalize(nodeType) + " 节点不支持字段规则配置");
        }
        Map<String, Object> active = mutableMap(rules);
        Map<String, Object> inactive = new LinkedHashMap<>();
        Object rawValidation = active.get("validation");
        boolean wrappedValidation = rawValidation instanceof Map<?, ?>;
        if (active.containsKey("validation")
                && !wrappedValidation
                && meaningful(rawValidation)) {
            throw new IllegalArgumentException(
                    "validation 必须为对象");
        }
        Map<String, Object> validation = wrappedValidation
                ? objectMap(rawValidation)
                : new LinkedHashMap<>();
        if (!wrappedValidation) {
            for (String key : STRUCTURED_VALIDATION_KEYS) {
                if (active.containsKey(key)) {
                    validation.put(key, active.remove(key));
                }
            }
        }
        Map<String, Object> inactiveValidation = new LinkedHashMap<>();
        String fieldType = normalize(
                props == null ? null : String.valueOf(
                        props.getOrDefault("fieldType", "")));
        moveUnsupportedValidation(
                validation,
                inactiveValidation,
                fieldType,
                "minLength",
                LENGTH_VALIDATION_FIELD_TYPES);
        moveUnsupportedValidation(
                validation,
                inactiveValidation,
                fieldType,
                "maxLength",
                LENGTH_VALIDATION_FIELD_TYPES);
        moveUnsupportedValidation(
                validation,
                inactiveValidation,
                fieldType,
                "min",
                RANGE_VALIDATION_FIELD_TYPES);
        moveUnsupportedValidation(
                validation,
                inactiveValidation,
                fieldType,
                "max",
                RANGE_VALIDATION_FIELD_TYPES);
        moveUnsupportedValidation(
                validation,
                inactiveValidation,
                fieldType,
                "format",
                FORMAT_VALIDATION_FIELD_TYPES);
        validateValidationValues(validation);
        if (wrappedValidation) {
            if (validation.isEmpty()) {
                active.remove("validation");
            } else {
                active.put("validation", validation);
            }
        } else {
            active.putAll(validation);
        }
        if (!inactiveValidation.isEmpty()) {
            if (wrappedValidation) {
                inactive.put("validation", inactiveValidation);
            } else {
                inactive.putAll(inactiveValidation);
            }
        }
        if (!migrateUnsupported && !inactive.isEmpty()) {
            throw new IllegalArgumentException(
                    "FIELD " + fieldType
                            + " 不支持校验属性: "
                            + String.join(
                                    ", ",
                                    inactiveValidation.keySet()));
        }
        return new NormalizedRules(
                pruneMap(active),
                pruneMap(inactive));
    }

    /**
     * 规范化节点数据源绑定配置，仅保留该节点允许的绑定位置。
     *
     * @param nodeType 节点类型
     * @param source   原始数据源绑定 Map
     * @return 归一化后的数据源绑定 Map
     * @throws IllegalArgumentException 节点不支持数据源或位置非法、重复时抛出
     */
    static Map<String, Object> normalizeDataSourceBindings(
            String nodeType,
            Map<String, Object> source) {
        Map<String, Object> input = mutableMap(source);
        if (input.isEmpty()) {
            return Map.of();
        }
        Set<String> allowedUsages = dataSourceUsages(nodeType);
        if (allowedUsages.isEmpty()) {
            throw new IllegalArgumentException(
                    normalize(nodeType) + " 节点不支持数据源绑定");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String usage = normalize(entry.getKey());
            if (!allowedUsages.contains(usage)) {
                throw new IllegalArgumentException(
                        normalize(nodeType)
                                + " 节点不支持数据源绑定位置: "
                                + entry.getKey());
            }
            if (normalized.containsKey(usage)) {
                throw new IllegalArgumentException(
                        "数据源绑定位置重复: " + usage);
            }
            if (meaningful(entry.getValue())) {
                normalized.put(usage, copyValue(entry.getValue()));
            }
        }
        return normalized;
    }

    /**
     * 校验节点的字段绑定类型与引用一致性。
     *
     * @param nodeType    节点类型
     * @param bindingType 绑定类型，为空按 NONE 处理
     * @param bindingRef  绑定引用ID
     * @throws IllegalArgumentException 绑定类型不支持或引用与类型不匹配时抛出
     */
    static void validateBinding(
            String nodeType,
            String bindingType,
            String bindingRef) {
        String normalizedType = normalize(nodeType);
        String normalizedBinding = normalize(
                StringUtils.hasText(bindingType) ? bindingType : "NONE");
        Set<String> allowed = bindingTypes(normalizedType);
        if (!allowed.contains(normalizedBinding)) {
            throw new IllegalArgumentException(
                    normalizedType + " 节点不支持绑定类型: "
                            + normalizedBinding);
        }
        if ("NONE".equals(normalizedBinding)
                && StringUtils.hasText(bindingRef)) {
            throw new IllegalArgumentException(
                    "未绑定节点不能保存 bindingRef");
        }
        if (!"NONE".equals(normalizedBinding)
                && !StringUtils.hasText(bindingRef)) {
            throw new IllegalArgumentException(
                    normalizedBinding + " 绑定必须提供 bindingRef");
        }
    }

    /**
     * 校验节点是否允许配置扩展组件，不允许时拒绝组件名/版本/快照。
     *
     * @param nodeType         节点类型
     * @param componentName    扩展组件名
     * @param componentVersion 组件版本
     * @param snapshotVersion  快照版本
     * @throws IllegalArgumentException 节点不支持扩展但配置了扩展组件时抛出
     */
    static void validateExtension(
            String nodeType,
            String componentName,
            Integer componentVersion,
            Integer snapshotVersion) {
        String normalizedType = normalize(nodeType);
        if (!supportsExtension(normalizedType)
                && (StringUtils.hasText(componentName)
                || componentVersion != null
                || snapshotVersion != null)) {
            throw new IllegalArgumentException(
                    normalizedType + " 节点不支持扩展组件配置");
        }
    }

    /**
     * 校验组件模板配置的合法性：模板ID与版本必须成对出现，局部覆盖依赖已锁定模板。
     *
     * @param nodeType        节点类型
     * @param templateId      模板ID
     * @param templateVersion 模板版本
     * @param localOverrides  局部覆盖属性
     * @throws IllegalArgumentException 节点不支持模板或模板配置不完整时抛出
     */
    static void validateTemplate(
            String nodeType,
            String templateId,
            Integer templateVersion,
            Map<String, Object> localOverrides) {
        String normalizedType = normalize(nodeType);
        boolean hasTemplate = StringUtils.hasText(templateId);
        boolean hasVersion = templateVersion != null;
        boolean hasOverrides = meaningful(localOverrides);
        if (!supportsTemplate(normalizedType)
                && (hasTemplate || hasVersion || hasOverrides)) {
            throw new IllegalArgumentException(
                    normalizedType + " 节点不支持组件模板配置");
        }
        if (hasTemplate != hasVersion) {
            throw new IllegalArgumentException(
                    "templateId 与 templateVersion 必须同时配置");
        }
        if (hasOverrides && !hasTemplate) {
            throw new IllegalArgumentException(
                    "localOverrides 必须依赖已锁定模板");
        }
        if (hasVersion && templateVersion < 1) {
            throw new IllegalArgumentException("模板版本必须大于 0");
        }
    }

    /**
     * 判断节点是否支持扩展组件配置。
     *
     * @param nodeType 节点类型
     * @return 支持返回 true
     */
    static boolean supportsExtension(String nodeType) {
        return EXTENSIBLE_TYPES.contains(normalize(nodeType));
    }

    /**
     * 判断节点是否支持字段规则配置。
     *
     * @param nodeType 节点类型
     * @return 支持返回 true
     */
    static boolean supportsRules(String nodeType) {
        return RULE_TYPES.contains(normalize(nodeType));
    }

    /**
     * 判断节点是否支持子表单配置。
     *
     * @param nodeType 节点类型
     * @return 支持返回 true
     */
    static boolean supportsChildForm(String nodeType) {
        return CHILD_FORM_TYPES.contains(normalize(nodeType));
    }

    /**
     * 判断节点是否支持组件模板配置。
     *
     * @param nodeType 节点类型
     * @return 支持返回 true
     */
    static boolean supportsTemplate(String nodeType) {
        return TEMPLATE_TYPES.contains(normalize(nodeType));
    }

    /**
     * 返回节点支持的数据源绑定位置集合，不支持数据源返回空集。
     *
     * @param nodeType 节点类型
     * @return 允许的绑定位置集合
     */
    static Set<String> dataSourceUsages(String nodeType) {
        return switch (normalize(nodeType)) {
            case "FIELD" -> FIELD_DATA_SOURCE_USAGES;
            case "SUB_FORM", "REPEATER" -> SUB_FORM_DATA_SOURCE_USAGES;
            default -> Set.of();
        };
    }

    /**
     * 返回节点支持的字段绑定类型集合，不支持绑定的节点返回仅含 NONE。
     *
     * @param nodeType 节点类型
     * @return 允许的绑定类型集合
     */
    static Set<String> bindingTypes(String nodeType) {
        return switch (normalize(nodeType)) {
            case "FIELD" -> FIELD_BINDING_TYPES;
            case "SUB_FORM", "REPEATER" -> SUB_FORM_BINDING_TYPES;
            default -> Set.of("NONE");
        };
    }

    /**
     * 判断值是否有意义（非空、非空白字符串，或非空集合/Map）。
     *
     * @param value 待判断值
     * @return 有意义返回 true
     */
    static boolean meaningful(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(
                    EntityFormNodePropertyPolicy::meaningful);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(
                    EntityFormNodePropertyPolicy::meaningful);
        }
        return true;
    }

    /**
     * 将源 Map 转为可变的 LinkedHashMap，为空时返回空 Map。
     *
     * @param source 源 Map，可为 null
     * @return 可变 Map 副本
     */
    static Map<String, Object> mutableMap(Map<String, Object> source) {
        return source == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source);
    }

    private static Map<String, Set<String>> buildAllowedProps() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put("SECTION", union(COMMON_CONTAINER_PROPS, Set.of()));
        result.put("GRID", union(
                COMMON_CONTAINER_PROPS, Set.of("gutter", "defaultSpan")));
        result.put("TAB_SET", union(
                COMMON_CONTAINER_PROPS,
                Set.of("tabPosition")));
        result.put("TAB", union(COMMON_CONTAINER_PROPS, Set.of()));
        result.put("COLLAPSE", union(
                COMMON_CONTAINER_PROPS,
                Set.of("defaultExpanded", "accordion")));
        result.put("TEXT", union(
                COMMON_CONTAINER_PROPS,
                Set.of("text")));
        result.put("FIELD", FIELD_PROPS);
        result.put("SUB_FORM", SUB_FORM_PROPS);
        result.put("REPEATER", SUB_FORM_PROPS);
        result.put("ACTION_SLOT", Set.of("label"));
        return Map.copyOf(result);
    }

    private static Set<String> union(
            Set<String> left,
            Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return Set.copyOf(result);
    }

    private static void validateActiveProps(
            String nodeType,
            Map<String, Object> props) {
        requireText(props, "label", 500);
        switch (nodeType) {
            case "GRID" -> {
                requireIntegerRange(props, "gutter", 0, 48);
                requireIntegerRange(props, "defaultSpan", 1, 24);
            }
            case "TAB_SET" -> requireEnum(
                    props,
                    "tabPosition",
                    Set.of("top", "left", "right", "bottom"));
            case "COLLAPSE" -> {
                requireBoolean(props, "defaultExpanded");
                requireBoolean(props, "accordion");
            }
            case "TEXT" -> requireText(props, "text", 20_000);
            case "FIELD" -> validateFieldProps(props);
            case "SUB_FORM", "REPEATER" ->
                    validateSubFormProps(nodeType, props);
            default -> {
            }
        }
        rejectArbitraryUrl(props.get("componentProps"), "componentProps");
    }

    private static void validateFieldProps(Map<String, Object> props) {
        requireText(props, "fieldCode", 200);
        requireText(props, "fieldName", 500);
        requireText(props, "fieldType", 50);
        requireText(props, "componentType", 100);
        requireIntegerRange(props, "gridSpan", 1, 24);
        requireBoolean(props, "required");
        requireBoolean(props, "readonly");
        requireBoolean(props, "hidden");
        String fieldType = normalize(text(props.get("fieldType")));
        String componentType = String.valueOf(
                props.getOrDefault("componentType", ""))
                .trim()
                .toLowerCase(Locale.ROOT);
        Set<String> supported =
                BUILT_IN_COMPONENT_FIELD_TYPES.get(componentType);
        if (StringUtils.hasText(componentType) && supported == null) {
            throw new IllegalArgumentException(
                    "未注册的字段组件类型: " + componentType);
        }
        if (supported != null
                && StringUtils.hasText(fieldType)
                && !supported.contains(fieldType)) {
            throw new IllegalArgumentException(
                    "组件 " + componentType
                            + " 不兼容字段类型 "
                            + fieldType);
        }
    }

    private static void validateSubFormProps(
            String nodeType,
            Map<String, Object> props) {
        requireText(props, "fieldCode", 200);
        requireText(props, "fieldName", 500);
        requireIntegerRange(props, "gridSpan", 1, 24);
        String expectedFieldType = "REPEATER".equals(nodeType)
                ? "SUB_FORM_LIST" : "SUB_FORM";
        String expectedComponentType = "REPEATER".equals(nodeType)
                ? "sub_form_list" : "sub_form";
        if (props.containsKey("fieldType")
                && !expectedFieldType.equals(
                        normalize(text(props.get("fieldType"))))) {
            throw new IllegalArgumentException(
                    nodeType + " 节点 fieldType 必须为 "
                            + expectedFieldType);
        }
        if (props.containsKey("componentType")
                && !expectedComponentType.equals(
                        String.valueOf(props.get("componentType"))
                                .trim()
                                .toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    nodeType + " 节点 componentType 必须为 "
                            + expectedComponentType);
        }
        Map<String, Object> componentProps =
                objectMap(props.get("componentProps"));
        Map<String, Object> subFormConfig =
                objectMap(componentProps.get("subFormConfig"));
        requireEnum(
                subFormConfig,
                "displayMode",
                Set.of("embedded", "tab"));
        requireEnum(
                subFormConfig,
                "layout",
                Set.of("form", "table"));
        requireBoolean(subFormConfig, "repeatable");
    }

    private static void moveUnsupportedValidation(
            Map<String, Object> validation,
            Map<String, Object> inactive,
            String fieldType,
            String key,
            Set<String> supportedTypes) {
        if (validation.containsKey(key)
                && !supportedTypes.contains(fieldType)) {
            inactive.put(key, validation.remove(key));
        }
    }

    private static void validateValidationValues(
            Map<String, Object> validation) {
        requireIntegerRange(validation, "minLength", 0, 20_000);
        requireIntegerRange(validation, "maxLength", 0, 20_000);
        requireNumber(validation, "min");
        requireNumber(validation, "max");
        requireEnum(validation, "format", VALID_FORMATS);
        Integer minLength = integerValue(validation.get("minLength"));
        Integer maxLength = integerValue(validation.get("maxLength"));
        if (minLength != null
                && maxLength != null
                && minLength > maxLength) {
            throw new IllegalArgumentException(
                    "最小长度不能大于最大长度");
        }
        Double min = numberValue(validation.get("min"));
        Double max = numberValue(validation.get("max"));
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException(
                    "最小值不能大于最大值");
        }
    }

    private static void requireText(
            Map<String, Object> value,
            String key,
            int maxLength) {
        if (!value.containsKey(key) || value.get(key) == null) {
            return;
        }
        if (!(value.get(key) instanceof String text)) {
            throw new IllegalArgumentException(
                    key + " 必须为字符串");
        }
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(
                    key + " 长度不能超过 " + maxLength);
        }
    }

    private static void requireBoolean(
            Map<String, Object> value,
            String key) {
        if (value.containsKey(key)
                && value.get(key) != null
                && !(value.get(key) instanceof Boolean)) {
            throw new IllegalArgumentException(
                    key + " 必须为布尔值");
        }
    }

    private static void requireIntegerRange(
            Map<String, Object> value,
            String key,
            int min,
            int max) {
        if (!value.containsKey(key) || value.get(key) == null) {
            return;
        }
        Integer number = integerValue(value.get(key));
        if (number == null || number < min || number > max) {
            throw new IllegalArgumentException(
                    key + " 必须在 " + min + "-" + max + " 范围内");
        }
    }

    private static void requireNumber(
            Map<String, Object> value,
            String key) {
        if (value.containsKey(key)
                && value.get(key) != null
                && numberValue(value.get(key)) == null) {
            throw new IllegalArgumentException(
                    key + " 必须为数字");
        }
    }

    private static void requireEnum(
            Map<String, Object> value,
            String key,
            Set<String> allowed) {
        if (!value.containsKey(key)
                || !meaningful(value.get(key))) {
            return;
        }
        String candidate = String.valueOf(value.get(key))
                .trim()
                .toLowerCase(Locale.ROOT);
        boolean matches = allowed.stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(candidate::equals);
        if (!matches) {
            throw new IllegalArgumentException(
                    key + " 不支持值: " + value.get(key));
        }
    }

    private static Integer integerValue(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double candidate = number.doubleValue();
        if (candidate % 1 != 0) {
            return null;
        }
        return number.intValue();
    }

    private static Double numberValue(Object value) {
        return value instanceof Number number
                ? number.doubleValue()
                : null;
    }

    private static void rejectArbitraryUrl(
            Object value,
            String path) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object nested = entry.getValue();
                if ("apiurl".equalsIgnoreCase(key)
                        && meaningful(nested)) {
                    throw new IllegalArgumentException(
                            path + "." + key
                                    + " 不允许配置任意 URL，请使用受控数据源");
                }
                rejectArbitraryUrl(nested, path + "." + key);
            }
        } else if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object nested : collection) {
                rejectArbitraryUrl(
                        nested,
                        path + "[" + index + "]");
                index++;
            }
        }
    }

    private static Map<String, Object> pruneMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = pruneValue(entry.getValue());
            if (meaningful(value)) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private static Object pruneValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object nested = pruneValue(entry.getValue());
                if (meaningful(nested)) {
                    result.put(String.valueOf(entry.getKey()), nested);
                }
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : collection) {
                Object nested = pruneValue(item);
                if (meaningful(nested)) {
                    result.add(nested);
                }
            }
            return result;
        }
        return value;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) ->
                result.put(String.valueOf(key), copyValue(item)));
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) ->
                    result.put(String.valueOf(key), copyValue(item)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            collection.forEach(item -> result.add(copyValue(item)));
            return result;
        }
        return value;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String normalize(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    record NormalizedProps(
            Map<String, Object> active,
            Map<String, Object> inactive) {
    }

    record NormalizedRules(
            Map<String, Object> active,
            Map<String, Object> inactive) {
    }
}
