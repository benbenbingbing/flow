package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Locates exact interface-operation bindings in draft and release snapshots.
 */
@Component
@RequiredArgsConstructor
public class UiDataSourceBindingMatcher {

    private final UiEventBindingMapper eventBindingMapper;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    public String findDraftEvent(
            String configType,
            String configId,
            String entityId,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode) {
        List<Map<String, Object>> bindings =
                objectMapper.convertValue(
                        eventBindingMapper.findForSnapshot(
                                configType,
                                configId,
                                entityId),
                        new TypeReference<>() {});
        return findEventBinding(
                bindings,
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.draft.eventBindings");
    }

    public String findPublished(
            String configType,
            Map<String, Object> snapshot,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode) {
        String eventPath = findEventBinding(
                mapList(snapshot.get("eventBindings")),
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.release.eventBindings");
        if (StringUtils.hasText(eventPath)) {
            return eventPath;
        }
        String compositionPath = findViewComposition(
                mapList(snapshot.get("viewCompositions")),
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.release.viewCompositions");
        if (StringUtils.hasText(compositionPath)) {
            return compositionPath;
        }
        if ("FORM".equals(configType)) {
            List<Map<String, Object>> owners = new ArrayList<>();
            addMap(owners, snapshot.get("form"));
            addMaps(owners, snapshot.get("nodes"));
            addMaps(owners, snapshot.get("legacyFields"));
            return findForm(
                    owners,
                    usage,
                    targetType,
                    targetKey,
                    sourceId,
                    operationCode,
                    "$.release.form");
        }
        Map<String, Object> list = stringMap(snapshot.get("list"));
        return findList(
                list,
                mapList(list.get("fields")),
                usage,
                targetType,
                targetKey,
                sourceId,
                operationCode,
                "$.release.list");
    }

    /**
     * 查找关联内容中特殊处理的精确接口操作绑定。
     *
     * <p>关联内容不是表单字段或列表列，必须同时匹配固定 usage、
     * COMPOSITION 目标类型和 compositionKey，避免同一页面其它接口服务
     * 被借用执行。</p>
     */
    private String findViewComposition(
            List<Map<String, Object>> compositions,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String basePath) {
        String normalizedUsage = normalize(usage);
        boolean resolve = UiDataSourceUsages.RELATED_CONTENT_RESOLVE.equals(
                normalizedUsage);
        boolean action = UiDataSourceUsages.RELATED_CONTENT_ACTION.equals(
                normalizedUsage);
        if ((!resolve && !action) || !StringUtils.hasText(targetKey)
                || resolve && !"COMPOSITION".equals(normalize(targetType))
                || action && !"COMPOSITION_ACTION".equals(
                normalize(targetType))) {
            return null;
        }
        String compositionKey = resolve ? targetKey.trim()
                : beforeSeparator(targetKey, "::");
        String actionKey = action ? afterSeparator(targetKey, "::") : null;
        if (!StringUtils.hasText(compositionKey)
                || action && !StringUtils.hasText(actionKey)) {
            return null;
        }
        for (int index = 0; index < compositions.size(); index++) {
            Map<String, Object> composition = compositions.get(index);
            if (!Objects.equals(
                    compositionKey,
                    text(composition.get("compositionKey")))) {
                continue;
            }
            Map<String, Object> config = stringMap(
                    composition.get("config"));
            Map<String, Object> special = stringMap(
                    config.get("specialHandling"));
            if (resolve && matchesBinding(
                    stringMap(special.get("interfaceService")),
                    sourceId, operationCode)) {
                return basePath
                        + "[" + index + "]"
                        + ".config.specialHandling.interfaceService";
            }
            if (action) {
                List<Map<String, Object>> services = mapList(
                        special.get("actionServices"));
                for (int actionIndex = 0;
                        actionIndex < services.size(); actionIndex++) {
                    Map<String, Object> service = services.get(actionIndex);
                    if (Objects.equals(
                            normalize(actionKey),
                            normalize(text(service.get("actionKey"))))
                            && matchesBinding(
                            service, sourceId, operationCode)) {
                        return basePath + "[" + index + "]"
                                + ".config.specialHandling.actionServices["
                                + actionIndex + "]";
                    }
                }
            }
        }
        return null;
    }

    private String beforeSeparator(String value, String separator) {
        int index = value == null ? -1 : value.indexOf(separator);
        return index <= 0 ? null : value.substring(0, index).trim();
    }

    private String afterSeparator(String value, String separator) {
        int index = value == null ? -1 : value.indexOf(separator);
        return index < 0 || index + separator.length() >= value.length()
                ? null : value.substring(index + separator.length()).trim();
    }

    public String findForm(
            List<Map<String, Object>> owners,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String basePath) {
        for (int index = 0; index < owners.size(); index++) {
            Map<String, Object> owner = owners.get(index);
            String ownerPath = basePath + "[" + index + "]";
            if (!formOwnerMatches(
                    owner,
                    targetType,
                    targetKey)) {
                continue;
            }
            String bindingPath =
                    findOwnerBinding(
                            owner,
                            usage,
                            sourceId,
                            operationCode,
                            ownerPath);
            if (StringUtils.hasText(bindingPath)) {
                return bindingPath;
            }
        }
        return null;
    }

    public String findList(
            Map<String, Object> list,
            List<Map<String, Object>> fields,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String basePath) {
        if ("OWNER".equals(normalize(targetType))) {
            if (UiDataSourceUsages.LIST_QUERY.equals(
                    normalize(usage))
                    && sourceId.equals(text(
                            list.get("queryDataSourceId")))
                    && operationCode.equals(text(
                            list.get("queryOperationCode")))) {
                return basePath + ".queryDataSourceId";
            }
            String ownerBinding = findOwnerBinding(
                    list,
                    usage,
                    sourceId,
                    operationCode,
                    basePath);
            if (StringUtils.hasText(ownerBinding)) {
                return ownerBinding;
            }
        }
        for (int index = 0; index < fields.size(); index++) {
            Map<String, Object> field = fields.get(index);
            if (!listFieldMatches(
                    field,
                    targetType,
                    targetKey)) {
                continue;
            }
            if (UiDataSourceUsages.LIST_COLUMN.equals(usage)
                    && sourceId.equals(text(
                            field.get("dataSourceId")))
                    && operationCode.equals(text(
                            field.get("dataSourceOperationCode")))) {
                return basePath
                        + ".fields[" + index + "].dataSourceId";
            }
            String bindingPath = findOwnerBinding(
                    field,
                    usage,
                    sourceId,
                    operationCode,
                    basePath + ".fields[" + index + "]");
            if (StringUtils.hasText(bindingPath)) {
                return bindingPath;
            }
        }
        return null;
    }

    private String findOwnerBinding(
            Map<String, Object> owner,
            String usage,
            String sourceId,
            String operationCode,
            String ownerPath) {
        Map<String, Object> bindings = parseObject(
                owner.get("dataSourceBindings") != null
                        ? owner.get("dataSourceBindings")
                        : owner.get("dataSourceBindingsDocument"),
                "数据源绑定");
        return findConfiguredBinding(
                bindings,
                usage,
                sourceId,
                operationCode,
                ownerPath + ".dataSourceBindings");
    }

    private String findConfiguredBinding(
            Map<String, Object> bindings,
            String usage,
            String sourceId,
            String operationCode,
            String path) {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        Object configured = null;
        String matchedKey = usage;
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            if (usage.equals(normalize(entry.getKey()))) {
                configured = entry.getValue();
                matchedKey = entry.getKey();
                break;
            }
        }
        if (configured == null) {
            return usage.equals(normalize(
                    text(bindings.get("usage"))))
                    && matchesBinding(
                            bindings,
                            sourceId,
                            operationCode)
                    ? path : null;
        }
        return containsOperation(
                configured,
                sourceId,
                operationCode)
                ? path + "." + matchedKey
                : null;
    }

    private String findEventBinding(
            List<Map<String, Object>> bindings,
            String usage,
            String targetType,
            String targetKey,
            String sourceId,
            String operationCode,
            String path) {
        for (int index = 0; index < bindings.size(); index++) {
            Map<String, Object> binding = bindings.get(index);
            if (!usage.equals(normalize(
                    text(binding.get("eventCode"))))) {
                continue;
            }
            if (!eventTargetMatches(
                    binding,
                    targetType,
                    targetKey)) {
                continue;
            }
            Object steps = binding.get("steps");
            if (steps == null) {
                steps = parseArray(
                        binding.get("stepsDocument"),
                        "UI事件绑定步骤");
            }
            if (containsOperation(
                    steps,
                    sourceId,
                    operationCode)) {
                return path + "[" + index + "].steps";
            }
        }
        return null;
    }

    private boolean eventTargetMatches(
            Map<String, Object> binding,
            String targetType,
            String targetKey) {
        return normalize(targetType).equals(normalize(
                text(binding.getOrDefault("targetType", "OWNER"))))
                && normalizedTargetKey(targetType, targetKey).equals(
                        normalizedTargetKey(
                                text(binding.getOrDefault(
                                        "targetType",
                                        "OWNER")),
                                text(binding.get("targetKey"))));
    }

    private boolean formOwnerMatches(
            Map<String, Object> owner,
            String targetType,
            String targetKey) {
        String type = normalize(targetType);
        if ("OWNER".equals(type)) {
            return StringUtils.hasText(text(owner.get("formKey")))
                    && !StringUtils.hasText(text(owner.get("nodeKey")));
        }
        if (!Set.of("FIELD", "NODE").contains(type)) {
            return false;
        }
        Map<String, Object> properties = parseObject(
                owner.get("propsDocument"),
                "表单节点属性");
        String ownerKey = "NODE".equals(type)
                ? firstText(
                        owner.get("nodeKey"),
                        owner.get("fieldCode"),
                        properties.get("fieldCode"))
                : firstText(
                        owner.get("fieldCode"),
                        properties.get("fieldCode"),
                        owner.get("nodeKey"));
        return Objects.equals(
                normalizedTargetKey(type, targetKey),
                normalizedTargetKey(type, ownerKey));
    }

    private boolean listFieldMatches(
            Map<String, Object> field,
            String targetType,
            String targetKey) {
        String type = normalize(targetType);
        return Set.of("COLUMN", "FIELD").contains(type)
                && Objects.equals(
                        normalizedTargetKey(type, targetKey),
                        normalizedTargetKey(
                                type,
                                text(field.get("fieldCode"))));
    }

    private String normalizedTargetKey(
            String targetType,
            String targetKey) {
        return "OWNER".equals(normalize(targetType))
                ? ""
                : StringUtils.hasText(targetKey)
                        ? targetKey.trim()
                        : "";
    }

    private boolean containsOperation(
            Object configured,
            String sourceId,
            String operationCode) {
        if (configured instanceof Map<?, ?> map) {
            return matchesBinding(map, sourceId, operationCode)
                    || map.values().stream()
                            .anyMatch(item ->
                                    containsOperation(
                                            item,
                                            sourceId,
                                            operationCode));
        }
        return configured instanceof List<?> list
                && list.stream().anyMatch(item ->
                        containsOperation(
                                item,
                                sourceId,
                                operationCode));
    }

    private boolean matchesBinding(
            Map<?, ?> binding,
            String sourceId,
            String operationCode) {
        return sourceId.equals(serviceId(binding))
                && operationCode.equals(text(
                        binding.get("operationCode")));
    }

    private String serviceId(Map<?, ?> binding) {
        String value = text(binding.get("serviceId"));
        return StringUtils.hasText(value)
                ? value.trim()
                : null;
    }

    private Map<String, Object> parseObject(
            Object value,
            String label) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        return value instanceof String document
                && StringUtils.hasText(document)
                ? codec.readObject(document, label)
                : Map.of();
    }

    private List<Object> parseArray(
            Object value,
            String label) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return value instanceof String document
                && StringUtils.hasText(document)
                ? codec.readArray(document, label)
                : List.of();
    }

    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        addMaps(result, list);
        return result;
    }

    private void addMap(
            List<Map<String, Object>> target,
            Object value) {
        Map<String, Object> map = stringMap(value);
        if (!map.isEmpty()) {
            target.add(map);
        }
    }

    private void addMaps(
            List<Map<String, Object>> target,
            Object value) {
        if (value instanceof List<?> list) {
            list.forEach(item -> addMap(target, item));
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }
}
