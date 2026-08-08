package com.workflow.entity.data.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityFieldValidationRuleService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
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
            if (!Boolean.TRUE.equals(
                    field.getIsRequired())
                    || isRelationField(field)) {
                continue;
            }
            String columnName =
                    recordMapper.toColumnName(
                            field.getFieldCode());
            if (isBlank(storageData.get(columnName))) {
                throw new RuntimeException(
                        "字段必填: "
                                + field.getFieldName());
            }
        }
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

    private boolean isBlank(Object value) {
        return value == null
                || value instanceof String text
                && text.isBlank();
    }
}
