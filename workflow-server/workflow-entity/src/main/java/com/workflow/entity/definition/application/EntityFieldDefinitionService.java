package com.workflow.entity.definition.application;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.EntityFieldFileItemService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 实体字段定义的增量保存与关系同步服务。
 */
@Service
@RequiredArgsConstructor
public class EntityFieldDefinitionService {

    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityFieldFileItemService fileItemService;
    private final EntityFieldOptionService fieldOptionService;
    private final EntityFieldValidationRuleService validationRuleService;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final ObjectMapper objectMapper;

    @Transactional
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.CREATE,
            operation = "新增实体字段",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_FIELD",
            targetIdArg = 0,
            captureArguments = true,
            captureResult = true)
    public EntityFieldDTO createField(
            String entityId,
            EntityFieldDTO dto) {
        EntityDefinition entity = requireDynamicEntity(entityId);
        validateSingleField(entityId, null, dto);
        EntityField saved = createDefinition(entityId, dto);
        entityMapper.updateById(entity);
        return convertToDTOWithRelation(entity, saved);
    }

    @Transactional
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.UPDATE,
            operation = "更新实体字段",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_FIELD",
            targetIdArg = 1,
            captureArguments = true,
            captureResult = true)
    public EntityFieldDTO updateField(
            String entityId,
            String fieldId,
            EntityFieldDTO dto) {
        EntityDefinition entity = requireDynamicEntity(entityId);
        EntityField current = fieldMapper.findByIdString(fieldId);
        if (current == null
                || !Objects.equals(entityId, current.getEntityId())) {
            throw new RuntimeException("实体字段不存在: " + fieldId);
        }
        validateSingleField(entityId, current, dto);
        updateDefinition(current, dto);
        entityMapper.updateById(entity);
        return convertToDTOWithRelation(entity, current);
    }

    public EntityField createDefinition(
            String entityId,
            EntityFieldDTO dto) {
        EntityField field = convertToEntity(dto);
        field.setId(null);
        field.setEntityId(entityId);
        field.setIsSystem(false);
        field.setEditable(true);
        field.setIsPublished(false);
        fieldMapper.insert(field);
        synchronizeFieldOptions(field, dto);
        fileItemService.saveFileItems(field.getId(), dto.getFileItems());
        return field;
    }

    public void updateDefinition(
            EntityField existingField,
            EntityFieldDTO fieldDTO) {
        boolean structureLocked =
                Boolean.TRUE.equals(existingField.getIsSystem())
                        || Boolean.TRUE.equals(existingField.getIsPublished());
        if (!structureLocked) {
            existingField.setFieldCode(fieldDTO.getFieldCode());
            existingField.setFieldType(fieldDTO.getFieldType());
            existingField.setDbType(fieldDTO.getDbType());
            existingField.setDbColumnName(toSnakeCase(fieldDTO.getFieldCode()));
        }
        existingField.setFieldName(fieldDTO.getFieldName());
        existingField.setFieldLength(fieldDTO.getFieldLength());
        existingField.setFieldPrecision(fieldDTO.getFieldPrecision());
        existingField.setIsRequired(fieldDTO.getIsRequired());
        existingField.setIsUnique(fieldDTO.getIsUnique());
        existingField.setDefaultValue(fieldDTO.getDefaultValue());
        existingField.setOptionsJson(fieldDTO.getOptionsJson());
        existingField.setDictType(fieldDTO.getDictType());
        existingField.setValueStorage(resolveValueStorage(fieldDTO));
        existingField.setValidateRules(fieldDTO.getValidateRules());
        existingField.setSortOrder(fieldDTO.getSortOrder());
        existingField.setFileTypes(fieldDTO.getFileTypes());
        existingField.setFileMaxSize(fieldDTO.getFileMaxSize());
        existingField.setFileMaxCount(fieldDTO.getFileMaxCount());
        existingField.setRefEntityId(firstText(
                fieldDTO.getChildEntityId(),
                fieldDTO.getRefEntityId()));
        if (StringUtils.isNotBlank(fieldDTO.getRefEntityType())) {
            existingField.setRefEntityType(
                    EntityField.RefEntityType.valueOf(
                            fieldDTO.getRefEntityType()));
        } else if (isRelationField(fieldDTO)) {
            existingField.setRefEntityType(
                    EntityField.RefEntityType.CUSTOM);
        } else {
            existingField.setRefEntityType(null);
        }
        existingField.setRefFieldCode(firstText(
                fieldDTO.getChildRefFieldCode(),
                fieldDTO.getRefFieldCode()));
        existingField.setRefListKey(fieldDTO.getRefListKey());
        fieldMapper.updateById(existingField);
        synchronizeFieldOptions(existingField, fieldDTO);
        fileItemService.saveFileItems(
                existingField.getId(),
                fieldDTO.getFileItems());
    }

    /**
     * @deprecated 实体关系已独立管理。字段批量保存不再创建、更新或删除关系。
     */
    @Deprecated(forRemoval = false)
    public void syncRelations(
            EntityDefinition parent,
            List<EntityFieldDTO> fieldDtos,
            List<EntityField> savedFields) {
        // Intentionally empty. Kept temporarily for source compatibility with
        // older integrations that still submit entity fields and relations in
        // one request.
    }

    private EntityDefinition requireDynamicEntity(String entityId) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + entityId);
        }
        EntityDefinition.StorageMode storageMode =
                entity.getStorageMode() == null
                        ? EntityDefinition.StorageMode.DYNAMIC
                        : entity.getStorageMode();
        if (storageMode == EntityDefinition.StorageMode.SYSTEM) {
            throw new BusinessConflictException(
                    "ENTITY_SYSTEM_DEFINITION_PROTECTED",
                    "平台系统实体字段由系统目录自动维护，不能单独保存");
        }
        return entity;
    }

    private void validateSingleField(
            String entityId,
            EntityField current,
            EntityFieldDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("字段配置不能为空");
        }
        if (!StringUtils.isNotBlank(dto.getFieldName())) {
            throw new IllegalArgumentException("字段名称不能为空");
        }
        if (!StringUtils.isNotBlank(dto.getFieldCode())) {
            throw new IllegalArgumentException("字段编码不能为空");
        }
        if (dto.getFieldType() == null) {
            throw new IllegalArgumentException("字段类型不能为空");
        }
        dto.setFieldName(dto.getFieldName().trim());
        dto.setFieldCode(dto.getFieldCode().trim());
        validationRuleService.validateAndNormalizeAll(List.of(dto));

        List<EntityField> existingFields = fieldMapper.findByEntityId(entityId);
        if (existingFields != null) {
            for (EntityField field : existingFields) {
                if (current != null
                        && Objects.equals(current.getId(), field.getId())) {
                    continue;
                }
                if (Objects.equals(
                        dto.getFieldCode(),
                        field.getFieldCode())) {
                    throw new RuntimeException(
                            "字段编码 [" + dto.getFieldCode()
                                    + "] 已存在，同一实体内字段编码不能重复，请修改后重试");
                }
            }
        }

        if (current == null) {
            return;
        }
        boolean structureLocked =
                Boolean.TRUE.equals(current.getIsSystem())
                        || Boolean.TRUE.equals(current.getIsPublished());
        if (structureLocked
                && !Objects.equals(
                        current.getFieldCode(),
                        dto.getFieldCode())) {
            throw new BusinessConflictException(
                    "ENTITY_FIELD_CODE_LOCKED",
                    "系统字段或已发布字段不能修改字段编码");
        }
        if (structureLocked
                && current.getFieldType() != dto.getFieldType()) {
            throw new BusinessConflictException(
                    "ENTITY_FIELD_TYPE_LOCKED",
                    "系统字段或已发布字段不能修改字段类型");
        }
    }

    private EntityFieldDTO convertToDTOWithRelation(
            EntityDefinition entity,
            EntityField field) {
        EntityFieldDTO dto = new EntityFieldDTO();
        dto.setId(field.getId());
        dto.setFieldCode(field.getFieldCode());
        dto.setFieldName(field.getFieldName());
        dto.setFieldType(field.getFieldType());
        dto.setDbType(field.getDbType());
        dto.setFieldLength(field.getFieldLength());
        dto.setFieldPrecision(field.getFieldPrecision());
        dto.setDbColumnName(field.getDbColumnName());
        dto.setIsRequired(field.getIsRequired());
        dto.setIsUnique(field.getIsUnique());
        dto.setDefaultValue(field.getDefaultValue());
        dto.setOptionsJson(field.getOptionsJson());
        List<Map<String, Object>> options =
                fieldOptionService.findOptions(field.getId());
        dto.setOptions(options);
        dto.setDictType(field.getDictType());
        dto.setValueStorage(field.getValueStorage());
        dto.setValidateRules(field.getValidateRules());
        dto.setSortOrder(field.getSortOrder());
        dto.setIsSystem(field.getIsSystem());
        dto.setEditable(field.getEditable());
        dto.setIsPublished(field.getIsPublished());
        dto.setUiConfigurable(
                systemEntityFieldPolicy.isUiConfigurable(entity, field));
        dto.setRuntimeReadable(
                systemEntityFieldPolicy.isRuntimeReadable(entity, field));
        dto.setFileTypes(field.getFileTypes());
        dto.setFileMaxSize(field.getFileMaxSize());
        dto.setFileMaxCount(field.getFileMaxCount());
        dto.setRefEntityId(field.getRefEntityId());
        EntityField.RefEntityType referenceType =
                field.getRefEntityType() != null
                        ? field.getRefEntityType()
                        : systemEntityFieldPolicy.referenceType(
                                entity.getEntityCode(),
                                field.getFieldCode());
        dto.setRefEntityType(
                referenceType == null ? null : referenceType.name());
        dto.setRefFieldCode(field.getRefFieldCode());
        dto.setRefListKey(field.getRefListKey());
        if (field.getFieldType() == EntityField.FieldType.FILE
                || field.getFieldType() == EntityField.FieldType.IMAGE) {
            dto.setFileItems(fileItemService.findByFieldId(field.getId()));
        }
        EntityRelation relation = relationMapper.selectByParentField(
                entity.getId(),
                field.getFieldCode());
        if (relation != null) {
            applyRelationMetadata(dto, relation);
        }
        return dto;
    }

    private void applyRelationMetadata(
            EntityFieldDTO field,
            EntityRelation relation) {
        field.setRelationCode(relation.getRelationCode());
        field.setRelationName(relation.getRelationName());
        field.setChildEntityId(relation.getChildEntityId());
        field.setChildEntityCode(relation.getChildEntityCode());
        field.setChildRefFieldCode(relation.getChildRefFieldCode());
        field.setRelationType(
                relation.getRelationType() == null
                        ? null
                        : relation.getRelationType().name());
        field.setCascadeDelete(relation.getCascadeDelete());
        field.setRelationRequired(relation.getRequired());
        field.setRefEntityId(relation.getChildEntityId());
        field.setRefFieldCode(relation.getChildRefFieldCode());
    }

    private void synchronizeFieldOptions(
            EntityField field,
            EntityFieldDTO dto) {
        List<Map<String, Object>> options = dto.getOptions();
        if (options == null
                && StringUtils.isNotBlank(dto.getOptionsJson())) {
            options = fieldOptionService.parseDocument(dto.getOptionsJson());
        }
        if (options == null) {
            return;
        }
        fieldOptionService.replace(field.getId(), options);
        field.setOptionsJson(
                options.isEmpty()
                        ? null
                        : objectMapper.valueToTree(options).toString());
        fieldMapper.updateById(field);
    }

    private EntityField convertToEntity(EntityFieldDTO dto) {
        EntityField field = new EntityField();
        field.setId(dto.getId());
        field.setFieldCode(dto.getFieldCode());
        field.setFieldName(dto.getFieldName());
        field.setFieldType(dto.getFieldType());
        field.setDbType(dto.getDbType());
        field.setFieldLength(dto.getFieldLength());
        field.setFieldPrecision(dto.getFieldPrecision());
        field.setDbColumnName(
                StringUtils.isNotBlank(dto.getDbColumnName())
                        ? dto.getDbColumnName()
                        : toSnakeCase(dto.getFieldCode()));
        field.setIsRequired(dto.getIsRequired());
        field.setIsUnique(dto.getIsUnique());
        field.setDefaultValue(dto.getDefaultValue());
        field.setOptionsJson(dto.getOptionsJson());
        field.setDictType(dto.getDictType());
        field.setValueStorage(resolveValueStorage(dto));
        field.setValidateRules(dto.getValidateRules());
        field.setSortOrder(dto.getSortOrder());
        field.setFileTypes(dto.getFileTypes());
        field.setFileMaxSize(dto.getFileMaxSize());
        field.setFileMaxCount(dto.getFileMaxCount());
        field.setRefEntityId(firstText(
                dto.getChildEntityId(),
                dto.getRefEntityId()));
        if (StringUtils.isNotBlank(dto.getRefEntityType())) {
            field.setRefEntityType(
                    EntityField.RefEntityType.valueOf(
                            dto.getRefEntityType()));
        } else if (isRelationField(dto)) {
            field.setRefEntityType(EntityField.RefEntityType.CUSTOM);
        }
        field.setRefFieldCode(firstText(
                dto.getChildRefFieldCode(),
                dto.getRefFieldCode()));
        field.setRefListKey(dto.getRefListKey());
        return field;
    }

    private boolean isRelationField(EntityFieldDTO dto) {
        return dto != null
                && dto.getFieldType() == EntityField.FieldType.SUB_FORM;
    }

    private String resolveValueStorage(EntityFieldDTO field) {
        if (field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE
                || ((field.getFieldType()
                == EntityField.FieldType.MULTI_SELECT
                || field.getFieldType()
                == EntityField.FieldType.CHECKBOX)
                && StringUtils.isNotBlank(field.getDictType()))) {
            return "MULTI_TABLE";
        }
        return StringUtils.isNotBlank(field.getValueStorage())
                ? field.getValueStorage()
                : "SCALAR";
    }

    private String firstText(String first, String second) {
        if (StringUtils.isNotBlank(first)) {
            return first.trim();
        }
        if (StringUtils.isNotBlank(second)) {
            return second.trim();
        }
        return null;
    }

    private String toSnakeCase(String camelCase) {
        if (camelCase == null) {
            return null;
        }
        return camelCase
                .replaceAll("([a-z])([A-Z]+)", "$1_$2")
                .toLowerCase();
    }
}
