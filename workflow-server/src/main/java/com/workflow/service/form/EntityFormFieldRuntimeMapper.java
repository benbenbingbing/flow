package com.workflow.service.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityFormField;

import java.util.HashMap;
import java.util.Map;

public final class EntityFormFieldRuntimeMapper {

    private EntityFormFieldRuntimeMapper() {
    }

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
        result.put("isReadonly", readonlyOverride != null ? readonlyOverride : field.getIsReadonly());
        result.put("isHidden", field.getIsHidden());
        result.put("defaultValue", field.getDefaultValue());
        result.put("placeholder", field.getPlaceholder());
        result.put("sortOrder", field.getSortOrder());
        result.put("gridSpan", field.getGridSpan());

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

    private static String resolveComponentType(EntityFormField field) {
        if (field.getComponentType() != null && !field.getComponentType().isEmpty()) {
            return field.getComponentType();
        }
        if (field.getFieldType() != null && !field.getFieldType().isEmpty()) {
            return field.getFieldType().toLowerCase();
        }
        return null;
    }

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

    private static Object parseComponentProps(String componentProps, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(componentProps, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
