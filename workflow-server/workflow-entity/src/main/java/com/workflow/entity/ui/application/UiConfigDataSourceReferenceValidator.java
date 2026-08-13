package com.workflow.entity.ui.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 校验发布快照中的接口服务、操作、上下文和作用域引用。
 */
@Component
@RequiredArgsConstructor
public class UiConfigDataSourceReferenceValidator {

    /** 接口服务定义查询入口。 */
    private final UiDataSourceDefinitionMapper dataSourceMapper;
    /** 发布快照和操作文档 JSON 编解码器。 */
    private final JsonDocumentCodec codec;

    public void validate(Map<String, Object> snapshot) {
        String document = codec.write(
                snapshot,
                "待发布UI配置");
        if (document.contains("\"sourceType\":\"SQL\"")
                || document.contains("\"sourceType\":\"SCRIPT\"")
                || document.contains("\"sourceType\":\"URL\"")
                || document.contains("\"sql\":")
                || document.contains("\"script\":")
                || document.contains("\"url\":")) {
            throw new IllegalArgumentException(
                    "发布配置禁止包含任意 SQL、脚本或外网 URL 数据源");
        }
        validateValue(snapshot, "$", owner(snapshot));
    }

    private void validateValue(
            Object value,
            String path,
            Owner owner) {
        if (value instanceof Map<?, ?> map) {
            validateReference(
                    map,
                    "serviceId",
                    "operationCode",
                    null,
                    path,
                    owner);
            validateReference(
                    map,
                    "dataSourceId",
                    "dataSourceOperationCode",
                    UiDataSourceUsages.LIST_COLUMN,
                    path,
                    owner);
            validateReference(
                    map,
                    "queryDataSourceId",
                    "queryOperationCode",
                    UiDataSourceUsages.LIST_QUERY,
                    path,
                    owner);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                validateValue(
                        entry.getValue(),
                        path + "." + entry.getKey(),
                        owner);
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0;
                    index < list.size();
                    index++) {
                validateValue(
                        list.get(index),
                        path + "[" + index + "]",
                        owner);
            }
        }
    }

    private void validateReference(
            Map<?, ?> value,
            String serviceKey,
            String operationKey,
            String bindingCode,
            String path,
            Owner owner) {
        String serviceId = text(value.get(serviceKey));
        if (!StringUtils.hasText(serviceId)) {
            return;
        }
        String operationCode = text(value.get(operationKey));
        if (!StringUtils.hasText(operationCode)) {
            throw new IllegalArgumentException(
                    "发布配置接口绑定缺少 "
                            + operationKey
                            + ": "
                            + path);
        }
        var definition = dataSourceMapper.selectById(serviceId);
        if (definition == null
                || !Boolean.TRUE.equals(
                        definition.getEnabled())
                || Integer.valueOf(1).equals(
                        definition.getDeleted())) {
            throw new IllegalArgumentException(
                    "发布配置引用的接口服务不存在或未启用: "
                            + path
                            + "."
                            + serviceKey
                            + "="
                            + serviceId);
        }
        Map<String, Object> operation = operation(
                definition.getOperationsDocument(),
                operationCode,
                serviceId);
        if (owner == null) {
            throw new IllegalArgumentException(
                    "发布配置缺少接口绑定所有者身份: "
                            + path);
        }
        String contextType = normalize(text(
                operation.get("contextType")));
        if (!owner.type().equals(contextType)) {
            throw new IllegalArgumentException(
                    "接口操作上下文 "
                            + contextType
                            + " 与发布类型 "
                            + owner.type()
                            + " 不一致: "
                            + serviceId
                            + "/"
                            + operationCode);
        }
        validateBindingContract(
                bindingCode,
                operation,
                serviceId,
                operationCode);
        if (!scopeMatches(
                normalize(definition.getScopeType()),
                definition.getScopeId(),
                owner)) {
            throw new IllegalArgumentException(
                    "接口服务作用域与发布对象不一致: "
                            + serviceId
                            + "/"
                            + operationCode);
        }
    }

    private void validateBindingContract(
            String bindingCode,
            Map<String, Object> operation,
            String serviceId,
            String operationCode) {
        if (!UiDataSourceUsages.LIST_QUERY.equals(
                bindingCode)) {
            return;
        }
        if (!"READ".equals(normalize(text(
                operation.get("kind"))))) {
            throw new IllegalArgumentException(
                    "列表查询接口操作必须为 READ: "
                            + serviceId
                            + "/"
                            + operationCode);
        }
        Map<String, Object> outputSchema =
                stringMap(operation.get("outputSchema"));
        Map<String, Object> properties =
                stringMap(outputSchema.get("properties"));
        Map<String, Object> records =
                stringMap(properties.get("records"));
        if (!"OBJECT".equals(normalize(text(
                outputSchema.get("type"))))
                || !"ARRAY".equals(normalize(text(
                        records.get("type"))))) {
            throw new IllegalArgumentException(
                    "列表查询接口输出 Schema 必须包含 records 数组: "
                            + serviceId
                            + "/"
                            + operationCode);
        }
    }

    private Map<String, Object> operation(
            String document,
            String operationCode,
            String serviceId) {
        if (!StringUtils.hasText(document)) {
            throw new IllegalArgumentException(
                    "接口服务未配置操作: " + serviceId);
        }
        return codec.readArray(document, "接口服务操作定义")
                .stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::stringMap)
                .filter(item -> Objects.equals(
                        operationCode,
                        text(item.get("code"))))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "接口服务操作不存在: "
                                        + serviceId
                                        + "/"
                                        + operationCode));
    }

    private boolean scopeMatches(
            String scopeType,
            String scopeId,
            Owner owner) {
        return switch (scopeType) {
            case "GLOBAL" -> true;
            case "ENTITY" -> Objects.equals(
                    scopeId,
                    owner.entityId());
            case "FORM" -> "FORM".equals(owner.type())
                    && Objects.equals(scopeId, owner.id());
            case "LIST" -> "LIST".equals(owner.type())
                    && Objects.equals(scopeId, owner.id());
            default -> false;
        };
    }

    private Owner owner(Map<String, Object> snapshot) {
        String type = normalize(text(snapshot.get("configType")));
        Map<String, Object> config = stringMap(
                snapshot.get(type.toLowerCase(Locale.ROOT)));
        if (!List.of("FORM", "LIST", "ENTITY").contains(type)
                || config.isEmpty()) {
            return null;
        }
        String id = text(config.get("id"));
        String entityId = "ENTITY".equals(type)
                ? id
                : text(config.get("entityId"));
        return StringUtils.hasText(id)
                && StringUtils.hasText(entityId)
                ? new Owner(type, id, entityId)
                : null;
    }

    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Object> result =
                new java.util.LinkedHashMap<>();
        map.forEach((key, child) ->
                result.put(String.valueOf(key), child));
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    /**
     * 发布快照解析出的可信所有者。
     *
     * @param type 所有者类型：FORM、LIST 或 ENTITY
     * @param id 所有者对象 ID
     * @param entityId 所有者所属实体 ID
     */
    private record Owner(
            String type,
            String id,
            String entityId) {
    }
}
