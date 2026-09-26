package com.workflow.entity.ui.application.validation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
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

    /**
     * 校验界面配置接口引用；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，作为 {@code codec.write} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 校验值；不满足约束时阻止后续处理。
     *
     * @param value 待校验值的原始输入，结果供调用方继续使用
     * @param path 路径，作为 {@code validateReference} 的输入影响后续处理
     * @param owner 归属方，供本方法校验值时使用
     */
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

    /**
     * 校验引用；不满足约束时阻止后续处理。
     *
     * @param value 待校验引用的原始输入，结果供调用方继续使用
     * @param serviceKey 服务键，后续用于授权校验、关联或幂等去重
     * @param operationKey 操作键，后续用于授权校验、关联或幂等去重
     * @param bindingCode 绑定编码，后续用于校验引用时定位或关联目标
     * @param path 路径，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param owner 归属方，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 按新 extensionId 或历史 serviceId + operationCode 解析迁移后的定义。
     *
     * @param extensionOrServiceId 扩展或服务ID，后续用于解析定义时定位或关联目标
     * @param operationCode 操作编码，后续用于解析定义时定位或关联目标
     * @return 解析后的定义结果，供调用方继续处理
     */
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

    /**
     * 校验绑定契约；不满足约束时阻止后续处理。
     *
     * @param bindingCode 绑定编码，后续用于校验绑定契约时定位或关联目标
     * @param definition 定义，供本方法校验绑定契约时使用
     * @param extensionId 扩展ID，后续用于校验绑定契约时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 判断作用域匹配条件是否成立，供调用方选择后续分支。
     *
     * @param scopeType 作用域类型标识，决定后续作用域匹配采用的处理分支
     * @param scopeId 作用域ID，后续用于处理作用域匹配时定位或关联目标
     * @param owner 归属方，作为 {@code equals} 的输入影响后续处理
     * @return 作用域匹配条件成立时为 true，否则为 false
     */
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

    /**
     * 处理归属方，并将结果传给后续步骤。
     *
     * @param snapshot 快照，作为 {@code normalize} 的输入影响后续处理
     * @return 处理后的归属方结果，供调用方继续处理
     */
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

    /**
     * 将输入映射的键规范为字符串，供后续序列化和字段读取。
     *
     * @param value 待处理字符串映射的原始输入，结果供调用方继续使用
     * @return 字符串映射键值结果，供调用方继续处理
     */
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

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化界面配置接口引用的原始输入，结果供调用方继续使用
     * @return 规范化后的界面配置接口引用文本，供调用方比较或展示
     */
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
