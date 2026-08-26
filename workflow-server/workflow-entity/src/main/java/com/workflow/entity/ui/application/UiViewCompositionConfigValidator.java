package com.workflow.entity.ui.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * “关联内容”配置文档白名单校验器。
 *
 * <p>配置只允许四步引导能够生成的声明式字段。接口服务和组件必须使用注册
 * 标识，不接受脚本、SQL、动态 URL、类名或 Bean 名；配置缺失或特殊处理失败
 * 时由调用方按显式失败策略终止，不存在隐式回退。</p>
 */
@Component
public class UiViewCompositionConfigValidator {

    private static final Set<String> ROOT_KEYS = Set.of(
            "schemaVersion", "name", "enabled", "source",
            "target", "presentation", "relation", "actions",
            "actionSettings", "specialHandling", "entitySnapshots");
    private static final Set<String> SOURCE_KEYS = Set.of(
            "entityId", "entityCode", "entityName");
    private static final Set<String> TARGET_KEYS = Set.of(
            "entityId", "entityCode", "entityName",
            "contentType", "contentId", "contentKey", "contentName",
            "releaseId", "releaseVersion", "contentHash");
    private static final Set<String> PRESENTATION_KEYS = Set.of(
            "position", "loadMode", "title", "emptyText");
    private static final Set<String> RELATION_KEYS = Set.of(
            "type", "relationCode", "relationName",
            "sourceField", "sourceFieldName",
            "targetField", "targetFieldName", "mappings");
    private static final Set<String> MAPPING_KEYS = Set.of(
            "source", "target", "sourceField", "targetField",
            "sourceLabel", "targetLabel", "literal", "required");
    private static final Set<String> ACTION_SETTINGS_KEYS = Set.of(
            "select", "create");
    private static final Set<String> SELECT_SETTINGS_KEYS = Set.of(
            "mode", "result", "mappings");
    private static final Set<String> CREATE_SETTINGS_KEYS = Set.of(
            "associateAfterCreate", "initialMappings");
    private static final Set<String> SPECIAL_KEYS = Set.of(
            "mode", "interfaceService", "actionServices", "customComponent",
            "failurePolicy");
    private static final Set<String> INTERFACE_SERVICE_KEYS = Set.of(
            "serviceId", "sourceCode", "serviceName", "serviceRevision",
            "operationCode", "operationName", "inputMappings",
            "outputMappings", "executableSnapshot", "definitionHash");
    private static final Set<String> ACTION_SERVICE_KEYS = Set.of(
            "actionKey", "serviceId", "sourceCode", "serviceName",
            "serviceRevision", "operationCode", "operationName",
            "inputMappings", "outputMappings", "failurePolicy",
            "executableSnapshot", "definitionHash");
    private static final Set<String> CUSTOM_COMPONENT_KEYS = Set.of(
            "name", "displayName", "version", "extensionType",
            "snapshotVersion", "artifactDigest", "props", "definitionSnapshot",
            "definitionHash");
    private static final Set<String> ENTITY_SNAPSHOTS_KEYS = Set.of(
            "source", "target");
    private static final Set<String> ENTITY_SNAPSHOT_KEYS = Set.of(
            "historyId", "entityId", "entityCode", "version",
            "schemaHash");

    private static final Set<String> CONTENT_TYPES = Set.of("FORM", "LIST");
    private static final Set<String> POSITIONS = Set.of(
            "INLINE", "TAB", "ROW_EXPAND", "DIALOG", "DRAWER", "PAGE");
    private static final Set<String> LOAD_MODES = Set.of(
            "IMMEDIATE", "ON_DEMAND");
    private static final Set<String> RELATION_TYPES = Set.of(
            "SAME_RECORD", "ENTITY_RELATION", "REFERENCE_FIELD",
            "REVERSE_REFERENCE", "FIELD_MATCH", "INTERFACE_SERVICE");
    private static final Set<String> ACTIONS = Set.of(
            "VIEW", "SELECT", "CREATE", "EDIT", "LINK", "UNLINK",
            "SAVE_WITH_FORM");
    private static final Set<String> SELECT_MODES = Set.of(
            "SINGLE", "MULTIPLE");
    private static final Set<String> SELECT_RESULTS = Set.of(
            "FILL_FIELDS", "LINK");
    private static final Set<String> SPECIAL_MODES = Set.of(
            "NONE", "INTERFACE_SERVICE", "CUSTOM_COMPONENT", "BOTH");
    private static final Set<String> FAILURE_POLICIES = Set.of(
            "ERROR", "PLACEHOLDER", "HIDE");
    private static final Set<String> FORBIDDEN_KEY_TOKENS = Set.of(
            "script", "sql", "url", "class", "bean");

    private static final Pattern BUSINESS_KEY =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");
    private static final Pattern CONTENT_HASH =
            Pattern.compile("[A-Fa-f0-9]{64}");
    private static final int MAX_MAPPINGS = 50;
    private static final int MAX_COMPONENT_PROPS = 50;
    private static final int MAX_ACTION_SERVICES = 20;

    /**
     * 校验并规范化关联内容配置。
     *
     * @param config 配置文档
     * @return 规范化配置、可读摘要和非阻断提示
     * @throws IllegalArgumentException 配置含未知字段、禁用能力或缺少条件字段
     */
    public ValidationResult validate(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("关联内容配置不能为空");
        }
        validateNoForbiddenContent(config, "$", 0);
        requireAllowedKeys(config, ROOT_KEYS, "关联内容配置");

        int schemaVersion = config.get("schemaVersion") == null
                ? 1 : requirePositiveInteger(
                config.get("schemaVersion"), "配置版本");
        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    "暂不支持的关联内容配置版本: " + schemaVersion);
        }
        String name = optionalText(config.get("name"), 80, "配置名称");
        boolean enabled = config.get("enabled") == null
                || requireBoolean(config.get("enabled"), "是否启用");
        Map<String, Object> source = normalizeSource(
                optionalMap(config.get("source"), "来源内容"));
        Map<String, Object> target = normalizeTarget(
                requireMap(config.get("target"), "显示什么"));
        Map<String, Object> presentation = normalizePresentation(
                requireMap(config.get("presentation"), "显示方式"));
        Map<String, Object> relation = normalizeRelation(
                requireMap(config.get("relation"), "数据关联"));
        List<String> actions = normalizeActions(config.get("actions"));
        Map<String, Object> actionSettings = normalizeActionSettings(
                optionalMap(config.get("actionSettings"), "操作设置"));
        Map<String, Object> special = normalizeSpecialHandling(
                optionalMap(config.get("specialHandling"), "特殊处理"));
        Map<String, Object> entitySnapshots = normalizeEntitySnapshots(
                optionalMap(config.get("entitySnapshots"), "实体发布快照"));

        validateCrossFields(
                target,
                presentation,
                relation,
                actions,
                actionSettings,
                special);

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("schemaVersion", schemaVersion);
        normalized.put("name", name == null ? "" : name);
        normalized.put("enabled", enabled);
        normalized.put("source", source);
        normalized.put("target", target);
        normalized.put("presentation", presentation);
        normalized.put("relation", relation);
        normalized.put("actions", actions);
        normalized.put("actionSettings", actionSettings);
        normalized.put("specialHandling", special);
        if (!entitySnapshots.isEmpty()) {
            normalized.put("entitySnapshots", entitySnapshots);
        }
        return new ValidationResult(
                normalized,
                buildSummary(target, presentation),
                buildWarnings(actions));
    }

    private Map<String, Object> normalizeSource(Map<String, Object> value) {
        if (value.isEmpty()) {
            return Map.of();
        }
        requireAllowedKeys(value, SOURCE_KEYS, "来源内容");
        Map<String, Object> source = new LinkedHashMap<>();
        putOptionalText(source, "entityId", value.get("entityId"), 64);
        putOptionalText(source, "entityCode", value.get("entityCode"), 100);
        putOptionalText(source, "entityName", value.get("entityName"), 200);
        return source;
    }

    private Map<String, Object> normalizeTarget(Map<String, Object> value) {
        requireAllowedKeys(value, TARGET_KEYS, "显示内容");
        Map<String, Object> target = new LinkedHashMap<>();
        putRequiredText(target, "entityId", value.get("entityId"), 64,
                "目标实体");
        putOptionalText(target, "entityCode", value.get("entityCode"), 100);
        putOptionalText(target, "entityName", value.get("entityName"), 200);
        target.put("contentType", requireEnum(
                value.get("contentType"), CONTENT_TYPES, "显示内容类型"));
        putRequiredText(target, "contentId", value.get("contentId"), 64,
                "目标表单或列表");
        putOptionalText(target, "contentKey", value.get("contentKey"), 100);
        putOptionalText(target, "contentName", value.get("contentName"), 200);
        putOptionalText(target, "releaseId", value.get("releaseId"), 64);
        if (value.get("releaseVersion") != null) {
            int version = requirePositiveInteger(
                    value.get("releaseVersion"), "目标发布版本");
            target.put("releaseVersion", version);
        }
        if (value.get("contentHash") != null) {
            String hash = requireText(
                    value.get("contentHash"), 64, "目标发布内容哈希");
            if (!CONTENT_HASH.matcher(hash).matches()) {
                throw new IllegalArgumentException(
                        "目标发布内容哈希必须为 64 位十六进制字符串");
            }
            target.put("contentHash", hash.toLowerCase(Locale.ROOT));
        }
        boolean hasReleaseField = target.containsKey("releaseId")
                || target.containsKey("releaseVersion")
                || target.containsKey("contentHash");
        if (hasReleaseField
                && !(target.containsKey("releaseId")
                && target.containsKey("releaseVersion")
                && target.containsKey("contentHash"))) {
            throw new IllegalArgumentException(
                    "目标发布版本必须同时包含 releaseId、releaseVersion 和 contentHash");
        }
        return target;
    }

    private Map<String, Object> normalizePresentation(
            Map<String, Object> value) {
        requireAllowedKeys(value, PRESENTATION_KEYS, "显示方式");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("position", requireEnum(
                value.get("position"), POSITIONS, "显示位置"));
        result.put("loadMode", requireEnum(
                value.getOrDefault("loadMode", "ON_DEMAND"),
                LOAD_MODES,
                "加载方式"));
        putOptionalText(result, "title", value.get("title"), 200);
        putOptionalText(result, "emptyText", value.get("emptyText"), 500);
        return result;
    }

    private Map<String, Object> normalizeRelation(Map<String, Object> value) {
        requireAllowedKeys(value, RELATION_KEYS, "数据关联");
        Map<String, Object> result = new LinkedHashMap<>();
        String type = requireEnum(
                value.get("type"), RELATION_TYPES, "关联方式");
        result.put("type", type);
        putOptionalBusinessKey(result, "relationCode", value.get("relationCode"));
        putOptionalText(result, "relationName", value.get("relationName"), 200);
        putOptionalBusinessKey(result, "sourceField", value.get("sourceField"));
        putOptionalText(result, "sourceFieldName", value.get("sourceFieldName"), 200);
        putOptionalBusinessKey(result, "targetField", value.get("targetField"));
        putOptionalText(result, "targetFieldName", value.get("targetFieldName"), 200);
        List<Map<String, Object>> mappings = normalizeMappings(
                value.get("mappings"), "字段匹配", false);
        if (!mappings.isEmpty()) {
            result.put("mappings", mappings);
        }
        switch (type) {
            case "ENTITY_RELATION" -> requirePresent(
                    result, "relationCode", "使用已有实体关系时必须选择关系");
            case "REFERENCE_FIELD" -> requirePresent(
                    result, "sourceField", "使用引用字段时必须选择来源字段");
            case "REVERSE_REFERENCE" -> requirePresent(
                    result, "targetField", "使用反向引用时必须选择目标字段");
            case "FIELD_MATCH" -> {
                boolean directPair = result.containsKey("sourceField")
                        && result.containsKey("targetField");
                if (mappings.isEmpty() && !directPair) {
                    throw new IllegalArgumentException(
                            "使用字段匹配时必须选择来源字段和目标字段，或配置字段映射");
                }
            }
            default -> {
                // SAME_RECORD 和 INTERFACE_SERVICE 不需要额外关系字段。
            }
        }
        return result;
    }

    private List<String> normalizeActions(Object raw) {
        if (!(raw instanceof Collection<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个允许操作");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            result.add(requireEnum(value, ACTIONS, "允许操作"));
        }
        return List.copyOf(result);
    }

    /**
     * 规范化第三步的业务动作设置。这里保留选择结果与新增初值映射，运行时
     * 只能据此生成受控字段补丁，不能接受客户端临时指定任意字段路径。
     */
    private Map<String, Object> normalizeActionSettings(
            Map<String, Object> value) {
        requireAllowedKeys(value, ACTION_SETTINGS_KEYS, "操作设置");
        Map<String, Object> select = optionalMap(
                value.get("select"), "选择记录设置");
        Map<String, Object> create = optionalMap(
                value.get("create"), "新增记录设置");
        requireAllowedKeys(select, SELECT_SETTINGS_KEYS, "选择记录设置");
        requireAllowedKeys(create, CREATE_SETTINGS_KEYS, "新增记录设置");

        Map<String, Object> normalizedSelect = new LinkedHashMap<>();
        normalizedSelect.put("mode", requireEnum(
                select.getOrDefault("mode", "SINGLE"),
                SELECT_MODES,
                "选择方式"));
        normalizedSelect.put("result", requireEnum(
                select.getOrDefault("result", "FILL_FIELDS"),
                SELECT_RESULTS,
                "选择记录后的处理方式"));
        normalizedSelect.put("mappings", normalizeMappings(
                select.get("mappings"), "选择结果回填", true));

        Map<String, Object> normalizedCreate = new LinkedHashMap<>();
        normalizedCreate.put(
                "associateAfterCreate",
                create.get("associateAfterCreate") != null
                        && requireBoolean(
                        create.get("associateAfterCreate"),
                        "新增后自动建立关联"));
        normalizedCreate.put("initialMappings", normalizeMappings(
                create.get("initialMappings"), "新增初值", true));
        return Map.of(
                "select", normalizedSelect,
                "create", normalizedCreate);
    }

    private Map<String, Object> normalizeSpecialHandling(
            Map<String, Object> value) {
        if (value.isEmpty()) {
            return Map.of(
                    "mode", "NONE",
                    "failurePolicy", "ERROR");
        }
        requireAllowedKeys(value, SPECIAL_KEYS, "特殊处理");
        String mode = requireEnum(
                value.getOrDefault("mode", "NONE"),
                SPECIAL_MODES,
                "特殊处理方式");
        String failurePolicy = requireEnum(
                value.getOrDefault("failurePolicy", "ERROR"),
                FAILURE_POLICIES,
                "失败处理");
        Map<String, Object> rawService = optionalMap(
                value.get("interfaceService"), "接口服务");
        Map<String, Object> rawComponent = optionalMap(
                value.get("customComponent"), "自定义组件");
        List<Map<String, Object>> rawActionServices = mapListValue(
                value.get("actionServices"), "动作接口服务");
        requireAllowedKeys(rawService, INTERFACE_SERVICE_KEYS, "接口服务");
        requireAllowedKeys(rawComponent, CUSTOM_COMPONENT_KEYS, "自定义组件");
        boolean serviceConfigured = hasTextValue(
                rawService.get("serviceId"))
                || hasTextValue(rawService.get("operationCode"))
                || !rawActionServices.isEmpty();
        boolean componentConfigured = hasTextValue(rawComponent.get("name"));
        if ("NONE".equals(mode) && (serviceConfigured || componentConfigured)) {
            throw new IllegalArgumentException(
                    "已选择接口服务或自定义组件时，必须显式启用特殊处理");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        boolean useService = "INTERFACE_SERVICE".equals(mode)
                || "BOTH".equals(mode);
        boolean useComponent = "CUSTOM_COMPONENT".equals(mode)
                || "BOTH".equals(mode);
        if (useService) {
            if (!rawService.isEmpty()) {
                result.put("interfaceService", normalizeInterfaceService(
                        rawService));
            }
            result.put("actionServices", normalizeActionServices(
                    rawActionServices));
        }
        if (useComponent) {
            result.put("customComponent", normalizeCustomComponent(
                    rawComponent));
        }
        result.put("failurePolicy", failurePolicy);

        boolean hasService = result.containsKey("interfaceService")
                || !mapListValue(result.get("actionServices"),
                "动作接口服务").isEmpty();
        boolean hasComponent = result.containsKey("customComponent");
        if (("INTERFACE_SERVICE".equals(mode) || "BOTH".equals(mode))
                && !hasService) {
            throw new IllegalArgumentException("特殊处理必须选择接口服务");
        }
        if (("CUSTOM_COMPONENT".equals(mode) || "BOTH".equals(mode))
                && !hasComponent) {
            throw new IllegalArgumentException("特殊处理必须选择自定义组件");
        }
        return result;
    }

    /** 规范化按 actionKey 显式绑定的数据或动作接口服务。 */
    private List<Map<String, Object>> normalizeActionServices(
            List<Map<String, Object>> values) {
        if (values.size() > MAX_ACTION_SERVICES) {
            throw new IllegalArgumentException(
                    "动作接口服务不能超过 " + MAX_ACTION_SERVICES + " 项");
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> value = values.get(index);
            requireAllowedKeys(
                    value, ACTION_SERVICE_KEYS,
                    "动作接口服务第 " + (index + 1) + " 项");
            Map<String, Object> normalized = new LinkedHashMap<>();
            String actionKey = requireText(
                    value.get("actionKey"), 100, "动作接口服务对应操作");
            requireBusinessKey(actionKey, "动作接口服务对应操作");
            if (!keys.add(actionKey)) {
                throw new IllegalArgumentException(
                        "同一个操作只能绑定一个接口服务: " + actionKey);
            }
            normalized.put("actionKey", actionKey);
            Map<String, Object> serviceValue = new LinkedHashMap<>();
            INTERFACE_SERVICE_KEYS.forEach(key -> {
                if (value.containsKey(key)) {
                    serviceValue.put(key, value.get(key));
                }
            });
            normalized.putAll(normalizeInterfaceService(serviceValue));
            normalized.put("failurePolicy", requireEnum(
                    value.getOrDefault("failurePolicy", "ERROR"),
                    FAILURE_POLICIES,
                    "动作接口服务失败处理"));
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private Map<String, Object> normalizeInterfaceService(
            Map<String, Object> value) {
        requireAllowedKeys(value, INTERFACE_SERVICE_KEYS, "接口服务");
        Map<String, Object> result = new LinkedHashMap<>();
        putRequiredText(result, "serviceId", value.get("serviceId"), 64,
                "接口服务");
        putOptionalBusinessKey(
                result, "sourceCode", value.get("sourceCode"));
        putOptionalText(result, "serviceName", value.get("serviceName"), 200);
        if (value.get("serviceRevision") != null) {
            result.put("serviceRevision", requirePositiveInteger(
                    value.get("serviceRevision"), "接口服务修订号"));
        }
        putRequiredBusinessKey(
                result, "operationCode", value.get("operationCode"),
                "接口操作");
        putOptionalText(result, "operationName", value.get("operationName"), 200);
        result.put("inputMappings", normalizeMappings(
                value.get("inputMappings"), "接口输入映射", true));
        result.put("outputMappings", normalizeMappings(
                value.get("outputMappings"), "接口输出映射", true));
        copyPinnedSnapshotFields(value, result);
        return result;
    }

    private Map<String, Object> normalizeCustomComponent(
            Map<String, Object> value) {
        requireAllowedKeys(value, CUSTOM_COMPONENT_KEYS, "自定义组件");
        Map<String, Object> result = new LinkedHashMap<>();
        putRequiredBusinessKey(result, "name", value.get("name"),
                "自定义组件");
        putOptionalText(result, "displayName", value.get("displayName"), 200);
        result.put("version", requirePositiveInteger(
                value.get("version"), "自定义组件版本"));
        if (value.get("artifactDigest") != null) {
            String digest = requireText(
                    value.get("artifactDigest"), 64,
                    "自定义组件制品摘要").toLowerCase(Locale.ROOT);
            if (!CONTENT_HASH.matcher(digest).matches()) {
                throw new IllegalArgumentException(
                        "自定义组件制品摘要必须为 64 位十六进制字符串");
            }
            result.put("artifactDigest", digest);
        }
        if (value.get("extensionType") != null) {
            result.put("extensionType", requireEnum(
                    value.get("extensionType"),
                    Set.of("NODE", "FORM", "LIST"),
                    "自定义组件类型"));
        }
        if (value.get("snapshotVersion") != null) {
            result.put("snapshotVersion", requirePositiveInteger(
                    value.get("snapshotVersion"), "自定义组件快照版本"));
        }
        Map<String, Object> props = optionalMap(
                value.get("props"), "自定义组件参数");
        if (props.size() > MAX_COMPONENT_PROPS) {
            throw new IllegalArgumentException(
                    "自定义组件参数不能超过 " + MAX_COMPONENT_PROPS + " 项");
        }
        Map<String, Object> normalizedProps = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            requireBusinessKey(entry.getKey(), "组件参数名");
            validateComponentPropValue(entry.getValue(), entry.getKey(), 0);
            normalizedProps.put(entry.getKey(), entry.getValue());
        }
        result.put("props", normalizedProps);
        copyPinnedSnapshotFields(value, result);
        return result;
    }

    /**
     * 实体快照是平台在宿主发布时写入的内部钉定信息。
     * 它不向配置人员暴露，但历史发布在激活、热修复和运行时
     * 必须通过同一白名单校验，因此需完整保留两端身份与指纹。
     */
    private Map<String, Object> normalizeEntitySnapshots(
            Map<String, Object> value) {
        if (value.isEmpty()) {
            return Map.of();
        }
        requireAllowedKeys(
                value, ENTITY_SNAPSHOTS_KEYS, "实体发布快照");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", normalizeEntitySnapshot(
                requireMap(value.get("source"), "来源实体发布快照"),
                "来源实体发布快照"));
        result.put("target", normalizeEntitySnapshot(
                requireMap(value.get("target"), "目标实体发布快照"),
                "目标实体发布快照"));
        return result;
    }

    private Map<String, Object> normalizeEntitySnapshot(
            Map<String, Object> value,
            String label) {
        requireAllowedKeys(value, ENTITY_SNAPSHOT_KEYS, label);
        Map<String, Object> result = new LinkedHashMap<>();
        putRequiredText(
                result, "historyId", value.get("historyId"), 64,
                label + "历史ID");
        putRequiredText(
                result, "entityId", value.get("entityId"), 64,
                label + "实体ID");
        putRequiredText(
                result, "entityCode", value.get("entityCode"), 100,
                label + "实体编码");
        result.put("version", requirePositiveInteger(
                value.get("version"), label + "版本"));
        String hash = requireText(
                value.get("schemaHash"), 64, label + "完整性哈希");
        if (!CONTENT_HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException(
                    label + "完整性哈希必须为 64 位十六进制字符串");
        }
        result.put("schemaHash", hash.toLowerCase(Locale.ROOT));
        return result;
    }

    /**
     * 发布态依赖快照会经过同一白名单校验器用于激活和热修复。
     * 设计态即使伪造这两个字段，发布时也会被服务端权威记录覆盖。
     */
    private void copyPinnedSnapshotFields(
            Map<String, Object> source,
            Map<String, Object> target) {
        if (source.get("executableSnapshot") != null) {
            target.put("executableSnapshot", requireText(
                    source.get("executableSnapshot"),
                    1_048_576,
                    "接口操作发布快照"));
        }
        if (source.get("definitionSnapshot") != null) {
            target.put("definitionSnapshot", requireText(
                    source.get("definitionSnapshot"),
                    1_048_576,
                    "组件定义发布快照"));
        }
        if (source.get("definitionHash") != null) {
            String hash = requireText(
                    source.get("definitionHash"),
                    64,
                    "发布依赖哈希");
            if (!CONTENT_HASH.matcher(hash).matches()) {
                throw new IllegalArgumentException(
                        "发布依赖哈希必须为 64 位十六进制字符串");
            }
            target.put("definitionHash", hash.toLowerCase(Locale.ROOT));
        }
    }

    private List<Map<String, Object>> normalizeMappings(
            Object raw,
            String label,
            boolean optional) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException(label + "必须为数组");
        }
        if (values.size() > MAX_MAPPINGS) {
            throw new IllegalArgumentException(
                    label + "不能超过 " + MAX_MAPPINGS + " 项");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> value = requireMap(
                    values.get(index), label + "第 " + (index + 1) + " 项");
            requireAllowedKeys(value, MAPPING_KEYS,
                    label + "第 " + (index + 1) + " 项");
            Map<String, Object> mapping = new LinkedHashMap<>();
            copyOptionalMappingText(mapping, value, "source");
            copyOptionalMappingText(mapping, value, "target");
            copyOptionalMappingText(mapping, value, "sourceField");
            copyOptionalMappingText(mapping, value, "targetField");
            putOptionalText(mapping, "sourceLabel", value.get("sourceLabel"), 200);
            putOptionalText(mapping, "targetLabel", value.get("targetLabel"), 200);
            if (value.containsKey("literal")) {
                validateLiteral(value.get("literal"), label);
                mapping.put("literal", value.get("literal"));
            }
            mapping.put("required", value.get("required") == null
                    || requireBoolean(value.get("required"), label + "是否必填"));
            boolean hasSource = mapping.containsKey("source")
                    || mapping.containsKey("sourceField")
                    || mapping.containsKey("literal");
            boolean hasTarget = mapping.containsKey("target")
                    || mapping.containsKey("targetField");
            if (!hasSource || !hasTarget) {
                throw new IllegalArgumentException(
                        label + "第 " + (index + 1)
                                + " 项必须同时声明来源和目标");
            }
            result.add(mapping);
        }
        if (!optional && raw != null && result.isEmpty()) {
            return List.of();
        }
        return List.copyOf(result);
    }

    private void validateCrossFields(
            Map<String, Object> target,
            Map<String, Object> presentation,
            Map<String, Object> relation,
            List<String> actions,
            Map<String, Object> actionSettings,
            Map<String, Object> special) {
        if ("ROW_EXPAND".equals(presentation.get("position"))
                && "IMMEDIATE".equals(presentation.get("loadMode"))) {
            throw new IllegalArgumentException(
                    "行展开内容必须按需加载，避免列表一次加载全部关联数据");
        }
        if (actions.contains("SAVE_WITH_FORM")) {
            throw new IllegalArgumentException(
                    "关联内容尚未接入随当前表单统一提交；组成型数据请使用已有子表单或重复器");
        }
        if (actions.contains("SELECT")
                && !"LIST".equals(target.get("contentType"))) {
            throw new IllegalArgumentException("选择记录仅适用于目标列表");
        }
        if ((actions.contains("CREATE") || actions.contains("EDIT"))
                && !"FORM".equals(target.get("contentType"))) {
            throw new IllegalArgumentException(
                    "新增或编辑记录需要选择目标表单；目标列表只负责查看、选择或关联");
        }
        Map<String, Object> select = optionalMap(
                actionSettings.get("select"), "选择记录设置");
        if (actions.contains("SELECT")) {
            String selectResult = String.valueOf(select.get("result"));
            List<Map<String, Object>> mappings = normalizeMappings(
                    select.get("mappings"), "选择结果回填", true);
            if ("FILL_FIELDS".equals(selectResult) && mappings.isEmpty()) {
                throw new IllegalArgumentException(
                        "选择记录并回填当前表单时必须配置至少一项字段映射");
            }
            if ("FILL_FIELDS".equals(selectResult)
                    && "MULTIPLE".equals(select.get("mode"))) {
                throw new IllegalArgumentException(
                        "回填普通表单字段时只能选择一条记录");
            }
            if ("LINK".equals(selectResult)
                    && !actions.contains("LINK")) {
                throw new IllegalArgumentException(
                        "选择后建立关联时必须同时启用建立关联");
            }
        }
        Set<String> relationshipTypes = Set.of(
                "REFERENCE_FIELD", "REVERSE_REFERENCE", "ENTITY_RELATION");
        if ((actions.contains("LINK") || actions.contains("UNLINK"))
                && !relationshipTypes.contains(relation.get("type"))) {
            throw new IllegalArgumentException(
                    "建立或解除关联仅适用于引用字段或已有实体关系");
        }
        Map<String, Object> create = optionalMap(
                actionSettings.get("create"), "新增记录设置");
        if (actions.contains("CREATE")
                && Boolean.TRUE.equals(create.get("associateAfterCreate"))) {
            throw new IllegalArgumentException(
                    "新增后自动建立关联尚未接入同一事务，请关闭后分别保存和建立关联");
        }
        if ("INTERFACE_SERVICE".equals(relation.get("type"))
                && !(special.get("interfaceService")
                instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "通过接口服务关联数据时，必须在特殊处理中单独选择数据解析接口");
        }
    }

    private String buildSummary(
            Map<String, Object> target,
            Map<String, Object> presentation) {
        Map<String, String> positionNames = Map.of(
                "INLINE", "嵌入当前页面",
                "TAB", "页签",
                "ROW_EXPAND", "行展开",
                "DIALOG", "弹窗",
                "DRAWER", "抽屉",
                "PAGE", "全屏打开");
        String targetName = firstNonBlank(
                target.get("contentName"),
                target.get("contentKey"),
                target.get("contentId"));
        String contentName = "FORM".equals(target.get("contentType"))
                ? "表单" : "列表";
        return "以" + positionNames.get(presentation.get("position"))
                + "方式显示“" + targetName + "”" + contentName;
    }

    private List<String> buildWarnings(List<String> actions) {
        if (actions.contains("SAVE_WITH_FORM")) {
            return List.of("关联内容不能随宿主统一提交，请使用已有组成型子表单或重复器。");
        }
        if (actions.contains("CREATE") || actions.contains("EDIT")
                || actions.contains("LINK") || actions.contains("UNLINK")) {
            return List.of("目标内容独立保存，不随当前表单一起提交。");
        }
        return List.of();
    }

    private void validateNoForbiddenContent(
            Object value,
            String path,
            int depth) {
        if (depth > 8) {
            throw new IllegalArgumentException("关联内容配置嵌套不能超过 8 层");
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalized = key.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]", "");
                for (String token : FORBIDDEN_KEY_TOKENS) {
                    if (normalized.equals(token)
                            || normalized.endsWith(token)
                            || normalized.startsWith(token)) {
                        throw new IllegalArgumentException(
                                "关联内容配置禁止使用字段: " + path + "." + key);
                    }
                }
                validateNoForbiddenContent(
                        entry.getValue(), path + "." + key, depth + 1);
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                validateNoForbiddenContent(
                        list.get(index), path + "[" + index + "]", depth + 1);
            }
        } else if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("http://")
                    || normalized.startsWith("https://")
                    || normalized.startsWith("jdbc:")
                    || normalized.startsWith("javascript:")) {
                throw new IllegalArgumentException(
                        "关联内容配置禁止使用动态地址或脚本: " + path);
            }
        }
    }

    private void validateComponentPropValue(Object value, String key, int depth) {
        if (depth > 6) {
            throw new IllegalArgumentException(
                    "自定义组件参数 " + key + " 嵌套过深");
        }
        if (value == null || value instanceof String
                || value instanceof Number || value instanceof Boolean) {
            return;
        }
        if (value instanceof List<?> list && list.size() <= 100) {
            for (Object item : list) {
                validateComponentPropValue(item, key, depth + 1);
            }
            return;
        }
        if (value instanceof Map<?, ?> map && map.size() <= 50) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childKey = String.valueOf(entry.getKey());
                requireBusinessKey(childKey, "组件参数名");
                validateComponentPropValue(
                        entry.getValue(), key + "." + childKey, depth + 1);
            }
            return;
        }
        throw new IllegalArgumentException(
                "自定义组件参数 " + key + " 仅支持简单值或简单值数组");
    }

    private void validateLiteral(Object value, String label) {
        if (value != null && !(value instanceof String)
                && !(value instanceof Number) && !(value instanceof Boolean)) {
            throw new IllegalArgumentException(label + "固定值只能是简单值");
        }
    }

    private Map<String, Object> requireMap(Object raw, String label) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + "必须为对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<String, Object> optionalMap(Object raw, String label) {
        return raw == null ? Map.of() : requireMap(raw, label);
    }

    private List<Map<String, Object>> mapListValue(
            Object raw,
            String label) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException(label + "必须为数组");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            result.add(requireMap(
                    values.get(index),
                    label + "第 " + (index + 1) + " 项"));
        }
        return List.copyOf(result);
    }

    private void requireAllowedKeys(
            Map<String, Object> value,
            Set<String> allowed,
            String label) {
        for (String key : value.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(
                        label + "包含不支持的配置项: " + key);
            }
        }
    }

    private String requireEnum(
            Object raw,
            Set<String> allowed,
            String label) {
        String value = requireText(raw, 64, label).toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(
                    label + "不支持: " + raw);
        }
        return value;
    }

    private int requirePositiveInteger(Object raw, String label) {
        if (!(raw instanceof Number number)
                || number.doubleValue() != number.intValue()
                || number.intValue() < 1) {
            throw new IllegalArgumentException(label + "必须为正整数");
        }
        return number.intValue();
    }

    private boolean requireBoolean(Object raw, String label) {
        if (!(raw instanceof Boolean value)) {
            throw new IllegalArgumentException(label + "必须为布尔值");
        }
        return value;
    }

    private String requireText(Object raw, int maxLength, String label) {
        if (!(raw instanceof String text) || !StringUtils.hasText(text)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String value = text.trim();
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + "长度不能超过 " + maxLength);
        }
        return value;
    }

    private void putRequiredText(
            Map<String, Object> target,
            String key,
            Object raw,
            int maxLength,
            String label) {
        target.put(key, requireText(raw, maxLength, label));
    }

    private void putOptionalText(
            Map<String, Object> target,
            String key,
            Object raw,
            int maxLength) {
        if (raw == null || raw instanceof String text
                && !StringUtils.hasText(text)) {
            return;
        }
        target.put(key, requireText(raw, maxLength, key));
    }

    private void putRequiredBusinessKey(
            Map<String, Object> target,
            String key,
            Object raw,
            String label) {
        String value = requireText(raw, 100, label);
        requireBusinessKey(value, label);
        target.put(key, value);
    }

    private void putOptionalBusinessKey(
            Map<String, Object> target,
            String key,
            Object raw) {
        if (raw == null || raw instanceof String text
                && !StringUtils.hasText(text)) {
            return;
        }
        String value = requireText(raw, 100, key);
        requireBusinessKey(value, key);
        target.put(key, value);
    }

    private void requireBusinessKey(String value, String label) {
        if (!BUSINESS_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    label + "必须以字母开头，且只能包含字母、数字、点、横线或下划线");
        }
    }

    private void copyOptionalMappingText(
            Map<String, Object> target,
            Map<String, Object> source,
            String key) {
        if (source.get(key) != null) {
            String value = requireText(source.get(key), 160, key);
            if (!BUSINESS_KEY.matcher(value).matches()) {
                throw new IllegalArgumentException(
                        key + "必须是受支持的字段或参数路径");
            }
            target.put(key, value);
        }
    }

    private void requirePresent(
            Map<String, Object> value,
            String key,
            String message) {
        if (!value.containsKey(key)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return "目标内容";
    }

    private boolean hasTextValue(Object value) {
        return value != null && StringUtils.hasText(String.valueOf(value));
    }

    private String optionalText(Object raw, int maxLength, String label) {
        if (raw == null || raw instanceof String text
                && !StringUtils.hasText(text)) {
            return null;
        }
        return requireText(raw, maxLength, label);
    }

    /** 规范化后的校验结果。 */
    public record ValidationResult(
            Map<String, Object> normalizedConfig,
            String summary,
            List<String> warnings) {
    }
}
