package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.dto.EntityFieldDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityPublishHistory;
import com.workflow.entity.EntityRelation;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityPublishHistoryMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 实体定义服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDefinitionService {
    
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityPublishHistoryMapper publishHistoryMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final EntityDataDynamicMapper entityDataDynamicMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityPublishHistoryService publishHistoryService;
    private final EntityFieldFileItemService fileItemService;
    private final ObjectMapper objectMapper;
    
    /**
     * 查询所有实体定义
     */
    @Transactional(readOnly = true)
    public List<EntityDefinitionDTO> findAll() {
        List<EntityDefinition> list = entityMapper.selectList(null);
        
        // 批量查询流程信息，避免N+1问题
        List<String> processIds = list.stream()
                .map(EntityDefinition::getProcessDefinitionId)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, String> processNameMap = processIds.stream()
                .map(id -> processMapper.selectById(id))
                .filter(process -> process != null)
                .collect(Collectors.toMap(
                    ProcessDefinitionConfig::getId,
                    ProcessDefinitionConfig::getProcessName,
                    (v1, v2) -> v1
                ));
        
        return list.stream()
                .map(entity -> convertToDTO(entity, processNameMap.get(entity.getProcessDefinitionId())))
                .collect(Collectors.toList());
    }
    
    /**
     * 根据ID查询
     */
    @Transactional(readOnly = true)
    public EntityDefinitionDTO findById(String id) {
        EntityDefinition entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + id);
        }
        // 加载字段
        List<EntityField> fields = fieldMapper.findByEntityId(id);
        entity.setFields(fields);
        // 查询流程名称
        String processName = getProcessName(entity.getProcessDefinitionId());
        return convertToDTO(entity, processName);
    }
    
    /**
     * 根据编码查询
     */
    @Transactional(readOnly = true)
    public EntityDefinitionDTO findByCode(String code) {
        EntityDefinition entity = entityMapper.findByEntityCode(code)
                .orElseThrow(() -> new RuntimeException("实体不存在: " + code));
        // 加载字段
        List<EntityField> fields = fieldMapper.findByEntityId(entity.getId());
        entity.setFields(fields);
        // 查询流程名称
        String processName = getProcessName(entity.getProcessDefinitionId());
        return convertToDTO(entity, processName);
    }
    
    /**
     * 驼峰命名转下划线命名
     */
    private String toSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
    
    private String getProcessName(String processId) {
        if (processId == null || processId.isEmpty()) {
            return null;
        }
        ProcessDefinitionConfig process = processMapper.selectById(processId);
        return process != null ? process.getProcessName() : null;
    }
    
    /**
     * 保存实体定义
     */
    @Transactional
    public EntityDefinitionDTO save(EntityDefinitionDTO dto) {
        // 校验实体编码唯一性（不区分大小写）
        validateEntityCodeUnique(dto.getEntityCode());
        
        // 校验字段编码唯一性
        validateFieldCodeUnique(dto.getFields());
        
        EntityDefinition entity = convertToEntity(dto);
        entityMapper.insert(entity);
        
        // 添加系统标准字段
        addSystemFields(entity.getId());
        
        // 保存字段（新建时确保字段ID为空，避免重复使用旧ID）
        if (dto.getFields() != null) {
            for (EntityFieldDTO fieldDTO : dto.getFields()) {
                EntityField field = convertToEntity(fieldDTO);
                field.setId(null); // 新建实体时，字段ID必须为空
                field.setEntityId(entity.getId());
                fieldMapper.insert(field);
            }
            syncRelations(entity, dto.getFields(), fieldMapper.findByEntityId(entity.getId()));
        }
        
        return convertToDTO(entity);
    }
    
    /**
     * 为实体添加系统标准字段
     * 系统字段说明：
     * - name: 数据名称（可编辑字段大小）
     * - code: 数据编码（可编辑字段大小）
     * - 其他字段：系统自动维护，不可编辑
     */
    private void addSystemFields(String entityId) {
        int sortOrder = 0;
        
        // 1. name - 数据名称（可编辑）
        EntityField nameField = createSystemField(entityId, "name", "数据名称", 
                EntityField.FieldType.STRING, "varchar(200)", 200, true, ++sortOrder);
        fieldMapper.insert(nameField);
        
        // 2. code - 数据编码（可编辑）
        EntityField codeField = createSystemField(entityId, "code", "数据编码", 
                EntityField.FieldType.STRING, "varchar(100)", 100, true, ++sortOrder);
        codeField.setIsUnique(true); // 编码默认唯一
        fieldMapper.insert(codeField);
        
        // 3. status - 状态（不可编辑，系统维护）
        EntityField statusField = createSystemField(entityId, "status", "状态", 
                EntityField.FieldType.STRING, "varchar(20)", 20, false, ++sortOrder);
        statusField.setDefaultValue("DRAFT");
        fieldMapper.insert(statusField);
        
        // 4. processInstanceId - 流程实例ID（不可编辑）
        EntityField processIdField = createSystemField(entityId, "processInstanceId", "流程实例ID", 
                EntityField.FieldType.STRING, "varchar(64)", 64, false, ++sortOrder);
        fieldMapper.insert(processIdField);
        
        // 5. processStartTime - 流程开始时间（不可编辑）
        EntityField processStartField = createSystemField(entityId, "processStartTime", "流程开始时间", 
                EntityField.FieldType.DATETIME, "datetime", null, false, ++sortOrder);
        fieldMapper.insert(processStartField);
        
        // 6. processEndTime - 流程结束时间（不可编辑）
        EntityField processEndField = createSystemField(entityId, "processEndTime", "流程结束时间", 
                EntityField.FieldType.DATETIME, "datetime", null, false, ++sortOrder);
        fieldMapper.insert(processEndField);
        
        // 7. submitterId - 提交人ID（不可编辑）
        EntityField submitterIdField = createSystemField(entityId, "submitterId", "提交人ID", 
                EntityField.FieldType.STRING, "varchar(64)", 64, false, ++sortOrder);
        fieldMapper.insert(submitterIdField);
        
        // 8. submitterName - 提交人姓名（不可编辑）
        EntityField submitterNameField = createSystemField(entityId, "submitterName", "提交人",
                EntityField.FieldType.STRING, "varchar(100)", 100, false, ++sortOrder);
        fieldMapper.insert(submitterNameField);

        // 9. deptId - 所属部门（可编辑，关联系统组织表）
        EntityField deptIdField = createSystemField(entityId, "deptId", "所属部门",
                EntityField.FieldType.REFERENCE, "varchar(64)", 64, true, ++sortOrder);
        deptIdField.setRefEntityType(EntityField.RefEntityType.DEPT);
        fieldMapper.insert(deptIdField);

        log.info("已为实体 [{}] 添加系统标准字段", entityId);
    }
    
    /**
     * 创建系统字段
     */
    private EntityField createSystemField(String entityId, String fieldCode, String fieldName, 
            EntityField.FieldType fieldType, String dbType, Integer fieldLength, 
            boolean editable, int sortOrder) {
        EntityField field = new EntityField();
        field.setEntityId(entityId);
        field.setFieldCode(fieldCode);
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        field.setDbType(dbType);
        field.setFieldLength(fieldLength);
        field.setIsRequired(false);
        field.setIsSystem(true); // 标记为系统字段
        field.setEditable(editable); // 是否可编辑
        field.setSortOrder(sortOrder);
        return field;
    }
    
    /**
     * 校验实体编码唯一性（不区分大小写）
     */
    private void validateEntityCodeUnique(String entityCode) {
        if (entityCode == null || entityCode.trim().isEmpty()) {
            throw new RuntimeException("实体编码不能为空");
        }
        
        // 检查是否已存在相同编码（不区分大小写）
        List<EntityDefinition> allEntities = entityMapper.selectList(null);
        for (EntityDefinition existing : allEntities) {
            if (existing.getEntityCode() != null && 
                existing.getEntityCode().equalsIgnoreCase(entityCode.trim())) {
                throw new RuntimeException("实体编码 [" + entityCode + "] 已存在，请更换其他编码");
            }
        }
    }
    
    /**
     * 校验字段编码唯一性（同一实体内字段编码不能重复）
     */
    private void validateFieldCodeUnique(List<EntityFieldDTO> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        
        java.util.Set<String> fieldCodes = new java.util.HashSet<>();
        for (EntityFieldDTO field : fields) {
            if (field.getFieldCode() == null || field.getFieldCode().trim().isEmpty()) {
                continue;
            }
            String code = field.getFieldCode().trim();
            if (!fieldCodes.add(code)) {
                throw new RuntimeException("字段编码 [" + code + "] 已存在，同一实体内字段编码不能重复，请修改后重试");
            }
        }
    }
    
    /**
     * 更新实体定义
     * 注意：实体发布后，已保存的字段不能再删除，只能添加新字段
     */
    @Transactional
    public EntityDefinitionDTO update(String id, EntityDefinitionDTO dto) {
        // 校验字段编码唯一性
        validateFieldCodeUnique(dto.getFields());
        
        EntityDefinition existing = entityMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("实体不存在: " + id);
        }
        
        // 检查实体是否已发布
        boolean isPublished = existing.getStatus() == EntityDefinition.Status.PUBLISHED;
        String entityCode = existing.getEntityCode();
        
        existing.setEntityName(dto.getEntityName());
        existing.setDescription(dto.getDescription());
        existing.setProcessDefinitionId(dto.getProcessDefinitionId());
        existing.setEnableProcess(dto.getEnableProcess());
        
        entityMapper.updateById(existing);
        
        // 更新字段
        if (dto.getFields() != null) {
            // 获取现有字段
            List<EntityField> existingFields = fieldMapper.findByEntityId(id);
            Map<String, EntityField> existingFieldMap = existingFields.stream()
                    .collect(Collectors.toMap(EntityField::getFieldCode, f -> f));
            
            // 收集需要保留的字段编码
            List<String> newFieldCodes = dto.getFields().stream()
                    .map(EntityFieldDTO::getFieldCode)
                    .filter(code -> code != null && !code.isEmpty())
                    .collect(Collectors.toList());
            
            if (isPublished) {
                // 已发布的实体：不允许删除字段，只能添加新字段
                for (EntityField existingField : existingFields) {
                    if (!Boolean.TRUE.equals(existingField.getIsSystem()) && 
                        !newFieldCodes.contains(existingField.getFieldCode())) {
                        throw new RuntimeException("实体已发布，不允许删除字段: " + existingField.getFieldCode() + 
                                "。已发布的实体只能添加新字段，不能删除已有字段。");
                    }
                }
            } else {
                // 未发布的实体：可以删除非系统字段
                for (EntityField field : existingFields) {
                    if (!Boolean.TRUE.equals(field.getIsSystem()) && 
                        !newFieldCodes.contains(field.getFieldCode())) {
                        fieldMapper.deleteById(field.getId());
                    }
                }
            }
            
            // 保存字段（系统字段允许更新部分属性，非系统字段正常更新）
            for (EntityFieldDTO fieldDTO : dto.getFields()) {
                String fieldCode = fieldDTO.getFieldCode();

                if (existingFieldMap.containsKey(fieldCode)) {
                    EntityField existingField = existingFieldMap.get(fieldCode);
                    if (Boolean.TRUE.equals(fieldDTO.getIsSystem())) {
                        // 系统字段只允许更新部分属性（名称、必填、默认值、选项、排序等），不允许修改编码和类型
                        existingField.setFieldName(fieldDTO.getFieldName());
                        existingField.setIsRequired(fieldDTO.getIsRequired());
                        existingField.setDefaultValue(fieldDTO.getDefaultValue());
                        existingField.setOptionsJson(fieldDTO.getOptionsJson());
                        existingField.setSortOrder(fieldDTO.getSortOrder());
                    } else {
                        // 非系统字段，更新字段定义（仅更新元数据，不修改数据库列）
                        existingField.setFieldName(fieldDTO.getFieldName());
                        existingField.setFieldLength(fieldDTO.getFieldLength());
                        existingField.setFieldPrecision(fieldDTO.getFieldPrecision());
                        existingField.setIsRequired(fieldDTO.getIsRequired());
                        existingField.setIsUnique(fieldDTO.getIsUnique());
                        existingField.setDefaultValue(fieldDTO.getDefaultValue());
                        existingField.setOptionsJson(fieldDTO.getOptionsJson());
                        existingField.setSortOrder(fieldDTO.getSortOrder());
                        existingField.setDbColumnName(toSnakeCase(fieldDTO.getFieldCode()));
                        existingField.setFileTypes(fieldDTO.getFileTypes());
                        existingField.setFileMaxSize(fieldDTO.getFileMaxSize());
                        existingField.setFileMaxCount(fieldDTO.getFileMaxCount());
                        // 实体引用/子表单字段
                        existingField.setRefEntityId(firstText(fieldDTO.getChildEntityId(), fieldDTO.getRefEntityId()));
                        if (fieldDTO.getRefEntityType() != null && !fieldDTO.getRefEntityType().isEmpty()) {
                            existingField.setRefEntityType(EntityField.RefEntityType.valueOf(fieldDTO.getRefEntityType()));
                        } else if (isRelationField(fieldDTO)) {
                            existingField.setRefEntityType(EntityField.RefEntityType.CUSTOM);
                        } else {
                            existingField.setRefEntityType(null);
                        }
                        existingField.setDisplayMode(fieldDTO.getDisplayMode());
                        existingField.setRefFieldCode(firstText(fieldDTO.getChildRefFieldCode(), fieldDTO.getRefFieldCode()));
                    }
                    fieldMapper.updateById(existingField);
                    // 级联保存附件项配置
                    fileItemService.saveFileItems(existingField.getId(), fieldDTO.getFileItems());
                } else {
                    // 跳过系统字段的新增（系统字段已在初始化时创建）
                    if (Boolean.TRUE.equals(fieldDTO.getIsSystem())) {
                        continue;
                    }
                    // 新字段，添加到字段定义表（不立即同步到数据表，等发布时同步）
                    EntityField field = convertToEntity(fieldDTO);
                    field.setId(null);
                    field.setEntityId(id);
                    field.setIsSystem(false);
                    field.setEditable(true);
                    field.setIsPublished(false); // 新字段标记为未发布
                    fieldMapper.insert(field);
                    // 级联保存附件项配置
                    fileItemService.saveFileItems(field.getId(), fieldDTO.getFileItems());
                    
                    // 注意：不再自动添加字段到数据表，只有点击发布时才同步
                }
            }

            syncRelations(existing, dto.getFields(), fieldMapper.findByEntityId(id));
            
            // 如果实体已发布，自动同步物理表结构（添加/修改字段）
            if (isPublished) {
                try {
                    // 重新加载完整的实体定义（包含最新字段）
                    EntityDefinition updatedEntity = entityMapper.selectById(id);
                    List<EntityField> allFields = fieldMapper.findByEntityId(id);
                    updatedEntity.setFields(allFields);
                    
                    // 同步物理表结构
                    List<String> ddlStatements = dynamicTableService.syncEntityTableStructure(updatedEntity);
                    if (!ddlStatements.isEmpty()) {
                        log.info("实体 [{}] 字段变更后自动同步物理表，执行DDL: {}", entityCode, ddlStatements);
                    }
                    
                    // 将所有未发布的字段标记为已发布
                    for (EntityField field : allFields) {
                        if (!Boolean.TRUE.equals(field.getIsSystem()) && !Boolean.TRUE.equals(field.getIsPublished())) {
                            field.setIsPublished(true);
                            fieldMapper.updateById(field);
                        }
                    }
                } catch (Exception e) {
                    log.error("实体 [{}] 字段变更后自动同步物理表失败: {}", entityCode, e.getMessage(), e);
                    // 不抛异常，避免影响字段保存操作
                }
            }
        }
        
        return convertToDTO(existing);
    }
    
    /**
     * 删除实体定义
     */
    @Transactional
    public void delete(String id) {
        relationMapper.deleteByParentEntityId(id);
        fieldMapper.deleteByEntityId(id);
        entityMapper.deleteById(id);
    }
    
    /**
     * 发布实体
     * 发布时自动创建数据表，并记录版本历史
     */
    @Transactional
    public EntityDefinitionDTO publish(String id, String userId, String userName) {
        EntityDefinition entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + id);
        }
        
        // 加载字段
        List<EntityField> fields = fieldMapper.findByEntityId(id);
        entity.setFields(fields);
        
        // 判断是首次发布还是字段变更
        boolean isFirstPublish = entity.getStatus() != EntityDefinition.Status.PUBLISHED;
        EntityPublishHistory.PublishType publishType = isFirstPublish 
                ? EntityPublishHistory.PublishType.CREATE 
                : EntityPublishHistory.PublishType.ALTER;
        
        // 同步表结构（创建表或添加字段）
        List<String> executedDdls = dynamicTableService.syncEntityTableStructure(entity);
        
        // 标记已同步的字段为已发布状态
        int publishedFieldCount = 0;
        for (EntityField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsSystem()) && 
                !Boolean.TRUE.equals(field.getIsPublished())) {
                field.setIsPublished(true);
                fieldMapper.updateById(field);
                publishedFieldCount++;
            }
        }
        if (publishedFieldCount > 0) {
            log.info("实体 [{}] 发布完成，标记 {} 个字段为已发布状态", entity.getEntityCode(), publishedFieldCount);
        }
        
        // 构建变更描述
        String changesDesc = buildChangesDescription(isFirstPublish, executedDdls, fields);
        
        // 记录版本历史
        String ddlString = executedDdls.isEmpty() ? null : String.join(";\n", executedDdls);
        publishHistoryService.createVersion(
                entity, fields, ddlString, publishType, changesDesc, userId, userName);
        
        entity.setStatus(EntityDefinition.Status.PUBLISHED);
        entityMapper.updateById(entity);
        return convertToDTO(entity);
    }
    
    /**
     * 构建变更描述
     */
    private String buildChangesDescription(boolean isFirstPublish, List<String> executedDdls, List<EntityField> fields) {
        if (isFirstPublish) {
            return "首次发布，创建数据表，包含 " + fields.size() + " 个字段";
        }
        
        if (executedDdls.isEmpty()) {
            return "无字段变更";
        }
        
        // 统计新增字段
        int addCount = 0;
        for (String ddl : executedDdls) {
            if (ddl.contains("ADD COLUMN")) {
                addCount++;
            }
        }
        
        if (addCount > 0) {
            return "新增 " + addCount + " 个字段到数据表";
        }
        
        return "表结构同步完成";
    }
    
    /**
     * 绑定流程
     * @param entityId 实体ID
     * @param processId 流程定义ID
     * @return 更新后的实体DTO
     */
    @Transactional
    public EntityDefinitionDTO bindProcess(String entityId, String processId) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + entityId);
        }
        
        // 如果要切换流程（原流程不为空且新流程不同），检查是否有流程数据
        String oldProcessId = entity.getProcessDefinitionId();
        if (oldProcessId != null && !oldProcessId.equals(processId)) {
            // 检查是否有流程数据（使用新表结构）
            String tableName = dynamicTableService.getTableName(entity.getEntityCode());
            int processDataCount = 0;
            if (dynamicTableService.tableExists(entity.getEntityCode())) {
                processDataCount = (int) entityDataDynamicMapper.count(tableName);
            }
            if (processDataCount > 0) {
                throw new RuntimeException("该实体已有 " + processDataCount + " 条流程数据，无法切换绑定的流程。请先处理完现有流程数据后再切换。");
            }
        }
        
        entity.setProcessDefinitionId(processId);
        entity.setEnableProcess(true);
        entityMapper.updateById(entity);

        EntityPublishHistory latestHistory = publishHistoryMapper.findLatestByEntityId(entityId);
        if (latestHistory != null) {
            latestHistory.setProcessDefinitionId(processId);
            publishHistoryMapper.updateById(latestHistory);
        }

        return convertToDTO(entity);
    }

    /**
     * 根据流程定义ID查询绑定的实体
     */
    @Transactional(readOnly = true)
    public EntityDefinitionDTO findByProcessDefinitionId(String processDefinitionId) {
        EntityDefinition entity = entityMapper.findByProcessDefinitionId(processDefinitionId)
                .orElseThrow(() -> new RuntimeException("该流程未绑定实体"));
        // 加载字段
        List<EntityField> fields = fieldMapper.findByEntityId(entity.getId());
        entity.setFields(fields);
        // 查询流程名称
        String processName = getProcessName(entity.getProcessDefinitionId());
        return convertToDTO(entity, processName);
    }

    private void syncRelations(EntityDefinition parent, List<EntityFieldDTO> fieldDtos, List<EntityField> savedFields) {
        if (parent == null || parent.getId() == null) {
            return;
        }
        relationMapper.deleteByParentEntityId(parent.getId());
        if (fieldDtos == null || fieldDtos.isEmpty()) {
            return;
        }

        Map<String, EntityField> fieldMap = savedFields == null ? new HashMap<>() : savedFields.stream()
                .filter(field -> field.getFieldCode() != null)
                .collect(Collectors.toMap(EntityField::getFieldCode, field -> field, (left, right) -> left));

        for (EntityFieldDTO fieldDTO : fieldDtos) {
            if (!isRelationField(fieldDTO)) {
                continue;
            }

            String childEntityId = firstText(fieldDTO.getChildEntityId(), fieldDTO.getRefEntityId());
            String childRefFieldCode = firstText(fieldDTO.getChildRefFieldCode(), fieldDTO.getRefFieldCode());
            if (childEntityId == null) {
                throw new RuntimeException("请选择子实体: " + fieldDTO.getFieldCode());
            }
            if (childRefFieldCode == null) {
                throw new RuntimeException("请选择子表外键: " + fieldDTO.getFieldCode());
            }

            EntityDefinition child = entityMapper.selectById(childEntityId);
            if (child == null) {
                throw new RuntimeException("子实体不存在: " + childEntityId);
            }

            EntityField savedField = fieldMap.get(fieldDTO.getFieldCode());
            EntityRelation relation = new EntityRelation();
            relation.setParentEntityId(parent.getId());
            relation.setParentEntityCode(parent.getEntityCode());
            relation.setParentFieldId(savedField != null ? savedField.getId() : fieldDTO.getId());
            relation.setParentFieldCode(fieldDTO.getFieldCode());
            relation.setRelationCode(firstText(fieldDTO.getRelationCode(), parent.getEntityCode() + "_" + fieldDTO.getFieldCode()));
            relation.setRelationName(firstText(fieldDTO.getRelationName(), fieldDTO.getFieldName()));
            relation.setChildEntityId(child.getId());
            relation.setChildEntityCode(child.getEntityCode());
            relation.setChildRefFieldCode(childRefFieldCode);
            relation.setRelationType(resolveRelationType(fieldDTO));
            relation.setCascadeDelete(fieldDTO.getCascadeDelete() == null ? true : fieldDTO.getCascadeDelete());
            relation.setRequired(fieldDTO.getRelationRequired() != null ? fieldDTO.getRelationRequired() : fieldDTO.getIsRequired());
            relation.setEnabled(true);
            relation.setDeleted(0);
            relation.setSortOrder(fieldDTO.getSortOrder());
            relationMapper.insert(relation);
        }
    }

    private boolean isRelationField(EntityFieldDTO fieldDTO) {
        return fieldDTO != null && (fieldDTO.getFieldType() == EntityField.FieldType.SUB_FORM
                || fieldDTO.getFieldType() == EntityField.FieldType.SUB_FORM_LIST);
    }

    private EntityRelation.RelationType resolveRelationType(EntityFieldDTO fieldDTO) {
        String relationType = firstText(fieldDTO.getRelationType(), null);
        if (relationType != null) {
            return EntityRelation.RelationType.valueOf(relationType);
        }
        return fieldDTO.getFieldType() == EntityField.FieldType.SUB_FORM
                ? EntityRelation.RelationType.ONE_TO_ONE
                : EntityRelation.RelationType.ONE_TO_MANY;
    }

    private String firstText(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }

    private void enrichRelationFields(EntityDefinitionDTO dto) {
        if (dto == null || dto.getId() == null || dto.getFields() == null || dto.getFields().isEmpty()) {
            return;
        }
        List<EntityRelation> relations = relationMapper.selectByParentEntityId(dto.getId());
        if (relations == null || relations.isEmpty()) {
            return;
        }
        Map<String, EntityRelation> relationMap = relations.stream()
                .filter(relation -> relation.getParentFieldCode() != null)
                .collect(Collectors.toMap(EntityRelation::getParentFieldCode, relation -> relation, (left, right) -> left));
        for (EntityFieldDTO field : dto.getFields()) {
            EntityRelation relation = relationMap.get(field.getFieldCode());
            if (relation == null) {
                continue;
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
    }

    // 转换方法
    private EntityDefinitionDTO convertToDTO(EntityDefinition entity) {
        return convertToDTO(entity, null);
    }
    
    private EntityDefinitionDTO convertToDTO(EntityDefinition entity, String processName) {
        EntityDefinitionDTO dto = new EntityDefinitionDTO();
        dto.setId(entity.getId());
        dto.setEntityCode(entity.getEntityCode());
        dto.setEntityName(entity.getEntityName());
        dto.setDescription(entity.getDescription());
        dto.setProcessDefinitionId(entity.getProcessDefinitionId());
        dto.setProcessName(processName);
        dto.setEnableProcess(entity.getEnableProcess());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        
        if (entity.getFields() != null) {
            dto.setFields(entity.getFields().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList()));
            enrichRelationFields(dto);
        }
        
        return dto;
    }
    
    private EntityFieldDTO convertToDTO(EntityField field) {
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
        dto.setRefEntityType(field.getRefEntityType() != null ? field.getRefEntityType().name() : null);
        dto.setDisplayMode(field.getDisplayMode());
        dto.setRefFieldCode(field.getRefFieldCode());
        // 加载文件字段的多组附件配置
        if (field.getFieldType() == EntityField.FieldType.FILE || field.getFieldType() == EntityField.FieldType.IMAGE) {
            try {
                dto.setFileItems(fileItemService.findByFieldId(field.getId()));
            } catch (Exception e) {
                // 表可能不存在，忽略异常
                dto.setFileItems(null);
            }
        }
        return dto;
    }
    
    private EntityDefinition convertToEntity(EntityDefinitionDTO dto) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(dto.getId());
        entity.setEntityCode(dto.getEntityCode());
        entity.setEntityName(dto.getEntityName());
        entity.setDescription(dto.getDescription());
        entity.setProcessDefinitionId(dto.getProcessDefinitionId());
        entity.setEnableProcess(dto.getEnableProcess());
        entity.setStatus(dto.getStatus());
        entity.setCreatedBy(dto.getCreatedBy());
        return entity;
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
        // 自动计算数据库列名（驼峰转下划线）
        if (dto.getDbColumnName() != null && !dto.getDbColumnName().isEmpty()) {
            field.setDbColumnName(dto.getDbColumnName());
        } else {
            field.setDbColumnName(toSnakeCase(dto.getFieldCode()));
        }
        field.setIsRequired(dto.getIsRequired());
        field.setIsUnique(dto.getIsUnique());
        field.setDefaultValue(dto.getDefaultValue());
        field.setOptionsJson(dto.getOptionsJson());
        field.setValidateRules(dto.getValidateRules());
        field.setSortOrder(dto.getSortOrder());
        field.setFileTypes(dto.getFileTypes());
        field.setFileMaxSize(dto.getFileMaxSize());
        field.setFileMaxCount(dto.getFileMaxCount());
        // 实体引用/子表单字段
        field.setRefEntityId(firstText(dto.getChildEntityId(), dto.getRefEntityId()));
        if (dto.getRefEntityType() != null && !dto.getRefEntityType().isEmpty()) {
            field.setRefEntityType(EntityField.RefEntityType.valueOf(dto.getRefEntityType()));
        } else if (isRelationField(dto)) {
            field.setRefEntityType(EntityField.RefEntityType.CUSTOM);
        }
        field.setDisplayMode(dto.getDisplayMode());
        field.setRefFieldCode(firstText(dto.getChildRefFieldCode(), dto.getRefFieldCode()));
        return field;
    }
}
