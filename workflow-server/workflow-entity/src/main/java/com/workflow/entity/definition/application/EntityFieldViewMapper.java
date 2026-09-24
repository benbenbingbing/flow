package com.workflow.entity.definition.application;

import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;


/** 字段的纯 DTO 映射；不查库、不写库，选项、关系与文件明细由调用方显式补充。 */
final class EntityFieldViewMapper {
    private EntityFieldViewMapper() {}

    /** 保留存储属性的空值，供版本差异和接口映射使用，不能在此应用展示默认值。 */
    static EntityFieldDTO fromField(EntityField field) {
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
        dto.setDictType(field.getDictType());
        dto.setValueStorage(field.getValueStorage());
        dto.setValidateRules(field.getValidateRules());
        dto.setSortOrder(field.getSortOrder());
        dto.setIsSystem(field.getIsSystem());
        dto.setEditable(field.getEditable());
        dto.setIsPublished(field.getIsPublished());
        dto.setFileTypes(field.getFileTypes());
        dto.setFileMaxSize(field.getFileMaxSize());
        dto.setFileMaxCount(field.getFileMaxCount());
        // 实体引用/子表单字段
        dto.setRefEntityId(field.getRefEntityId());
        dto.setRefEntityType(field.getRefEntityType() == null ? null : field.getRefEntityType().name());
        dto.setRefFieldCode(field.getRefFieldCode());
        dto.setRefListKey(field.getRefListKey());
        return dto;
    }

    /** 为管理接口补充系统字段能力和引用类型回退，不影响历史字段快照比较。 */
    static EntityFieldDTO forDefinition(EntityDefinition entity, EntityField field,
                                        SystemEntityFieldPolicy systemEntityFieldPolicy) {
        EntityFieldDTO dto = fromField(field);
        dto.setUiConfigurable(
                systemEntityFieldPolicy.isUiConfigurable(entity, field));
        dto.setRuntimeReadable(
                systemEntityFieldPolicy.isRuntimeReadable(entity, field));
        EntityField.RefEntityType referenceType = field.getRefEntityType() != null
                ? field.getRefEntityType()
                : systemEntityFieldPolicy.referenceType(
                        entity == null ? null : entity.getEntityCode(),
                        field.getFieldCode());
        dto.setRefEntityType(
                referenceType == null ? null : referenceType.name());
        return dto;
    }
}
