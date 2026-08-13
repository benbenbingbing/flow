package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.SystemEntityService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.contracts.ui.UiDataSourceUsages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 单选实体字段事件的权威选择数据加载器。
 */
@Service
@RequiredArgsConstructor
public class EntitySelectionRuntimeService {

    private static final Set<String> SYSTEM_TYPES =
            Set.of("USER", "DEPT", "ROLE", "GROUP");

    private final EntityDataDynamicService entityDataService;
    private final SystemEntityService systemEntityService;
    private final EntityDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper;

    public Object resolve(
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain) {
        if (request == null
                || !UiDataSourceUsages.ENTITY_SELECTED.equals(normalize(
                        request.getEventCode()))
                || !"FORM".equals(normalize(request.getConfigType()))
                || !"FIELD".equals(normalize(request.getTargetType()))
                || chain == null
                || chain.steps().isEmpty()) {
            return request == null ? null : request.getSelection();
        }
        ReferenceConfig reference = referenceConfig(
                chain.snapshot(),
                request.getTargetKey());
        if (reference == null || reference.multiple()) {
            return request.getSelection();
        }
        String selectedId = selectedId(request.getSelection());
        if (!StringUtils.hasText(selectedId)) {
            return null;
        }
        Map<String, Object> authoritative;
        if ("CUSTOM".equals(reference.entityType())) {
            String entityCode = reference.entityCode();
            if (!StringUtils.hasText(entityCode)
                    && StringUtils.hasText(reference.entityId())) {
                EntityDefinition definition =
                        definitionMapper.selectById(reference.entityId());
                entityCode = definition == null
                        ? null : definition.getEntityCode();
            }
            if (!StringUtils.hasText(entityCode)) {
                throw new IllegalArgumentException(
                        "单选实体字段未配置有效的关联实体");
            }
            EntityDataDTO detail =
                    entityDataService.findAccessibleById(
                            entityCode,
                            selectedId,
                            reference.listKey());
            authoritative = objectMapper.convertValue(
                    detail,
                    new TypeReference<Map<String, Object>>() {});
            authoritative.put("entityType", "CUSTOM");
        } else if (SYSTEM_TYPES.contains(reference.entityType())) {
            Map<String, Object> detail =
                    systemEntityService.selectById(
                            reference.entityType(),
                            selectedId);
            if (detail == null) {
                throw new IllegalArgumentException(
                        "选择的系统实体数据不存在或已失效");
            }
            authoritative = new LinkedHashMap<>(detail);
        } else {
            throw new IllegalArgumentException(
                    "不支持的引用实体类型: "
                            + reference.entityType());
        }
        if (request.getSelection() instanceof Map<?, ?> clientSelection
                && clientSelection.containsKey("selectionData")) {
            authoritative.put(
                    "selectionData",
                    clientSelection.get("selectionData"));
        }
        return authoritative;
    }

    private ReferenceConfig referenceConfig(
            Map<String, Object> snapshot,
            String targetKey) {
        if (snapshot == null || !StringUtils.hasText(targetKey)) {
            return null;
        }
        for (Map<String, Object> node :
                mapList(snapshot.get("nodes"))) {
            Map<String, Object> props = objectMap(
                    node.get("propsDocument"));
            String fieldCode = firstText(
                    props.get("fieldCode"),
                    node.get("nodeKey"));
            if (targetKey.equals(fieldCode)) {
                return fromField(props);
            }
        }
        for (Map<String, Object> field :
                mapList(snapshot.get("legacyFields"))) {
            if (targetKey.equals(text(field.get("fieldCode")))) {
                return fromField(field);
            }
        }
        return null;
    }

    private ReferenceConfig fromField(
            Map<String, Object> field) {
        Map<String, Object> componentProps =
                objectMap(field.get("componentProps"));
        Map<String, Object> refConfig =
                objectMap(componentProps.get("refConfig"));
        String fieldType = normalize(firstText(
                field.get("fieldType"),
                field.get("componentType")));
        String componentType =
                normalize(text(field.get("componentType")));
        boolean multiple =
                "MULTI_REFERENCE".equals(fieldType)
                        || "MULTI_REFERENCE".equals(componentType);
        boolean reference = multiple
                || Set.of(
                        "REFERENCE", "USER", "DEPT",
                        "ROLE", "GROUP")
                .contains(fieldType)
                || "REFERENCE".equals(componentType)
                || !refConfig.isEmpty()
                || field.containsKey("refEntityId")
                || field.containsKey("refEntityType");
        if (!reference) {
            return null;
        }
        String entityType = normalize(firstText(
                refConfig.get("refEntityType"),
                field.get("refEntityType"),
                SYSTEM_TYPES.contains(fieldType)
                        ? fieldType : "CUSTOM"));
        return new ReferenceConfig(
                entityType,
                firstText(
                        refConfig.get("refEntityId"),
                        field.get("refEntityId")),
                firstText(
                        refConfig.get("entityCode"),
                        field.get("refEntityCode")),
                firstText(
                        refConfig.get("listKey"),
                        field.get("refListKey")),
                multiple);
    }

    private String selectedId(Object selection) {
        if (selection instanceof Map<?, ?> map) {
            return firstText(
                    map.get("id"),
                    map.get("value"));
        }
        if (selection instanceof List<?>) {
            return null;
        }
        return text(selection);
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> stringMap((Map<?, ?>) item))
                .toList();
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        if (value instanceof String document
                && StringUtils.hasText(document)) {
            try {
                return objectMapper.readValue(
                        document,
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private record ReferenceConfig(
            String entityType,
            String entityId,
            String entityCode,
            String listKey,
            boolean multiple) {
    }
}
