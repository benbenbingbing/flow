package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityRelation;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.service.config.EntityFormConfigurationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 实体表单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFormService {

    private enum SaveMode {
        USER_CAS,
        SYSTEM_IMPORT
    }
    
    private final EntityFormMapper formMapper;
    private final EntityFormFieldMapper formFieldMapper;
    private final EntityFormNodeMapper formNodeMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityFormConfigurationValidator configurationValidator;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final JsonDocumentCodec jsonDocumentCodec;
    
    /**
     * 查询所有表单列表
     */
    public List<EntityForm> list() {
        List<EntityForm> forms = formMapper.selectList(null);
        forms.forEach(this::fillFormDetails);
        return forms;
    }
    
    /**
     * 查询实体的表单列表
     */
    public List<EntityForm> getFormsByEntityId(String entityId) {
        List<EntityForm> forms = formMapper.selectByEntityId(entityId);
        forms.forEach(this::fillFormDetails);
        return forms;
    }
    
    /**
     * 根据ID查询表单
     */
    public EntityForm getById(String id) {
        EntityForm form = formMapper.selectById(id);
        if (form != null) {
            fillFormDetails(form);
            List<EntityFormField> fields = getFormFields(id);
            // 补充 fieldCode（从 entity_field 查询）
            for (EntityFormField field : fields) {
                if (field.getFieldId() != null) {
                    com.workflow.entity.EntityField entityField = fieldMapper.findByIdString(field.getFieldId());
                    enrichFormField(field, entityField);
                }
            }
            form.setFields(fields);
            form.setNodes(formNodeMapper.findByFormId(id));
        }
        return form;
    }

    /**
     * 兼容既有迁移模块的系统导入入口。
     *
     * 普通 HTTP 更新必须调用带 expectedRevision 的重载。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm saveForm(EntityForm form) {
        return saveFormInternal(form, null, SaveMode.SYSTEM_IMPORT);
    }

    /**
     * 普通整包表单保存，已有配置必须携带 expectedRevision。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm saveForm(EntityForm form, Integer expectedRevision) {
        return saveFormInternal(form, expectedRevision, SaveMode.USER_CAS);
    }

    /**
     * 显式系统导入入口；系统导入会锁定当前草稿后按当前版本覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm saveFormForImport(EntityForm form) {
        return saveFormInternal(form, null, SaveMode.SYSTEM_IMPORT);
    }

    private EntityForm saveFormInternal(
            EntityForm source,
            Integer expectedRevision,
            SaveMode saveMode) {
        if (source == null) {
            throw new IllegalArgumentException("表单配置不能为空");
        }
        EntityForm current = null;
        EntityForm desired = new EntityForm();
        boolean isNew = !StringUtils.hasText(source.getId());
        if (isNew) {
            desired.setId(saveMode == SaveMode.SYSTEM_IMPORT
                    ? source.getId()
                    : null);
            desired.setEntityId(source.getEntityId());
            desired.setFormKey(source.getFormKey());
            desired.setRevision(1);
            desired.setCreateTime(LocalDateTime.now());
        } else {
            current = formMapper.selectByIdForUpdate(source.getId());
            if (current == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            if (saveMode == SaveMode.USER_CAS) {
                requireExpectedRevision(
                        expectedRevision,
                        current,
                        "表单已被其他人修改");
            }
            BeanUtils.copyProperties(current, desired);
        }

        applyMutableFormProperties(source, desired, isNew, saveMode);
        desired.setFields(source.getFields());
        desired.setNodes(null);
        applyFormDefaults(desired);
        entityAccessPolicy.requireDynamicById(desired.getEntityId());
        configurationValidator.validateForm(desired);
        validateFormKey(desired);

        LocalDateTime now = LocalDateTime.now();
        desired.setUpdateTime(now);
        if (isNew) {
            formMapper.insert(desired);
            log.info("新增实体表单：{}", desired.getFormName());
        } else {
            int currentRevision = revisionOf(current);
            desired.setRevision(currentRevision + 1);
            desired.setDraftHash(null);
            UpdateWrapper<EntityForm> wrapper = formRevisionCondition(
                    desired.getId(), current);
            setMutableFormColumns(wrapper, desired);
            wrapper.set("revision", desired.getRevision())
                    .set("draft_hash", null)
                    .set("update_time", now);
            if (formMapper.update(null, wrapper) != 1) {
                throw formConflict(
                        desired.getId(),
                        "表单已被其他人修改，请刷新后重试");
            }
            log.info("更新实体表单：{}", desired.getFormName());
        }

        if (Boolean.TRUE.equals(desired.getIsDefault())) {
            clearOtherDefaultForm(desired.getEntityId(), desired.getId());
        }
        if (source.getFields() != null) {
            synchronizeFormFieldsByDiff(
                    desired.getId(),
                    source.getFields(),
                    saveMode);
        }
        return getById(desired.getId());
    }
    
    /**
     * 将同一实体下的其他表单设为非默认
     */
    private void clearOtherDefaultForm(String entityId, String currentFormId) {
        List<EntityForm> forms = formMapper.selectByEntityId(entityId);
        for (EntityForm form : forms) {
            if (!form.getId().equals(currentFormId) && Boolean.TRUE.equals(form.getIsDefault())) {
                UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", form.getId())
                        .eq("deleted", 0)
                        .eq("is_default", true)
                        .set("is_default", false)
                        .setSql("revision = revision + 1")
                        .set("draft_hash", null)
                        .set("update_time", LocalDateTime.now());
                formMapper.update(null, wrapper);
                log.info("取消表单默认状态：{}", form.getFormName());
            }
        }
    }
    
    /**
     * 获取实体的默认表单
     */
    public EntityForm getDefaultForm(String entityId) {
        EntityForm form = formMapper.selectDefaultByEntityId(entityId);
        if (form != null) {
            fillFormDetails(form);
            List<EntityFormField> fields = getFormFields(form.getId());
            // 补充 fieldCode（从 entity_field 查询）
            for (EntityFormField field : fields) {
                if (field.getFieldId() != null) {
                    com.workflow.entity.EntityField entityField = fieldMapper.findByIdString(field.getFieldId());
                    enrichFormField(field, entityField);
                }
            }
            form.setFields(fields);
            form.setNodes(formNodeMapper.findByFormId(form.getId()));
        }
        return form;
    }

    /**
     * 删除表单
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteForm(String id) {
        EntityForm form = formMapper.selectById(id);
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }
        
        // 删除字段
        formFieldMapper.deleteByFormId(id);
        
        // 逻辑删除表单
        formMapper.deleteById(id);
        log.info("删除实体表单：{}", form.getFormName());
    }
    
    /**
     * 仅更新表单初始化配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateInitConfig(String id, Map<String, Object> initConfig) {
        EntityForm form = formMapper.selectById(id);
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }
        String document = initConfig == null || initConfig.isEmpty()
                ? null
                : jsonDocumentCodec.write(
                        jsonDocumentCodec.ensureSchemaVersion(initConfig, 1),
                        "表单初始化配置");
        UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
               .set("init_config", document)
               .set("update_time", LocalDateTime.now());
        formMapper.update(null, wrapper);
        log.info("更新表单初始化配置：{}", form.getFormName());
    }
    
    /**
     * 普通字段整包保存，父表单 revision 作为聚合 CAS。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFormFields(
            String formId,
            List<EntityFormField> fields,
            Integer expectedRevision) {
        EntityForm current = lockForm(formId);
        requireExpectedRevision(
                expectedRevision,
                current,
                "表单已被其他人修改");
        configurationValidator.validateFields(fields);
        touchFormWithRevision(current);
        synchronizeFormFieldsByDiff(formId, fields, SaveMode.USER_CAS);
    }

    /**
     * 禁止旧调用在没有 expectedRevision 时覆盖整包字段。
     */
    @Deprecated
    public void saveFormFields(String formId, List<EntityFormField> fields) {
        throw new IllegalArgumentException("expectedRevision 不能为空");
    }

    /**
     * 系统导入入口：整包保存字段，锁定父表单后按当前版本覆盖。
     *
     * @param formId 表单ID
     * @param fields 字段列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFormFieldsForImport(
            String formId,
            List<EntityFormField> fields) {
        EntityForm current = lockForm(formId);
        configurationValidator.validateFields(fields);
        touchFormWithRevision(current);
        synchronizeFormFieldsByDiff(formId, fields, SaveMode.SYSTEM_IMPORT);
    }

    private void synchronizeFormFieldsByDiff(
            String formId,
            List<EntityFormField> fields,
            SaveMode saveMode) {
        List<EntityFormField> existing = formFieldMapper.selectByFormId(formId);
        Map<String, EntityFormField> existingById = new HashMap<>();
        Map<String, EntityFormField> existingByCode = new HashMap<>();
        existing.forEach(field -> {
            existingById.put(field.getId(), field);
            if (StringUtils.hasText(field.getFieldCode())) {
                existingByCode.put(field.getFieldCode(), field);
            }
        });
        Set<String> retainedIds = new HashSet<>();

        for (int i = 0; i < (fields == null ? 0 : fields.size()); i++) {
            EntityFormField source = fields.get(i);
            if (!StringUtils.hasText(source.getFieldCode())
                    && source.getFieldId() != null) {
                com.workflow.entity.EntityField entityField =
                        fieldMapper.findByIdString(source.getFieldId());
                if (entityField != null && StringUtils.hasText(entityField.getFieldCode())) {
                    source.setFieldCode(entityField.getFieldCode());
                }
            }
            EntityFormField current = StringUtils.hasText(source.getId())
                    ? existingById.get(source.getId())
                    : null;
            if (current == null
                    && (!StringUtils.hasText(source.getId())
                    || saveMode == SaveMode.SYSTEM_IMPORT)) {
                current = existingByCode.get(source.getFieldCode());
            }
            if (current == null
                    && saveMode == SaveMode.USER_CAS
                    && StringUtils.hasText(source.getId())
                    && existingByCode.containsKey(source.getFieldCode())) {
                throw formConflict(
                        formId,
                        "表单字段标识已变化，请刷新后重试");
            }
            if (current == null) {
                EntityFormField created = new EntityFormField();
                copyMutableFormFieldProperties(source, created);
                created.setId(source.getId());
                created.setFormId(formId);
                created.setSortOrder(i);
                created.setCreateTime(LocalDateTime.now());
                created.setUpdateTime(LocalDateTime.now());
                EntityFormField sameId = StringUtils.hasText(created.getId())
                        ? formFieldMapper.selectById(created.getId())
                        : null;
                if (sameId != null) {
                    throw formConflict(
                            formId,
                            "表单字段 ID 已被其他配置占用，请刷新后重试");
                }
                formFieldMapper.insert(created);
                source.setId(created.getId());
                retainedIds.add(created.getId());
            } else {
                retainedIds.add(current.getId());
                EntityFormField updated = new EntityFormField();
                copyMutableFormFieldProperties(source, updated);
                updated.setId(current.getId());
                updated.setFormId(formId);
                updated.setSortOrder(i);
                updated.setCreateTime(current.getCreateTime());
                updated.setUpdateTime(LocalDateTime.now());
                if (!sameFormField(updated, current)) {
                    UpdateWrapper<EntityFormField> wrapper =
                            formFieldSnapshotCondition(formId, current);
                    setMutableFormFieldColumns(wrapper, updated);
                    wrapper.set("sort_order", updated.getSortOrder())
                            .set("update_time", updated.getUpdateTime());
                    if (formFieldMapper.update(null, wrapper) != 1) {
                        throw formConflict(
                                formId,
                                "表单字段已被其他人修改，请刷新后重试");
                    }
                }
                source.setId(current.getId());
            }
        }
        for (EntityFormField current : existing) {
            if (!retainedIds.contains(current.getId())) {
                UpdateWrapper<EntityFormField> wrapper =
                        formFieldSnapshotCondition(formId, current);
                if (formFieldMapper.delete(wrapper) != 1) {
                    throw formConflict(
                            formId,
                            "表单字段已被其他人修改，请刷新后重试");
                }
            }
        }
    }

    private void applyMutableFormProperties(
            EntityForm source,
            EntityForm target,
            boolean isNew,
            SaveMode saveMode) {
        target.setFormName(source.getFormName());
        target.setDescription(source.getDescription());
        target.setLayoutType(source.getLayoutType());
        target.setIsDefault(source.getIsDefault());
        target.setStatus(source.getStatus());
        target.setCustomComponent(source.getCustomComponent());
        target.setCustomComponentVersion(source.getCustomComponentVersion());
        target.setCustomComponentSnapshotVersion(
                source.getCustomComponentSnapshotVersion());
        target.setInitConfig(source.getInitConfig());
        if (isNew
                || saveMode == SaveMode.SYSTEM_IMPORT
                || source.getDataSourceBindingsDocument() != null) {
            target.setDataSourceBindingsDocument(
                    source.getDataSourceBindingsDocument());
        }
        target.setViewConfig(source.getViewConfig());
    }

    private void applyFormDefaults(EntityForm form) {
        if (!StringUtils.hasText(form.getLayoutType())) {
            form.setLayoutType("vertical");
        }
        if (form.getStatus() == null) {
            form.setStatus(1);
        }
        if (form.getIsDefault() == null) {
            form.setIsDefault(false);
        }
    }

    private void validateFormKey(EntityForm form) {
        if (!StringUtils.hasText(form.getFormKey())) {
            return;
        }
        String excludeId = StringUtils.hasText(form.getId())
                ? form.getId()
                : "";
        if (formMapper.existsFormKey(
                form.getEntityId(),
                form.getFormKey(),
                excludeId)) {
            throw new IllegalArgumentException(
                    "表单标识已存在：" + form.getFormKey());
        }
    }

    private void setMutableFormColumns(
            UpdateWrapper<EntityForm> wrapper,
            EntityForm form) {
        wrapper.set("form_name", form.getFormName())
                .set("description", form.getDescription())
                .set("layout_type", form.getLayoutType())
                .set("is_default", form.getIsDefault())
                .set("status", form.getStatus())
                .set("custom_component", form.getCustomComponent())
                .set("custom_component_version",
                        form.getCustomComponentVersion())
                .set("custom_component_snapshot_version",
                        form.getCustomComponentSnapshotVersion())
                .set("init_config", form.getInitConfig())
                .set("data_source_bindings_document",
                        form.getDataSourceBindingsDocument())
                .set("view_config", form.getViewConfig());
    }

    private UpdateWrapper<EntityForm> formRevisionCondition(
            String formId,
            EntityForm current) {
        UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", formId).eq("deleted", 0);
        if (current.getRevision() == null) {
            wrapper.isNull("revision");
        } else {
            wrapper.eq("revision", current.getRevision());
        }
        return wrapper;
    }

    private EntityForm lockForm(String formId) {
        EntityForm current = formMapper.selectByIdForUpdate(formId);
        if (current == null) {
            throw new IllegalArgumentException("表单不存在");
        }
        return current;
    }

    private void touchFormWithRevision(EntityForm current) {
        UpdateWrapper<EntityForm> wrapper =
                formRevisionCondition(current.getId(), current);
        wrapper.set("revision", revisionOf(current) + 1)
                .set("draft_hash", null)
                .set("update_time", LocalDateTime.now());
        if (formMapper.update(null, wrapper) != 1) {
            throw formConflict(
                    current.getId(),
                    "表单已被其他人修改，请刷新后重试");
        }
    }

    private void requireExpectedRevision(
            Integer expectedRevision,
            EntityForm current,
            String message) {
        if (expectedRevision == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        if (!expectedRevision.equals(revisionOf(current))) {
            throw new RevisionConflictException(
                    message,
                    getById(current.getId()));
        }
    }

    private int revisionOf(EntityForm form) {
        return form.getRevision() == null ? 0 : form.getRevision();
    }

    private RevisionConflictException formConflict(
            String formId,
            String message) {
        return new RevisionConflictException(message, getById(formId));
    }

    private void copyMutableFormFieldProperties(
            EntityFormField source,
            EntityFormField target) {
        target.setFieldId(source.getFieldId());
        target.setFieldCode(source.getFieldCode());
        target.setFieldName(source.getFieldName());
        target.setFieldLabel(source.getFieldLabel());
        target.setFieldType(source.getFieldType());
        target.setComponentType(source.getComponentType());
        target.setIsRequired(source.getIsRequired());
        target.setIsReadonly(source.getIsReadonly());
        target.setIsHidden(source.getIsHidden());
        target.setDefaultValue(source.getDefaultValue());
        target.setPlaceholder(source.getPlaceholder());
        target.setValidationRules(source.getValidationRules());
        target.setExtensionConfig(source.getExtensionConfig());
        target.setComponentProps(source.getComponentProps());
        target.setGridSpan(source.getGridSpan());
    }

    private UpdateWrapper<EntityFormField> formFieldSnapshotCondition(
            String formId,
            EntityFormField current) {
        UpdateWrapper<EntityFormField> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", current.getId())
                .eq("form_id", formId);
        if (current.getUpdateTime() == null) {
            wrapper.isNull("update_time");
        } else {
            wrapper.eq("update_time", current.getUpdateTime());
        }
        return wrapper;
    }

    private void setMutableFormFieldColumns(
            UpdateWrapper<EntityFormField> wrapper,
            EntityFormField field) {
        wrapper.set("field_id", field.getFieldId())
                .set("field_code", field.getFieldCode())
                .set("field_name", field.getFieldName())
                .set("field_label", field.getFieldLabel())
                .set("field_type", field.getFieldType())
                .set("component_type", field.getComponentType())
                .set("is_required", field.getIsRequired())
                .set("is_readonly", field.getIsReadonly())
                .set("is_hidden", field.getIsHidden())
                .set("default_value", field.getDefaultValue())
                .set("placeholder", field.getPlaceholder())
                .set("validation_rules", field.getValidationRules())
                .set("extension_config", field.getExtensionConfig())
                .set("component_props", field.getComponentProps())
                .set("grid_span", field.getGridSpan());
    }

    private boolean sameFormField(
            EntityFormField left,
            EntityFormField right) {
        return Objects.equals(left.getFieldId(), right.getFieldId())
                && Objects.equals(left.getFieldCode(), right.getFieldCode())
                && Objects.equals(left.getFieldName(), right.getFieldName())
                && Objects.equals(left.getFieldLabel(), right.getFieldLabel())
                && Objects.equals(left.getFieldType(), right.getFieldType())
                && Objects.equals(
                        left.getComponentType(),
                        right.getComponentType())
                && Objects.equals(left.getIsRequired(), right.getIsRequired())
                && Objects.equals(left.getIsReadonly(), right.getIsReadonly())
                && Objects.equals(left.getIsHidden(), right.getIsHidden())
                && Objects.equals(left.getDefaultValue(), right.getDefaultValue())
                && Objects.equals(left.getPlaceholder(), right.getPlaceholder())
                && Objects.equals(
                        left.getValidationRules(),
                        right.getValidationRules())
                && Objects.equals(
                        left.getExtensionConfig(),
                        right.getExtensionConfig())
                && Objects.equals(
                        left.getComponentProps(),
                        right.getComponentProps())
                && Objects.equals(left.getGridSpan(), right.getGridSpan())
                && Objects.equals(left.getSortOrder(), right.getSortOrder());
    }
    
    /**
     * 获取表单字段
     */
    public List<EntityFormField> getFormFields(String formId) {
        List<EntityFormField> fields = formFieldMapper.selectByFormId(formId);
        for (EntityFormField field : fields) {
            if (field.getFieldId() != null) {
                com.workflow.entity.EntityField entityField = fieldMapper.findByIdString(field.getFieldId());
                enrichFormField(field, entityField);
            }
        }
        return fields;
    }
    
    /**
     * 根据实体ID和表单Key查询表单
     */
    public EntityForm getByEntityIdAndFormKey(String entityId, String formKey) {
        EntityForm form = formMapper.selectByEntityIdAndFormKey(entityId, formKey);
        if (form != null) {
            fillFormDetails(form);
            List<EntityFormField> fields = getFormFields(form.getId());
            // 补充 fieldCode（从 entity_field 查询）
            for (EntityFormField field : fields) {
                if (field.getFieldId() != null) {
                    com.workflow.entity.EntityField entityField = fieldMapper.findByIdString(field.getFieldId());
                    enrichFormField(field, entityField);
                }
            }
            form.setFields(fields);
            form.setNodes(formNodeMapper.findByFormId(form.getId()));
        }
        return form;
    }

    /**
     * 根据实体编码获取实体定义
     */
    public com.workflow.entity.EntityDefinition getEntityByCode(String entityCode) {
        return entityMapper.findByEntityCode(entityCode).orElse(null);
    }
    
    /**
     * 获取实体的字段列表（用于创建表单时选择）
     */
    public List<EntityField> getEntityFields(String entityId) {
        return fieldMapper.findByEntityId(entityId);
    }

    /**
     * 补充表单字段的元数据（从 entity_field 查询）
     */
    private void enrichFormField(EntityFormField field, EntityField entityField) {
        if (entityField == null) {
            return;
        }
        // 优先使用数据库已持久化的 fieldCode，避免关联实体字段变更后回退到 fieldId
        if (!StringUtils.hasText(field.getFieldCode())) {
            field.setFieldCode(entityField.getFieldCode());
        }
        if (entityField.getFieldType() != null) {
            field.setFieldType(entityField.getFieldType().name());
        }
        if (entityField.getRefEntityId() != null) {
            field.setRefEntityId(entityField.getRefEntityId());
        }
        if (entityField.getRefEntityType() != null) {
            field.setRefEntityType(entityField.getRefEntityType().name());
        }
        if (entityField.getDisplayMode() != null) {
            field.setDisplayMode(entityField.getDisplayMode());
        }
        if (entityField.getRefFieldCode() != null) {
            field.setRefFieldCode(entityField.getRefFieldCode());
        }
        enrichRelationMetadata(field, entityField);
        // 系统可编辑字段强制非只读（避免表单设计器误设为只读导致无法交互）
        if (Boolean.TRUE.equals(entityField.getIsSystem()) && Boolean.TRUE.equals(entityField.getEditable())) {
            field.setIsReadonly(0);
        }
    }

    private void enrichRelationMetadata(EntityFormField field, EntityField entityField) {
        if (entityField.getEntityId() == null || entityField.getFieldCode() == null) {
            return;
        }
        EntityRelation relation = relationMapper.selectByParentField(entityField.getEntityId(), entityField.getFieldCode());
        if (relation == null) {
            return;
        }
        field.setRelationCode(relation.getRelationCode());
        field.setRelationName(relation.getRelationName());
        field.setChildEntityId(relation.getChildEntityId());
        field.setChildEntityCode(relation.getChildEntityCode());
        field.setChildRefFieldCode(relation.getChildRefFieldCode());
        field.setRelationType(relation.getRelationType() != null ? relation.getRelationType().name() : null);
        field.setCascadeDelete(relation.getCascadeDelete());
        field.setRelationRequired(relation.getRequired());
        field.setRefEntityId(relation.getChildEntityId());
        field.setRefFieldCode(relation.getChildRefFieldCode());
    }

    /**
     * 填充表单详情
     */
    private void fillFormDetails(EntityForm form) {
        if (form.getEntityId() != null) {
            EntityDefinition entity = entityMapper.selectById(form.getEntityId());
            form.setEntity(entity);
        }
    }
    
    /**
     * 设置默认表单
     * @param formId 表单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultForm(String formId) {
        EntityForm form = formMapper.selectById(formId);
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }
        
        // 设置为默认表单
        form.setIsDefault(true);
        form.setUpdateTime(LocalDateTime.now());
        formMapper.updateById(form);
        
        // 将同一实体下的其他表单设为非默认
        clearOtherDefaultForm(form.getEntityId(), formId);
        
        log.info("设置默认表单：{} (entityId={})", form.getFormName(), form.getEntityId());
    }
    
    /**
     * 复制表单
     * @param sourceFormId 源表单ID
     * @return 新表单
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm copyForm(String sourceFormId) {
        // 查询源表单
        EntityForm sourceForm = formMapper.selectById(sourceFormId);
        if (sourceForm == null) {
            throw new RuntimeException("表单不存在");
        }
        
        // 查询源表单字段
        List<EntityFormField> sourceFields = formFieldMapper.selectByFormId(sourceFormId);
        
        // 创建新表单
        EntityForm newForm = new EntityForm();
        newForm.setEntityId(sourceForm.getEntityId());
        newForm.setFormName(sourceForm.getFormName() + " copy");
        newForm.setFormKey(sourceForm.getFormKey() + "_copy_" + System.currentTimeMillis());
        newForm.setDescription(sourceForm.getDescription());
        newForm.setLayoutType(sourceForm.getLayoutType());
        newForm.setCustomComponent(sourceForm.getCustomComponent());
        newForm.setCustomComponentVersion(
                sourceForm.getCustomComponentVersion());
        newForm.setCustomComponentSnapshotVersion(
                sourceForm.getCustomComponentSnapshotVersion());
        newForm.setInitConfig(sourceForm.getInitConfig());
        newForm.setViewConfig(sourceForm.getViewConfig());
        newForm.setStatus(1);
        newForm.setRevision(1);
        newForm.setCreateTime(LocalDateTime.now());
        newForm.setUpdateTime(LocalDateTime.now());
        
        // 保存新表单
        formMapper.insert(newForm);
        
        // 复制字段
        if (sourceFields != null && !sourceFields.isEmpty()) {
            for (int i = 0; i < sourceFields.size(); i++) {
                EntityFormField sourceField = sourceFields.get(i);
                EntityFormField newField = new EntityFormField();
                newField.setFormId(newForm.getId());
                newField.setFieldId(sourceField.getFieldId());
                newField.setFieldCode(sourceField.getFieldCode());
                newField.setFieldName(sourceField.getFieldName());
                newField.setFieldLabel(sourceField.getFieldLabel());
                newField.setFieldType(sourceField.getFieldType());
                newField.setComponentType(sourceField.getComponentType());
                newField.setIsRequired(sourceField.getIsRequired());
                newField.setIsReadonly(sourceField.getIsReadonly());
                newField.setIsHidden(sourceField.getIsHidden());
                newField.setDefaultValue(sourceField.getDefaultValue());
                newField.setPlaceholder(sourceField.getPlaceholder());
                newField.setComponentProps(sourceField.getComponentProps());
                newField.setValidationRules(sourceField.getValidationRules());
                newField.setExtensionConfig(sourceField.getExtensionConfig());
                newField.setGridSpan(sourceField.getGridSpan());
                newField.setSortOrder(i);
                newField.setCreateTime(LocalDateTime.now());
                newField.setUpdateTime(LocalDateTime.now());
                
                formFieldMapper.insert(newField);
            }
        }

        List<com.workflow.entity.EntityFormNode> sourceNodes =
                formNodeMapper.findByFormId(sourceFormId);
        Map<String, String> copiedIds = new HashMap<>();
        for (com.workflow.entity.EntityFormNode sourceNode : sourceNodes) {
            copiedIds.put(sourceNode.getId(),
                    java.util.UUID.randomUUID().toString().replace("-", ""));
        }
        for (com.workflow.entity.EntityFormNode sourceNode : sourceNodes) {
            com.workflow.entity.EntityFormNode newNode =
                    new com.workflow.entity.EntityFormNode();
            BeanUtils.copyProperties(sourceNode, newNode);
            newNode.setId(copiedIds.get(sourceNode.getId()));
            newNode.setFormId(newForm.getId());
            newNode.setParentId(copiedIds.get(sourceNode.getParentId()));
            newNode.setRevision(1);
            newNode.setCreatedAt(LocalDateTime.now());
            newNode.setUpdatedAt(LocalDateTime.now());
            newNode.setDeleted(0);
            formNodeMapper.insert(newNode);
        }
        
        log.info("复制表单：{} -> {}", sourceForm.getFormName(), newForm.getFormName());
        
        // 填充详情返回
        fillFormDetails(newForm);
        newForm.setFields(getFormFields(newForm.getId()));
        newForm.setNodes(formNodeMapper.findByFormId(newForm.getId()));
        
        return newForm;
    }
}
