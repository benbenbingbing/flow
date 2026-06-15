package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityRelation;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体表单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFormService {
    
    private final EntityFormMapper formMapper;
    private final EntityFormFieldMapper formFieldMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    
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
        }
        return form;
    }

    /**
     * 保存表单
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityForm saveForm(EntityForm form) {
        // 校验表单标识唯一性
        if (StringUtils.hasText(form.getFormKey())) {
            String excludeId = form.getId() != null ? form.getId() : "";
            if (formMapper.existsFormKey(form.getEntityId(), form.getFormKey(), excludeId)) {
                throw new RuntimeException("表单标识已存在：" + form.getFormKey());
            }
        }
        
        // 设置默认值
        if (!StringUtils.hasText(form.getLayoutType())) {
            form.setLayoutType("vertical");
        }
        if (form.getStatus() == null) {
            form.setStatus(1);
        }
        if (form.getIsDefault() == null) {
            form.setIsDefault(false);
        }
        
        form.setUpdateTime(LocalDateTime.now());
        
        if (!StringUtils.hasText(form.getId())) {
            // 新增
            form.setCreateTime(LocalDateTime.now());
            formMapper.insert(form);
            log.info("新增实体表单：{}", form.getFormName());
        } else {
            // 更新
            formMapper.updateById(form);
            log.info("更新实体表单：{}", form.getFormName());
        }
        
        // 如果设置为默认表单，将同一实体下的其他表单设为非默认
        if (Boolean.TRUE.equals(form.getIsDefault())) {
            clearOtherDefaultForm(form.getEntityId(), form.getId());
        }
        
        // 保存字段
        if (form.getFields() != null) {
            saveFormFields(form.getId(), form.getFields());
        }
        
        return form;
    }
    
    /**
     * 将同一实体下的其他表单设为非默认
     */
    private void clearOtherDefaultForm(String entityId, String currentFormId) {
        List<EntityForm> forms = formMapper.selectByEntityId(entityId);
        for (EntityForm form : forms) {
            if (!form.getId().equals(currentFormId) && Boolean.TRUE.equals(form.getIsDefault())) {
                form.setIsDefault(false);
                formMapper.updateById(form);
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
    public void updateInitConfig(String id, String initConfig) {
        EntityForm form = formMapper.selectById(id);
        if (form == null) {
            throw new RuntimeException("表单不存在");
        }
        UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
               .set("init_config", initConfig)
               .set("update_time", LocalDateTime.now());
        formMapper.update(null, wrapper);
        log.info("更新表单初始化配置：{}", form.getFormName());
    }
    
    /**
     * 保存表单字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFormFields(String formId, List<EntityFormField> fields) {
        // 删除原有字段
        formFieldMapper.deleteByFormId(formId);

        // 保存新字段
        if (fields != null && !fields.isEmpty()) {
            for (int i = 0; i < fields.size(); i++) {
                EntityFormField field = fields.get(i);
                field.setFormId(formId);
                field.setSortOrder(i);
                field.setCreateTime(LocalDateTime.now());
                field.setUpdateTime(LocalDateTime.now());

                // 如果 fieldCode 为空，尝试从关联的实体字段补充
                if (!StringUtils.hasText(field.getFieldCode()) && field.getFieldId() != null) {
                    com.workflow.entity.EntityField entityField = fieldMapper.findByIdString(field.getFieldId());
                    if (entityField != null && StringUtils.hasText(entityField.getFieldCode())) {
                        field.setFieldCode(entityField.getFieldCode());
                    }
                }

                formFieldMapper.insert(field);
            }
        }
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
        newForm.setStatus(1);
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
                newField.setGridSpan(sourceField.getGridSpan());
                newField.setSortOrder(i);
                newField.setCreateTime(LocalDateTime.now());
                newField.setUpdateTime(LocalDateTime.now());
                
                formFieldMapper.insert(newField);
            }
        }
        
        log.info("复制表单：{} -> {}", sourceForm.getFormName(), newForm.getFormName());
        
        // 填充详情返回
        fillFormDetails(newForm);
        newForm.setFields(getFormFields(newForm.getId()));
        
        return newForm;
    }
}
