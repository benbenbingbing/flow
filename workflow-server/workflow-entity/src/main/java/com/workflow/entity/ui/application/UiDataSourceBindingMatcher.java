package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Locates legacy data-source bindings and unified event bindings.
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
            String sourceId) {
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
                sourceId,
                "$.draft.eventBindings");
    }

    public String findPublished(
            String configType,
            Map<String, Object> snapshot,
            String usage,
            String sourceId) {
        String eventPath = findEventBinding(
                mapList(snapshot.get("eventBindings")),
                usage,
                sourceId,
                "$.release.eventBindings");
        if (StringUtils.hasText(eventPath)) {
            return eventPath;
        }
        if ("FORM".equals(configType)) {
            List<Map<String, Object>> owners = new ArrayList<>();
            addMap(owners, snapshot.get("form"));
            addMaps(owners, snapshot.get("nodes"));
            addMaps(owners, snapshot.get("legacyFields"));
            return findForm(
                    owners,
                    usage,
                    sourceId,
                    "$.release.form");
        }
        Map<String, Object> list = stringMap(snapshot.get("list"));
        return findList(
                list,
                mapList(list.get("fields")),
                usage,
                sourceId,
                "$.release.list");
    }

    public String findForm(
            List<Map<String, Object>> owners,
            String usage,
            String sourceId,
            String basePath) {
        for (int index = 0; index < owners.size(); index++) {
            Map<String, Object> owner = owners.get(index);
            String ownerPath = basePath + "[" + index + "]";
            String bindingPath =
                    findOwnerBinding(
                            owner,
                            usage,
                            sourceId,
                            ownerPath);
            if (StringUtils.hasText(bindingPath)) {
                return bindingPath;
            }
            Map<String, Object> init = parseObject(
                    owner.get("initConfig"),
                    "表单初始化配置");
            if (!init.isEmpty()) {
                bindingPath = findConfiguredBinding(
                        init,
                        usage,
                        sourceId,
                        ownerPath + ".initConfig");
                if (StringUtils.hasText(bindingPath)) {
                    return bindingPath;
                }
            }
        }
        return null;
    }

    public String findList(
            Map<String, Object> list,
            List<Map<String, Object>> fields,
            String usage,
            String sourceId,
            String basePath) {
        if ("LIST_QUERY".equals(usage)
                && sourceId.equals(text(
                        list.get("queryDataSourceId")))) {
            return basePath + ".queryDataSourceId";
        }
        String ownerBinding = findOwnerBinding(
                list,
                usage,
                sourceId,
                basePath);
        if (StringUtils.hasText(ownerBinding)) {
            return ownerBinding;
        }
        for (int index = 0; index < fields.size(); index++) {
            Map<String, Object> field = fields.get(index);
            if ("LIST_COLUMN".equals(usage)
                    && sourceId.equals(text(
                            field.get("dataSourceId")))) {
                return basePath
                        + ".fields[" + index + "].dataSourceId";
            }
            String bindingPath = findOwnerBinding(
                    field,
                    usage,
                    sourceId,
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
                ownerPath + ".dataSourceBindings");
    }

    private String findConfiguredBinding(
            Map<String, Object> bindings,
            String usage,
            String sourceId,
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
                    && sourceId.equals(sourceId(bindings))
                    ? path : null;
        }
        return containsSource(configured, sourceId)
                ? path + "." + matchedKey
                : null;
    }

    private String findEventBinding(
            List<Map<String, Object>> bindings,
            String usage,
            String sourceId,
            String path) {
        for (int index = 0; index < bindings.size(); index++) {
            Map<String, Object> binding = bindings.get(index);
            if (!usage.equals(normalize(
                    text(binding.get("eventCode"))))) {
                continue;
            }
            Object steps = binding.get("steps");
            if (steps == null) {
                steps = parseArray(
                        binding.get("stepsDocument"),
                        "UI事件绑定步骤");
            }
            if (containsSource(steps, sourceId)) {
                return path + "[" + index + "].steps";
            }
        }
        return null;
    }

    private boolean containsSource(
            Object configured,
            String sourceId) {
        if (configured instanceof String value) {
            return sourceId.equals(value);
        }
        if (configured instanceof Map<?, ?> map) {
            return sourceId.equals(sourceId(map))
                    || map.values().stream()
                            .anyMatch(item ->
                                    containsSource(item, sourceId));
        }
        return configured instanceof List<?> list
                && list.stream().anyMatch(item ->
                        containsSource(item, sourceId));
    }

    private String sourceId(Map<?, ?> binding) {
        for (String key : List.of(
                "serviceId", "sourceId", "id")) {
            String value = text(binding.get(key));
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }
}
