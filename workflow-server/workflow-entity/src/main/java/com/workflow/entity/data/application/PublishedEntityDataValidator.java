package com.workflow.entity.data.application;

import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Validation and normalization that must use one immutable published snapshot.
 */
final class PublishedEntityDataValidator {

    private PublishedEntityDataValidator() {
    }

    static void sanitizeAndValidate(
            EntityPublishedSnapshot snapshot,
            Map<String, Object> storageData,
            EntityRuntimeRecordMapper recordMapper,
            RichTextSanitizer richTextSanitizer) {
        if (snapshot.getFields() == null || snapshot.getFields().isEmpty()) {
            return;
        }
        for (EntityField field : snapshot.getFields()) {
            if (isRelationField(field) || !StringUtils.hasText(field.getFieldCode())) {
                continue;
            }
            String columnName = recordMapper.toColumnName(field.getFieldCode());
            Object value = storageData.get(columnName);
            if (field.getFieldType() == EntityField.FieldType.RICH_TEXT
                    && value instanceof String html) {
                value = richTextSanitizer.sanitize(html);
                storageData.put(columnName, value);
            }
            if (Boolean.TRUE.equals(field.getIsRequired()) && isBlank(value)) {
                throw new RuntimeException("字段必填: " + field.getFieldName());
            }
        }
    }

    private static boolean isRelationField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.SUB_FORM
                || field.getFieldType() == EntityField.FieldType.SUB_LIST;
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String string && string.isBlank());
    }
}
