package com.workflow.service.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityFormField;

import java.util.HashMap;
import java.util.Map;

/**
 * 表单字段运行时映射工具类
 * 
 * 将持久化的 {@link EntityFormField} 转换为前端运行时所需的扁平 Map 结构，
 * 处理组件类型回退、只读覆盖、JSON 配置解析以及关联关系对象组装等逻辑。
 * 不可实例化，仅提供静态方法。
 */
public final class EntityFormFieldRuntimeMapper {

    private EntityFormFieldRuntimeMapper() {
    }

    /**
     * 将表单字段转换为前端运行时所需的 Map。
     *
     * @param field             表单字段实体
     * @param readonlyOverride  只读覆盖标记，为 true 时强制只读
     * @param objectMapper      用于解析 validationRules/extensionConfig/componentProps 等 JSON 配置
     * @return 包含字段运行时信息的 Map
     */
    public static Map<String, Object> toMap(EntityFormField field, Boolean readonlyOverride, ObjectMapper objectMapper) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", field.getId());
        result.put("fieldId", field.getFieldId());
        result.put("fieldCode", field.getFieldCode() != null ? field.getFieldCode() : field.getFieldId());
        result.put("fieldName", field.getFieldName());
        result.put("fieldLabel", field.getFieldLabel());
        result.put("fieldType", field.getFieldType());
        result.put("componentType", resolveComponentType(field));
        result.put("isRequired", field.getIsRequired());
        result.put("isReadonly", Boolean.TRUE.equals(readonlyOverride) ? 1 : field.getIsReadonly());
        result.put("isHidden", field.getIsHidden());
        result.put("defaultValue", field.getDefaultValue());
        result.put("placeholder", field.getPlaceholder());
        result.put("sortOrder", field.getSortOrder());
        result.put("gridSpan", field.getGridSpan());
        if (field.getValidationRules() != null) {
            result.put("validationRules", parseJsonObject(field.getValidationRules(), objectMapper));
        }
        if (field.getExtensionConfig() != null) {
            result.put("extensionConfig", parseJsonObject(field.getExtensionConfig(), objectMapper));
        }

        if (field.getOptionsJson() != null) {
            result.put("optionsJson", field.getOptionsJson());
        }
        if (field.getRefEntityId() != null) {
            result.put("refEntityId", field.getRefEntityId());
        }
        if (field.getRefEntityType() != null) {
            result.put("refEntityType", field.getRefEntityType());
        }
        if (field.getDisplayMode() != null) {
            result.put("displayMode", field.getDisplayMode());
        }
        if (field.getRefFieldCode() != null) {
            result.put("refFieldCode", field.getRefFieldCode());
        }
        if (field.getRelationCode() != null) {
            result.put("relationCode", field.getRelationCode());
        }
        if (field.getRelationName() != null) {
            result.put("relationName", field.getRelationName());
        }
        if (field.getChildEntityId() != null) {
            result.put("childEntityId", field.getChildEntityId());
        }
        if (field.getChildEntityCode() != null) {
            result.put("childEntityCode", field.getChildEntityCode());
        }
        if (field.getChildRefFieldCode() != null) {
            result.put("childRefFieldCode", field.getChildRefFieldCode());
        }
        if (field.getRelationType() != null) {
            result.put("relationType", field.getRelationType());
        }
        if (field.getCascadeDelete() != null) {
            result.put("cascadeDelete", field.getCascadeDelete());
        }
        if (field.getRelationRequired() != null) {
            result.put("relationRequired", field.getRelationRequired());
        }
        putRelationObject(result, field);
        if (field.getComponentProps() != null) {
            result.put("componentProps", parseComponentProps(field.getComponentProps(), objectMapper));
        }

        return result;
    }

    /** 解析组件类型：优先 componentType，回退到 fieldType 的小写形式 */
    private static String resolveComponentType(EntityFormField field) {
        if (field.getComponentType() != null && !field.getComponentType().isEmpty()) {
            return field.getComponentType();
        }
        if (field.getFieldType() != null && !field.getFieldType().isEmpty()) {
            return field.getFieldType().toLowerCase();
        }
        return null;
    }

    /** 当存在子实体配置时，组装关联关系对象并放入 result.relation */
    private static void putRelationObject(Map<String, Object> result, EntityFormField field) {
        if (field.getChildEntityId() == null && field.getChildEntityCode() == null && field.getChildRefFieldCode() == null) {
            return;
        }
        Map<String, Object> relation = new HashMap<>();
        relation.put("code", field.getRelationCode());
        relation.put("name", field.getRelationName());
        relation.put("childEntityId", field.getChildEntityId());
        relation.put("childEntityCode", field.getChildEntityCode());
        relation.put("childRefFieldCode", field.getChildRefFieldCode());
        relation.put("type", field.getRelationType());
        relation.put("cascadeDelete", field.getCascadeDelete());
        relation.put("required", field.getRelationRequired());
        result.put("relation", relation);
    }

    /** 解析组件属性 JSON */
    private static Object parseComponentProps(String componentProps, ObjectMapper objectMapper) {
        return parseJsonObject(componentProps, objectMapper);
    }

    /** 解析 JSON 字符串为 Map，解析失败返回空 Map */
    private static Object parseJsonObject(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
