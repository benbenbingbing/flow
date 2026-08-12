package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态实体写入前的发布字段和流程约束校验。
 */
@Component
@RequiredArgsConstructor
public class EntityDataMutationValidator {

    private final EntityDataDynamicMapper dynamicMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityPublishedSnapshotService snapshotService;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityFieldValidationRuleService fieldValidationRuleService;
    private final ObjectMapper objectMapper;

    public void validateProcessStart(
            boolean requested,
            EntityDefinition definition) {
        if (!requested) {
            return;
        }
        if (definition.getLifecycleMode()
                != EntityDefinition.LifecycleMode.WORKFLOW) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_SUPPORTED",
                    "独立业务实体不支持发起流程");
        }
        if (!StringUtils.hasText(
                definition.getProcessDefinitionId())) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "流程实体尚未绑定流程，不能发起");
        }
    }

    public void validatePublishedFields(
            String entityCode,
            Map<String, Object> storageData,
            String excludeId) {
        EntityPublishedSnapshot snapshot =
                snapshotService.getLatestByEntityCode(
                        entityCode);
        if (snapshot.getFields() == null
                || snapshot.getFields().isEmpty()) {
            return;
        }
        validateRequired(snapshot, storageData);
        validateRules(snapshot, storageData);
        validateUnique(
                entityCode,
                snapshot,
                storageData,
                excludeId);
    }

    private void validateRequired(
            EntityPublishedSnapshot snapshot,
            Map<String, Object> storageData) {
        for (EntityField field : snapshot.getFields()) {
            if (isRelationField(field)) {
                continue;
            }
            String columnName =
                    recordMapper.toColumnName(
                            field.getFieldCode());
            Object value = storageData.get(columnName);
            Object normalizedValue = isAttachmentField(field)
                    ? parseAttachmentValue(value)
                    : value;
            if (Boolean.TRUE.equals(field.getIsRequired())
                    && (isAttachmentField(field)
                            ? !hasAttachmentValue(normalizedValue)
                            : isBlank(normalizedValue))) {
                throw new RuntimeException(
                        "字段必填: "
                                + field.getFieldName());
            }
            validateRequiredAttachmentItems(field, normalizedValue);
        }
    }

    private void validateRequiredAttachmentItems(
            EntityField field,
            Object value) {
        if (!isAttachmentField(field)) {
            return;
        }
        List<EntityFieldFileItem> fileItems =
                field.getFileItems() == null
                        ? List.of()
                        : field.getFileItems();
        List<Integer> requiredIndexes = new ArrayList<>();
        for (int index = 0; index < fileItems.size(); index++) {
            if (Boolean.TRUE.equals(
                    fileItems.get(index).getRequired())) {
                requiredIndexes.add(index);
            }
        }
        if (requiredIndexes.isEmpty()) {
            return;
        }
        if (!(value instanceof Map<?, ?>)) {
            if (requiredIndexes.size() == 1
                    && hasAttachmentValue(value)) {
                return;
            }
            throwMissingAttachmentItem(
                    field,
                    fileItems.get(requiredIndexes.get(0)));
        }
        Map<?, ?> groupedValue = (Map<?, ?>) value;
        for (Integer index : requiredIndexes) {
            EntityFieldFileItem item = fileItems.get(index);
            String key = StringUtils.hasText(item.getItemName())
                    ? item.getItemName()
                    : "附件项" + (index + 1);
            if (!hasAttachmentValue(groupedValue.get(key))) {
                throwMissingAttachmentItem(field, item);
            }
        }
    }

    private Object parseAttachmentValue(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        String normalized = text.trim();
        if (!normalized.startsWith("{")
                && !normalized.startsWith("[")) {
            return value;
        }
        try {
            return objectMapper.readValue(
                    normalized,
                    Object.class);
        } catch (Exception exception) {
            return value;
        }
    }

    private boolean hasAttachmentValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (hasAttachmentValue(item)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            List<String> fileUrlKeys = List.of(
                    "url",
                    "path",
                    "fileUrl");
            if (fileUrlKeys.stream()
                    .anyMatch(map::containsKey)) {
                return fileUrlKeys.stream()
                        .filter(map::containsKey)
                        .map(map::get)
                        .anyMatch(this::hasAttachmentValue);
            }
            if (List.of(
                    "name",
                    "originalName",
                    "size",
                    "type",
                    "uid",
                    "status").stream()
                    .anyMatch(map::containsKey)) {
                return false;
            }
            return map.values().stream()
                    .anyMatch(this::hasAttachmentValue);
        }
        return true;
    }

    private void throwMissingAttachmentItem(
            EntityField field,
            EntityFieldFileItem item) {
        String itemName = StringUtils.hasText(item.getItemName())
                ? item.getItemName()
                : "附件项";
        throw new RuntimeException(
                field.getFieldName()
                        + "缺少必填附件项: "
                        + itemName);
    }

    private void validateUnique(
            String entityCode,
            EntityPublishedSnapshot snapshot,
            Map<String, Object> storageData,
            String excludeId) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        for (EntityField field : snapshot.getFields()) {
            if (!Boolean.TRUE.equals(
                    field.getIsUnique())
                    || isRelationField(field)) {
                continue;
            }
            String columnName =
                    recordMapper.toColumnName(
                            field.getFieldCode());
            Object value = storageData.get(columnName);
            if (isBlank(value)) {
                continue;
            }
            Map<String, Object> condition =
                    new HashMap<>();
            condition.put(columnName, value);
            condition.put(columnName + "_op", "EQ");
            if (StringUtils.hasText(excludeId)) {
                condition.put("id", excludeId);
                condition.put("id_op", "NE");
            }
            if (dynamicMapper.countByCondition(
                    tableName,
                    condition) > 0) {
                throw new RuntimeException(
                        "字段值已存在: "
                                + field.getFieldName());
            }
        }
    }

    private void validateRules(
            EntityPublishedSnapshot snapshot,
            Map<String, Object> storageData) {
        for (EntityField field : snapshot.getFields()) {
            if (isRelationField(field)) {
                continue;
            }
            String columnName =
                    recordMapper.toColumnName(
                            field.getFieldCode());
            fieldValidationRuleService.validateValue(
                    field,
                    storageData.get(columnName));
        }
    }

    public boolean isRelationField(EntityField field) {
        return field.getFieldType()
                == EntityField.FieldType.SUB_FORM
                || field.getFieldType()
                == EntityField.FieldType.SUB_LIST;
    }

    private boolean isAttachmentField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.FILE
                || field.getFieldType() == EntityField.FieldType.IMAGE;
    }

    private boolean isBlank(Object value) {
        return value == null
                || value instanceof String text
                && text.isBlank();
    }
}
