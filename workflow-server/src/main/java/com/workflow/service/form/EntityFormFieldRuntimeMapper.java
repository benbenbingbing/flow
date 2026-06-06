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

    private static Object parseComponentProps(String componentProps, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(componentProps, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
