package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 校验发布快照中的接口扩展、上下文和作用域引用。
 */
@Component
@RequiredArgsConstructor
public class UiConfigInterfaceReferenceValidator {

    /** 统一扩展定义查询入口。 */
    private final UiExtensionDefinitionMapper dataSourceMapper;
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

    /**
     * 校验可编辑列表草稿中的直接接口引用。
     *
     * <p>mutable DTO 只接受 extensionId，不解析历史 serviceId + operationCode；
     * 旧 pair 仅由不可变发布快照和导入适配器处理。</p>
     *
     * @param listId 列表 ID；新建列表可为空，此时 LIST 作用域接口不会匹配
     * @param entityId 列表所属实体 ID
     * @param queryExtensionId 列表查询接口扩展 ID
     * @param fields 列表字段草稿
     */
    public void validateListDraft(
            String listId,
            String entityId,
            String queryExtensionId,
            List<EntityListField> fields) {
        Owner owner = new Owner("LIST", listId, entityId);
        if (StringUtils.hasText(queryExtensionId)) {
            validateReference(
                    Map.of("queryInterfaceExtensionId", queryExtensionId),
                    "queryInterfaceExtensionId",
                    null,
                    UiDataSourceUsages.LIST_QUERY,
                    "$.list",
                    owner);
        }
        List<EntityListField> safeFields = fields == null
                ? List.of() : fields;
        for (int index = 0; index < safeFields.size(); index++) {
            EntityListField field = safeFields.get(index);
            if (field == null || !StringUtils.hasText(
                    field.getInterfaceExtensionId())) {
                continue;
            }
            validateReference(
                    Map.of("interfaceExtensionId",
                            field.getInterfaceExtensionId()),
                    "interfaceExtensionId",
                    null,
                    UiDataSourceUsages.LIST_COLUMN,
                    "$.fields[" + index + "]",
                    owner);
        }
    }

    private void validateValue(
            Object value,
            String path,
            Owner owner) {
        if (value instanceof Map<?, ?> map) {
            validateReference(
                    map,
                    "extensionId",
                    null,
                    null,
                    path,
                    owner);
            validateReference(
                    map,
                    "interfaceExtensionId",
                    null,
                    UiDataSourceUsages.LIST_COLUMN,
                    path,
                    owner);
            validateReference(
                    map,
                    "queryInterfaceExtensionId",
                    null,
                    UiDataSourceUsages.LIST_QUERY,
                    path,
                    owner);
            // 历史发布快照不可改写 hash，继续只读识别旧 pair。
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
        String operationCode = operationKey == null
                ? null : text(value.get(operationKey));
        if (operationKey != null && !StringUtils.hasText(operationCode)) {
            throw new IllegalArgumentException(
                    "发布配置接口绑定缺少 "
                            + operationKey
                            + ": "
                            + path);
        }
        UiExtensionDefinition definition = resolveDefinition(
                serviceId, operationCode);
        if (definition == null
                || !"INTERFACE".equals(normalize(
                        definition.getExtensionType()))
                || !Boolean.TRUE.equals(
                        definition.getEnabled())
                || Integer.valueOf(1).equals(
                        definition.getDeleted())) {
            throw new IllegalArgumentException(
                    "发布配置引用的接口扩展不存在或未启用: "
                            + path
                            + "."
                            + serviceKey
                            + "="
                            + serviceId);
        }
        if (owner == null) {
            throw new IllegalArgumentException(
                    "发布配置缺少接口绑定所有者身份: "
                            + path);
        }
        String contextType = normalize(
                definition.getInterfaceContextType());
        if (!owner.type().equals(contextType)) {
            throw new IllegalArgumentException(
                    "接口操作上下文 "
                            + contextType
                            + " 与发布类型 "
                            + owner.type()
                            + " 不一致: "
                            + serviceId);
        }
        validateBindingContract(
                bindingCode,
                definition,
                serviceId);
        if (!scopeMatches(
                normalize(definition.getScopeType()),
                definition.getScopeId(),
                owner)) {
            throw new IllegalArgumentException(
                    "接口扩展作用域与发布对象不一致: "
                            + serviceId);
        }
    }

    /** 按新 extensionId 或历史 serviceId + operationCode 解析迁移后的定义。 */
    private UiExtensionDefinition resolveDefinition(
            String extensionOrServiceId,
            String operationCode) {
        UiExtensionDefinition definition =
                dataSourceMapper.selectById(extensionOrServiceId);
        if (definition != null || !StringUtils.hasText(operationCode)) {
            return definition;
        }
        return dataSourceMapper.selectOne(
                new LambdaQueryWrapper<UiExtensionDefinition>()
                        .eq(UiExtensionDefinition::getExtensionType,
                                "INTERFACE")
                        .eq(UiExtensionDefinition::getLegacyServiceId,
                                extensionOrServiceId)
                        .eq(UiExtensionDefinition::getProviderOperationCode,
                                operationCode)
                        .eq(UiExtensionDefinition::getDeleted, 0));
    }

    private void validateBindingContract(
            String bindingCode,
            UiExtensionDefinition definition,
            String extensionId) {
        if (!UiDataSourceUsages.LIST_QUERY.equals(bindingCode)
                && !UiDataSourceUsages.LIST_COLUMN.equals(bindingCode)) {
            return;
        }
        if (!"READ".equals(normalize(
                definition.getInterfaceKind()))) {
            throw new IllegalArgumentException(
                    "列表查询和列接口必须为 READ: " + extensionId);
        }
        Map<String, Object> outputSchema = StringUtils.hasText(
                definition.getOutputSchemaDocument())
                ? codec.readObject(
                        definition.getOutputSchemaDocument(),
                        "接口输出Schema")
                : Map.of();
        if (UiDataSourceUsages.LIST_COLUMN.equals(bindingCode)) {
            if (!"OBJECT".equals(normalize(text(
                    outputSchema.get("type"))))) {
                throw new IllegalArgumentException(
                        "列表列接口输出 Schema 必须为 object: "
                                + extensionId);
            }
            return;
        }
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
                            + extensionId);
        }
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
