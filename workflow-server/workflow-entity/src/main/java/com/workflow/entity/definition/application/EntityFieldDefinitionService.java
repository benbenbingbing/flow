package com.workflow.entity.definition.application;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
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

    /**
     * 创建字段；结果供后续流程传递或持久化。
     *
     * @param entityId 实体ID，后续用于创建字段时定位或关联目标
     * @param dto DTO，作为 {@code requireDataFieldType} 的输入影响后续处理
     * @return 创建后的字段结果，供调用方继续处理
     */
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
        requireDataFieldType(dto);
        validateSingleField(entityId, null, dto);
        EntityField saved = createDefinition(entityId, dto);
        entityMapper.touchUpdateTime(entityId);
        return convertToDTOWithRelation(entity, saved);
    }

    /**
     * 更新字段；后续读取或执行将使用更新后的状态。
     *
     * @param entityId 实体ID，后续用于更新字段时定位或关联目标
     * @param fieldId 字段ID，后续用于更新字段时定位或关联目标
     * @param dto DTO，作为 {@code validateSingleField} 的输入影响后续处理
     * @return 更新后的字段结果，供调用方继续处理
     */
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
        entityMapper.touchUpdateTime(entityId);
        return convertToDTOWithRelation(entity, current);
    }

    /**
     * 创建定义；结果供后续流程传递或持久化。
     *
     * @param entityId 实体ID，后续用于创建定义时定位或关联目标
     * @param dto DTO，作为 {@code requireDataFieldType} 的输入影响后续处理
     * @return 创建后的定义结果，供调用方继续处理
     */
    public EntityField createDefinition(
            String entityId,
            EntityFieldDTO dto) {
        requireDataFieldType(dto);
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

    /**
     * 更新定义；后续读取或执行将使用更新后的状态。
     *
     * @param existingField 已有字段，作为 {@code assertPublishedStructureUnchanged} 的输入影响后续处理
     * @param fieldDTO 字段DTO，作为 {@code assertPublishedStructureUnchanged} 的输入影响后续处理
     */
    public void updateDefinition(
            EntityField existingField,
            EntityFieldDTO fieldDTO) {
        if (existingField.getFieldType() != fieldDTO.getFieldType()) requireDataFieldType(fieldDTO);
        assertPublishedStructureUnchanged(existingField, fieldDTO);
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
     *
     * @param parent 父级，供本方法处理同步关系集合时使用
     * @param fieldDtos 字段{@code dtos}，供本方法处理同步关系集合时使用
     * @param savedFields {@code saved}字段，供本方法处理同步关系集合时使用
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

    /**
     * 校验并获取动态实体；不满足约束时阻止后续处理。
     *
     * @param entityId 实体ID，后续用于校验并获取动态实体时定位或关联目标
     * @return 校验并获取后的动态实体结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private EntityDefinition requireDynamicEntity(String entityId) {
        // 字段增删改与实体发布统一持有实体独占锁，防止发布快照与字段写入交错。
        EntityDefinition entity = entityMapper.findByIdForUpdate(entityId)
                .orElseThrow(() -> new RuntimeException(
                        "实体不存在: " + entityId));
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

    /**
     * 校验{@code single}字段；不满足约束时阻止后续处理。
     *
     * @param entityId 实体ID，后续用于校验{@code single}字段时定位或关联目标
     * @param current 当前，供本方法校验{@code single}字段时使用
     * @param dto DTO，作为 {@code dto.setFieldName} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
    }

    /**
     * 所有字段更新入口共用的已发布结构约束。
     *
     * <p>必须放在 {@link #updateDefinition(EntityField, EntityFieldDTO)}
     * 内部，避免实体批量保存绕过单字段接口的校验。</p>
     *
     * @param current 当前，作为 {@code Boolean.TRUE.equals} 的输入影响后续处理
     * @param dto DTO，供本方法处理{@code assert}已发布{@code structure}{@code unchanged}时使用
     */
    private void assertPublishedStructureUnchanged(
            EntityField current,
            EntityFieldDTO dto) {
        boolean locked = Boolean.TRUE.equals(current.getIsSystem())
                || Boolean.TRUE.equals(current.getIsPublished());
        if (locked
                && !Objects.equals(
                        current.getFieldCode(),
                        dto.getFieldCode())) {
            throw new BusinessConflictException(
                    "ENTITY_FIELD_CODE_LOCKED",
                    "系统字段或已发布字段不能修改字段编码");
        }
        if (locked
                && current.getFieldType() != dto.getFieldType()) {
            throw new BusinessConflictException(
                    "ENTITY_FIELD_TYPE_LOCKED",
                    "系统字段或已发布字段不能修改字段类型");
        }
        if (locked
                && isReferenceTargetField(current.getFieldType())
                && (!Objects.equals(
                current.getRefEntityId(), requestedRefEntityId(dto))
                || !Objects.equals(
                current.getRefEntityType(), requestedRefEntityType(dto))
                || !Objects.equals(
                current.getRefFieldCode(), requestedRefFieldCode(dto)))) {
            // 已发布流程会把实体字段坐标固化到人员解析器配置。允许字段
            // 在原地切换目标实体会让既有部署读取另一类记录，必须新增字段
            // 和新流程版本完成演进，而不能破坏已发布语义。
            throw new BusinessConflictException(
                    "ENTITY_FIELD_REFERENCE_LOCKED",
                    "系统字段或已发布用户/关系字段不能修改关联实体及关联字段");
        }
    }

    /**
     * 判断是否引用目标字段；判断结果决定调用方的后续分支。
     *
     * @param fieldType 字段类型标识，决定后续引用目标字段采用的处理分支
     * @return 引用目标字段条件成立时为 true，否则为 false
     */
    private boolean isReferenceTargetField(
            EntityField.FieldType fieldType) {
        return fieldType == EntityField.FieldType.USER
                || fieldType == EntityField.FieldType.REFERENCE
                || fieldType == EntityField.FieldType.MULTI_REFERENCE;
    }

    /**
     * 生成请求引用实体ID文本，供后续匹配或展示。
     *
     * @param dto DTO，作为 {@code firstText} 的输入影响后续处理
     * @return 处理后的请求引用实体ID文本，供调用方比较或展示
     */
    private String requestedRefEntityId(EntityFieldDTO dto) {
        return firstText(dto.getChildEntityId(), dto.getRefEntityId());
    }

    /**
     * 处理请求引用实体类型，并将结果传给后续步骤。
     *
     * @param dto DTO，作为 {@code EntityField.RefEntityType.valueOf} 的输入影响后续处理
     * @return 处理后的请求引用实体类型结果，供调用方继续处理
     */
    private EntityField.RefEntityType requestedRefEntityType(
            EntityFieldDTO dto) {
        if (StringUtils.isNotBlank(dto.getRefEntityType())) {
            return EntityField.RefEntityType.valueOf(
                    dto.getRefEntityType());
        }
        return isRelationField(dto)
                ? EntityField.RefEntityType.CUSTOM : null;
    }

    /**
     * 生成请求引用字段编码文本，供后续匹配或展示。
     *
     * @param dto DTO，作为 {@code firstText} 的输入影响后续处理
     * @return 处理后的请求引用字段编码文本，供调用方比较或展示
     */
    private String requestedRefFieldCode(EntityFieldDTO dto) {
        return firstText(
                dto.getChildRefFieldCode(), dto.getRefFieldCode());
    }

    /**
     * 转换截止DTO关系；输出作为后续校验或处理的输入。
     *
     * @param entity 实体，作为 {@code dto.setUiConfigurable} 的输入影响后续处理
     * @param field 字段，作为 {@code dto.setId} 的输入影响后续处理
     * @return 转换后的截止DTO关系结果，供调用方继续处理
     */
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

    /**
     * 应用关系元数据，并将结果传给后续步骤。
     *
     * @param field 字段，供本方法应用关系元数据时使用
     * @param relation 关系，作为 {@code field.setRelationCode} 的输入影响后续处理
     */
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

    /**
     * 处理{@code synchronize}字段选项，并将结果传给后续步骤。
     *
     * @param field 字段，作为 {@code fieldOptionService.replace} 的输入影响后续处理
     * @param dto DTO，作为 {@code fieldOptionService.parseDocument} 的输入影响后续处理
     */
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

    /**
     * 转换截止实体；输出作为后续校验或处理的输入。
     *
     * @param dto DTO，作为 {@code field.setId} 的输入影响后续处理
     * @return 转换后的截止实体结果，供调用方继续处理
     */
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

    /**
     * 判断是否关系字段；判断结果决定调用方的后续分支。
     *
     * @param dto DTO，供本方法判断是否关系字段时使用
     * @return 关系字段条件成立时为 true，否则为 false
     */
    private boolean isRelationField(EntityFieldDTO dto) {
        return dto != null
                && dto.getFieldType() == EntityField.FieldType.SUB_FORM;
    }

    /**
     * 子表单、关联列表属于页面组件，不能再通过实体字段写入创建展示或关系配置。
     *
     * @param dto DTO，供本方法校验并获取数据字段类型时使用
     */
    static void requireDataFieldType(EntityFieldDTO dto) {
        if (dto != null && (dto.getFieldType() == EntityField.FieldType.SUB_FORM
                || dto.getFieldType() == EntityField.FieldType.SUB_LIST)) {
            throw new IllegalArgumentException("子表单和子列表已改为页面组件，请先配置实体关系，再在表单设计中添加");
        }
    }

    /**
     * 解析值存储；输出作为后续校验或处理的输入。
     *
     * @param field 字段，作为 {@code StringUtils.isNotBlank} 的输入影响后续处理
     * @return 解析后的值存储文本，供调用方比较或展示
     */
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

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param first 首个，供本方法处理首个文本时使用
     * @param second {@code second}，供本方法处理首个文本时使用
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(String first, String second) {
        if (StringUtils.isNotBlank(first)) {
            return first.trim();
        }
        if (StringUtils.isNotBlank(second)) {
            return second.trim();
        }
        return null;
    }

    /**
     * 转换为{@code snake}分支；输出作为后续校验或处理的输入。
     *
     * @param camelCase {@code camel}分支，供本方法转换为{@code snake}分支时使用
     * @return 转换为后的{@code snake}分支文本，供调用方比较或展示
     */
    private String toSnakeCase(String camelCase) {
        if (camelCase == null) {
            return null;
        }
        return camelCase
                .replaceAll("([a-z])([A-Z]+)", "$1_$2")
                .toLowerCase();
    }
}
