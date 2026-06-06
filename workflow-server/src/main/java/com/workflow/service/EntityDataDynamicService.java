package com.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.DataPermissionResult;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityRelation;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.ProcessTask;
import com.workflow.entity.SysUser;
import com.workflow.listener.MultiInstanceCollectionListener;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessTaskMapper;
import com.workflow.service.permission.DataPermissionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体数据动态服务
 * 使用独立表存储每个实体的数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDataDynamicService {

    private static final int MAX_RELATION_DEPTH = 8;

    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityStatusMapper entityStatusMapper;
    private final ProcessTaskMapper processTaskMapper;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final DynamicTableService dynamicTableService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final IdentityService identityService;
    private final org.flowable.engine.TaskService taskService;
    private final EntityCodeGeneratorService codeGeneratorService;
    private final ObjectMapper objectMapper;
    private final ProcessTaskService processTaskService;
    private final MultiInstanceCollectionListener multiInstanceCollectionListener;
    private final DataPermissionEngine dataPermissionEngine;
    private final SysUserService sysUserService;

    /**
     * 查询某实体的所有数据（带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByEntityCode(String entityCode) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode);

        List<Map<String, Object>> dataList;
        if (!permission.isHasPermission()) {
            dataList = new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            dataList = dynamicMapper.selectList(tableName);
        } else {
            dataList = dynamicMapper.selectListWithPermission(tableName, permission.getSqlCondition());
        }

        return dataList.stream()
                .map(data -> convertToDTO(data, entityCode))
                .collect(Collectors.toList());
    }

    /**
     * 查询某实体的所有数据（返回原始Map，用于选择器，带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByEntityCodeSimple(String entityCode) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode);

        if (!permission.isHasPermission()) {
            return new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            return dynamicMapper.selectList(tableName);
        } else {
            return dynamicMapper.selectListWithPermission(tableName, permission.getSqlCondition());
        }
    }

    /**
     * 根据ID查询
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findById(String entityCode, String id) {
        String tableName = dynamicTableService.getTableName(entityCode);
        Map<String, Object> data = dynamicMapper.selectById(tableName, id);
        if (data == null) {
            throw new RuntimeException("数据不存在: " + id);
        }
        EntityDataDTO dto = convertToDTO(data, entityCode);
        loadSubFormData(dto);
        return dto;
    }

    /**
     * 根据流程实例ID查询
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findByProcessInstanceId(String entityCode, String processInstanceId) {
        String tableName = dynamicTableService.getTableName(entityCode);
        Map<String, Object> data = dynamicMapper.selectByProcessInstanceId(tableName, processInstanceId);
        if (data == null) {
            throw new RuntimeException("数据不存在: " + processInstanceId);
        }
        EntityDataDTO dto = convertToDTO(data, entityCode);
        loadSubFormData(dto);
        return dto;
    }

    /**
     * 保存实体数据
     * 如果绑定了流程且startProcess为true，则同时发起流程
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO save(EntityDataDTO dto) {
        log.info("保存数据: entityCode={}, id={}, data={}", dto.getEntityCode(), dto.getId(), dto.getData());
        String entityCode = dto.getEntityCode();
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new RuntimeException("实体不存在: " + entityCode));
        List<EntityRelation> relations = loadRelations(definition);
        Map<String, Object> originalData = dto.getData();
        Map<String, Object> parentData = withoutRelationData(originalData, relations);
        Map<String, Object> relationData = extractRelationData(originalData, relations);

        // 验证实体是否启用流程
        if (Boolean.TRUE.equals(dto.getStartProcess()) &&
                !Boolean.TRUE.equals(definition.getEnableProcess())) {
            throw new RuntimeException("该实体未启用流程，无法发起流程");
        }

        // 获取表名
        String tableName = dynamicTableService.getTableName(entityCode);

        // 检查表是否存在
        if (!dynamicTableService.tableExists(entityCode)) {
            // 自动创建表
            dynamicTableService.createEntityTable(definition);
        }

        // 获取当前用户信息
        String currentUserId = getCurrentUserId(dto.getSubmitterId());
        String currentUserName = getCurrentUserName(dto.getSubmitterName());

        // 准备数据
        dto.setData(parentData);
        Map<String, Object> data = convertToMap(dto);
        dto.setData(originalData);

        if (dto.getId() == null || dto.getId().isEmpty()) {
            // ========== 新增数据 ==========
            String id = generateId();
            data.put("id", id);
            data.put("created_by", currentUserId);
            data.put("created_at", LocalDateTime.now());
            data.put("updated_by", currentUserId);
            data.put("updated_at", LocalDateTime.now());
            data.put("deleted", 0);

            // 设置提交人（新增时设置，之后不可修改）
            data.put("submitter_id", currentUserId);
            data.put("submitter_name", currentUserName);
            dto.setSubmitterId(currentUserId);
            dto.setSubmitterName(currentUserName);

            // 设置部门ID（用于数据权限）
            String currentDeptId = getCurrentDeptId();
            if (currentDeptId != null) {
                data.put("dept_id", currentDeptId);
            }

            // 设置默认状态（从实体状态配置中获取 NEW 分类的第一个状态）
            String defaultStatus = getDefaultStatus(entityCode);
            data.put("status", defaultStatus);
            dto.setStatus(defaultStatus);

            // 流程相关字段初始为空
            // process_instance_id, process_start_time, process_end_time 初始为空

            // 生成数据编码（code）- 使用编码规则服务
            String code = codeGeneratorService.generateCode(entityCode);
            data.put("code", code);
            dto.setCode(code);

            // 如果有流程，生成业务单号
            if (Boolean.TRUE.equals(definition.getEnableProcess())) {
                String dataNo = generateDataNo(entityCode);
                data.put("data_no", dataNo);
                dto.setDataNo(dataNo);
            }
            
            // 从表单数据中提取 name 字段并设置到主表
            if (dto.getData() != null && dto.getData().get("name") != null) {
                String name = (String) dto.getData().get("name");
                data.put("name", name);
                dto.setName(name);
            }

            dynamicMapper.insert(tableName, data);
            dto.setId(id);
        } else {
            // ========== 更新数据 ==========
            // 更新时不能修改提交人
            data.put("updated_by", currentUserId);
            data.put("updated_at", LocalDateTime.now());
            
            // 移除可能传入的提交人字段，防止被修改
            data.remove("submitter_id");
            data.remove("submitter_name");
            
            dynamicMapper.update(tableName, data);
        }

        saveRelationData(dto.getId(), relations, relationData, 1, new HashSet<>());

        // 如果需要发起流程
        if (Boolean.TRUE.equals(dto.getStartProcess()) &&
                definition.getProcessDefinitionId() != null) {
            startProcess(dto, definition, data);
        }

        return dto;
    }

    /**
     * 更新数据
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO update(String entityCode, String id, Map<String, Object> formData) {
        String tableName = dynamicTableService.getTableName(entityCode);
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new RuntimeException("实体不存在: " + entityCode));
        List<EntityRelation> relations = loadRelations(definition);
        Map<String, Object> parentFormData = withoutRelationDataFromRequest(formData, relations);
        Map<String, Object> relationData = extractRelationDataFromRequest(formData, relations);

        // 查询原数据
        Map<String, Object> existingData = dynamicMapper.selectById(tableName, id);
        if (existingData == null) {
            throw new RuntimeException("数据不存在: " + id);
        }

        // 构建更新数据：以 existingData 的列为基础（确保不更新不存在的列）
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", id);
        updateData.put("updated_by", UserContext.getUserId());
        updateData.put("updated_at", LocalDateTime.now());

        // 系统字段映射（前端 camelCase -> 数据库 snake_case）
        Map<String, String> standardFieldMap = new HashMap<>();
        standardFieldMap.put("name", "name");
        standardFieldMap.put("code", "code");
        standardFieldMap.put("status", "status");
        standardFieldMap.put("title", "title");
        standardFieldMap.put("dataNo", "data_no");
        standardFieldMap.put("processInstanceId", "process_instance_id");
        standardFieldMap.put("processStartTime", "process_start_time");
        standardFieldMap.put("processEndTime", "process_end_time");
        standardFieldMap.put("currentTaskId", "current_task_id");
        standardFieldMap.put("currentTaskName", "current_task_name");
        standardFieldMap.put("currentTaskAssignee", "current_task_assignee");
        standardFieldMap.put("submitterId", "submitter_id");
        standardFieldMap.put("submitterName", "submitter_name");
        standardFieldMap.put("deptId", "dept_id");
        standardFieldMap.put("submitTime", "submit_time");

        // 1. 从 formData 直接提取标准字段
        standardFieldMap.forEach((frontendKey, dbKey) -> {
            if (parentFormData.containsKey(frontendKey)) {
                Object value = parentFormData.get(frontendKey);
                if (value instanceof String && ((String) value).isEmpty()) {
                    value = null;
                }
                updateData.put(dbKey, value);
            }
        });

        // 2. 从 formData.data 提取自定义字段（排除系统字段，camelCase 转 snake_case）
        Set<String> systemFields = new HashSet<>(Arrays.asList(
                "id", "dataNo", "data_no", "title", "name", "code", "status",
                "processInstanceId", "process_instance_id",
                "processStartTime", "process_start_time",
                "processEndTime", "process_end_time",
                "currentTaskId", "current_task_id",
                "currentTaskName", "current_task_name",
                "currentTaskAssignee", "current_task_assignee",
                "submitterId", "submitter_id",
                "submitterName", "submitter_name",
                "deptId", "dept_id",
                "submitTime", "submit_time",
                "createdAt", "created_at", "updatedAt", "updated_at",
                "createdBy", "created_by", "updatedBy", "updated_by",
                "deleted", "entityCode", "entity_code", "startProcess", "start_process"
        ));

        Object dataObj = parentFormData.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) dataObj;
            for (Map.Entry<String, Object> entry : customData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key == null || key.isEmpty() || systemFields.contains(key)) {
                    continue;
                }
                String columnName = camelToUnderscore(key);
                if (systemFields.contains(columnName)) {
                    continue;
                }
                if (value instanceof String && ((String) value).isEmpty()) {
                    value = null;
                }
                if (value instanceof Map || value instanceof List) {
                    try {
                        value = objectMapper.writeValueAsString(value);
                    } catch (Exception e) {
                        log.warn("字段 {} 序列化 JSON 失败: {}", key, e.getMessage());
                    }
                }
                updateData.put(columnName, value);
            }
        }

        // 3. 补充 existingData 中缺失的字段（保持原有值）
        existingData.forEach((key, value) -> {
            if (!updateData.containsKey(key)) {
                updateData.put(key, value);
            }
        });

        dynamicMapper.update(tableName, updateData);
        saveRelationData(id, relations, relationData, 1, new HashSet<>());

        EntityDataDTO dto = convertToDTO(updateData, entityCode);
        if (dto.getData() != null) {
            dto.getData().putAll(relationData);
        }
        return dto;
    }

    private List<EntityField> loadEntityFields(EntityDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return List.of();
        }
        List<EntityField> fields = fieldMapper.findByEntityId(definition.getId());
        return fields != null ? fields : List.of();
    }

    private List<EntityRelation> loadRelations(EntityDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return List.of();
        }
        List<EntityRelation> relations = relationMapper.selectByParentEntityId(definition.getId());
        return relations != null ? relations : List.of();
    }

    private Map<String, Object> withoutRelationData(Map<String, Object> data, List<EntityRelation> relations) {
        if (data == null || data.isEmpty() || relations == null || relations.isEmpty()) {
            return data;
        }
        Map<String, Object> parentData = new HashMap<>(data);
        for (EntityRelation relation : relations) {
            if (StringUtils.hasText(relation.getParentFieldCode())) {
                parentData.remove(relation.getParentFieldCode());
            }
        }
        return parentData;
    }

    private Map<String, Object> withoutRelationDataFromRequest(Map<String, Object> formData, List<EntityRelation> relations) {
        if (formData == null || formData.isEmpty()) {
            return formData;
        }
        Map<String, Object> parentFormData = new HashMap<>(formData);
        Object dataObj = formData.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) dataObj;
            parentFormData.put("data", withoutRelationData(customData, relations));
        } else {
            parentFormData = withoutRelationData(parentFormData, relations);
        }
        return parentFormData;
    }

    private Map<String, Object> extractRelationData(Map<String, Object> data, List<EntityRelation> relations) {
        Map<String, Object> result = new HashMap<>();
        if (data == null || data.isEmpty() || relations == null || relations.isEmpty()) {
            return result;
        }
        for (EntityRelation relation : relations) {
            String fieldCode = relation.getParentFieldCode();
            if (StringUtils.hasText(fieldCode) && data.containsKey(fieldCode)) {
                result.put(fieldCode, data.get(fieldCode));
            }
        }
        return result;
    }

    private Map<String, Object> extractRelationDataFromRequest(Map<String, Object> formData, List<EntityRelation> relations) {
        if (formData == null) {
            return Map.of();
        }
        Object dataObj = formData.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customData = (Map<String, Object>) dataObj;
            return extractRelationData(customData, relations);
        }
        return extractRelationData(formData, relations);
    }

    private void saveRelationData(String parentId, List<EntityRelation> relations, Map<String, Object> relationData,
                                  int depth, Set<String> path) {
        if (!StringUtils.hasText(parentId) || relations == null || relations.isEmpty()
                || relationData == null || relationData.isEmpty() || depth > MAX_RELATION_DEPTH) {
            return;
        }
        for (EntityRelation relation : relations) {
            String fieldCode = relation.getParentFieldCode();
            if (!StringUtils.hasText(fieldCode) || !relationData.containsKey(fieldCode)) {
                continue;
            }

            String pathKey = relation.getParentEntityCode() + ":" + fieldCode;
            if (!path.add(pathKey)) {
                log.warn("关系存在循环，跳过: {}", pathKey);
                continue;
            }

            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())) {
                path.remove(pathKey);
                continue;
            }

            ensureEntityTable(childDefinition);
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            List<Map<String, Object>> existingRows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            List<Map<String, Object>> incomingRows = toRelationRows(relationData.get(fieldCode), relation.getRelationType());
            Set<String> activeIds = new HashSet<>();

            List<EntityRelation> childRelations = loadRelations(childDefinition);
            for (Map<String, Object> row : incomingRows) {
                Map<String, Object> childRelationData = extractRelationData(row, childRelations);
                Map<String, Object> childData = withoutRelationData(row, childRelations);
                childData.put(relation.getChildRefFieldCode(), parentId);
                childData.put("updated_by", UserContext.getUserId());
                childData.put("updated_at", LocalDateTime.now());
                childData.put("deleted", 0);
                normalizeJsonValues(childData);

                String childId = stringValue(childData.get("id"));
                if (!StringUtils.hasText(childId)) {
                    childId = generateId();
                    childData.put("id", childId);
                    childData.put("created_by", UserContext.getUserId());
                    childData.put("created_at", LocalDateTime.now());
                    dynamicMapper.insert(childTableName, childData);
                } else {
                    dynamicMapper.update(childTableName, childData);
                }
                activeIds.add(childId);
                saveRelationData(childId, childRelations, childRelationData, depth + 1, path);
            }

            deleteMissingRows(childTableName, existingRows, activeIds);
            path.remove(pathKey);
        }
    }

    private EntityDefinition loadChildEntity(EntityRelation relation) {
        EntityDefinition childDefinition = null;
        if (StringUtils.hasText(relation.getChildEntityId())) {
            childDefinition = definitionMapper.selectById(relation.getChildEntityId());
        }
        if (childDefinition == null && StringUtils.hasText(relation.getChildEntityCode())) {
            childDefinition = definitionMapper.findByEntityCode(relation.getChildEntityCode()).orElse(null);
        }
        if (childDefinition != null) {
            childDefinition.setFields(loadEntityFields(childDefinition));
        }
        return childDefinition;
    }

    private void ensureEntityTable(EntityDefinition definition) {
        if (!dynamicTableService.tableExists(definition.getEntityCode())) {
            dynamicTableService.createEntityTable(definition);
        }
    }

    private List<Map<String, Object>> toRelationRows(Object value, EntityRelation.RelationType relationType) {
        if (value == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) value;
            rows.add(new HashMap<>(row));
            return rows;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) item;
                    rows.add(new HashMap<>(row));
                }
            }
        }
        if (relationType == EntityRelation.RelationType.ONE_TO_ONE && rows.size() > 1) {
            return List.of(rows.get(0));
        }
        return rows;
    }

    private List<Map<String, Object>> findRowsByReference(String tableName, String refFieldCode, String parentId) {
        Map<String, Object> condition = new HashMap<>();
        condition.put(refFieldCode, parentId);
        condition.put(refFieldCode + "_op", "EQ");
        List<Map<String, Object>> rows = dynamicMapper.selectByCondition(tableName, condition);
        return rows != null ? rows : List.of();
    }

    private void deleteMissingRows(String tableName, List<Map<String, Object>> existingRows, Set<String> activeIds) {
        if (existingRows == null || existingRows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : existingRows) {
            String id = stringValue(row.get("id"));
            if (StringUtils.hasText(id) && !activeIds.contains(id)) {
                dynamicMapper.deleteById(tableName, id);
            }
        }
    }

    private void loadSubFormData(EntityDataDTO dto) {
        if (dto == null || dto.getId() == null || dto.getEntityCode() == null) {
            return;
        }
        EntityDefinition definition = definitionMapper.findByEntityCode(dto.getEntityCode()).orElse(null);
        if (definition == null) {
            return;
        }
        if (dto.getData() == null) {
            dto.setData(new HashMap<>());
        }
        loadRelationData(definition, dto.getId(), dto.getData(), 1, new HashSet<>());
    }

    private void loadRelationData(EntityDefinition parentDefinition, String parentId, Map<String, Object> target,
                                  int depth, Set<String> path) {
        if (parentDefinition == null || !StringUtils.hasText(parentId) || target == null || depth > MAX_RELATION_DEPTH) {
            return;
        }
        List<EntityRelation> relations = loadRelations(parentDefinition);
        for (EntityRelation relation : relations) {
            String fieldCode = relation.getParentFieldCode();
            if (!StringUtils.hasText(fieldCode)) {
                continue;
            }

            String pathKey = relation.getParentEntityCode() + ":" + fieldCode;
            if (!path.add(pathKey)) {
                continue;
            }

            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())) {
                path.remove(pathKey);
                continue;
            }

            if (!dynamicTableService.tableExists(childDefinition.getEntityCode())) {
                target.put(fieldCode, emptyRelationValue(relation));
                path.remove(pathKey);
                continue;
            }
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            List<Map<String, Object>> rows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            List<Map<String, Object>> childRows = rows == null ? List.of() : rows.stream()
                    .map(row -> {
                        Map<String, Object> childRow = toChildFormRow(row, childDefinition.getEntityCode());
                        String childId = stringValue(childRow.get("id"));
                        loadRelationData(childDefinition, childId, childRow, depth + 1, path);
                        return childRow;
                    })
                    .collect(Collectors.toList());
            if (relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE) {
                target.put(fieldCode, childRows.isEmpty() ? null : childRows.get(0));
            } else {
                target.put(fieldCode, childRows);
            }
            path.remove(pathKey);
        }
    }

    private Object emptyRelationValue(EntityRelation relation) {
        return relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE ? null : List.of();
    }

    private Map<String, Object> toChildFormRow(Map<String, Object> row, String entityCode) {
        EntityDataDTO childDto = convertToDTO(row, entityCode);
        Map<String, Object> data = new HashMap<>();
        if (childDto.getData() != null) {
            data.putAll(childDto.getData());
        }
        if (childDto.getId() != null) {
            data.put("id", childDto.getId());
        }
        return data;
    }

    private void normalizeJsonValues(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map || value instanceof List) {
                try {
                    entry.setValue(objectMapper.writeValueAsString(value));
                } catch (Exception e) {
                    log.warn("字段 {} 序列化 JSON 失败: {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private void cascadeDeleteRelations(EntityDefinition parentDefinition, String parentId, boolean physical,
                                        int depth, Set<String> path) {
        if (parentDefinition == null || !StringUtils.hasText(parentId) || depth > MAX_RELATION_DEPTH) {
            return;
        }
        for (EntityRelation relation : loadRelations(parentDefinition)) {
            if (!Boolean.TRUE.equals(relation.getCascadeDelete()) || !StringUtils.hasText(relation.getParentFieldCode())) {
                continue;
            }
            String pathKey = relation.getParentEntityCode() + ":" + relation.getParentFieldCode();
            if (!path.add(pathKey)) {
                continue;
            }
            EntityDefinition childDefinition = loadChildEntity(relation);
            if (childDefinition == null || !StringUtils.hasText(childDefinition.getEntityCode())
                    || !StringUtils.hasText(relation.getChildRefFieldCode())
                    || !dynamicTableService.tableExists(childDefinition.getEntityCode())) {
                path.remove(pathKey);
                continue;
            }
            String childTableName = dynamicTableService.getTableName(childDefinition.getEntityCode());
            List<Map<String, Object>> childRows = findRowsByReference(childTableName, relation.getChildRefFieldCode(), parentId);
            for (Map<String, Object> childRow : childRows) {
                String childId = stringValue(childRow.get("id"));
                if (!StringUtils.hasText(childId)) {
                    continue;
                }
                cascadeDeleteRelations(childDefinition, childId, physical, depth + 1, path);
                if (physical) {
                    dynamicMapper.physicalDeleteById(childTableName, childId);
                } else {
                    dynamicMapper.deleteById(childTableName, childId);
                }
            }
            path.remove(pathKey);
        }
    }

    /**
     * 删除数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String entityCode, String id) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        cascadeDeleteRelations(definition, id, false, 1, new HashSet<>());
        String tableName = dynamicTableService.getTableName(entityCode);
        dynamicMapper.deleteById(tableName, id);
    }

    /**
     * 物理删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void physicalDelete(String entityCode, String id) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        cascadeDeleteRelations(definition, id, true, 1, new HashSet<>());
        String tableName = dynamicTableService.getTableName(entityCode);
        dynamicMapper.physicalDeleteById(tableName, id);
    }

    /**
     * 条件查询（带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByCondition(String entityCode, Map<String, Object> condition) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode);

        List<Map<String, Object>> dataList;
        if (!permission.isHasPermission()) {
            dataList = new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            dataList = dynamicMapper.selectByCondition(tableName, condition);
        } else {
            dataList = dynamicMapper.selectByConditionWithPermission(tableName, condition, permission.getSqlCondition());
        }

        return dataList.stream()
                .map(data -> convertToDTO(data, entityCode))
                .collect(Collectors.toList());
    }

    /**
     * 统计数量（带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public long count(String entityCode) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode);

        if (!permission.isHasPermission()) {
            return 0;
        } else if (!permission.isNeedFilter()) {
            return dynamicMapper.count(tableName);
        } else {
            // 带权限条件的统计：使用原生SQL执行
            String sql = "SELECT COUNT(*) FROM " + tableName +
                    " WHERE deleted = 0 AND (" + permission.getSqlCondition() + ")";
            List<Map<String, Object>> result = dynamicMapper.executeQuery(sql);
            if (result != null && !result.isEmpty()) {
                Object count = result.get(0).values().iterator().next();
                return count != null ? Long.parseLong(count.toString()) : 0;
            }
            return 0;
        }
    }

    // ============ 私有方法 ============

    /**
     * 发起流程
     */
    private void startProcess(EntityDataDTO dto, EntityDefinition definition, Map<String, Object> data) {
        String processConfigId = definition.getProcessDefinitionId();
        if (processConfigId == null || processConfigId.isEmpty()) {
            throw new RuntimeException("实体未绑定流程定义");
        }
        
        // 获取流程配置，获取流程key
        com.workflow.entity.ProcessDefinitionConfig processConfig = 
                processDefinitionConfigMapper.selectById(processConfigId);
        if (processConfig == null) {
            throw new RuntimeException("流程定义不存在: " + processConfigId);
        }
        if (processConfig.getStatus() == com.workflow.entity.ProcessDefinitionConfig.ProcessStatus.DISABLED) {
            throw new RuntimeException("流程已禁用，无法发起: " + processConfig.getProcessName());
        }
        
        String processKey = processConfig.getProcessKey();

        // 设置流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("entityCode", dto.getEntityCode());
        variables.put("entityDataId", dto.getId());
        variables.put("dataNo", dto.getDataNo());
        variables.put("submitterId", dto.getSubmitterId());
        variables.put("submitterName", dto.getSubmitterName());
        
        // 设置 skipNodeEnabled 变量，用于支持节点自动跳过功能
        // 如果节点设置了 skipNode=true，且该节点的 skipExpression 为 ${skipNodeEnabled}，
        // 则该节点会被自动跳过
        variables.put("skipNodeEnabled", true);
        
        // 设置 initiator 变量（流程中使用 ${initiator} 表达式）
        if (dto.getSubmitterId() != null) {
            variables.put("initiator", dto.getSubmitterId());
        }

        // 添加表单数据到流程变量
        if (dto.getData() != null) {
            variables.putAll(dto.getData());
        }
        
        // 添加自定义流程变量（如会签人员列表等）
        if (dto.getProcessVariables() != null) {
            variables.putAll(dto.getProcessVariables());
        }

        // 预计算多实例集合变量（根据节点配置的审批人自动计算）
        multiInstanceCollectionListener.prepareVariables(processConfigId, variables);

        // 设置流程发起人
        String submitterId = dto.getSubmitterId();
        if (submitterId != null && !submitterId.isEmpty()) {
            identityService.setAuthenticatedUserId(submitterId);
        }

        // 启动流程（使用流程key）
        ProcessInstance processInstance = runtimeService
                .startProcessInstanceByKey(processKey, dto.getId(), variables);

        // 查询当前活动任务
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();

        // 更新数据表中的流程信息
        String tableName = dynamicTableService.getTableName(dto.getEntityCode());
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", dto.getId());
        // 设置流程实例ID
        updateData.put("process_instance_id", processInstance.getId());
        // 设置流程开始时间
        updateData.put("process_start_time", LocalDateTime.now());
        // 设置流程状态（从实体状态配置中获取 PROCESSING 分类的第一个状态）
        String processingStatus = getProcessingStatus(dto.getEntityCode());
        updateData.put("status", processingStatus);
        updateData.put("updated_at", LocalDateTime.now());
        
        // 设置当前任务ID、名称和审批人
        if (currentTask != null) {
            updateData.put("current_task_id", currentTask.getId());
            updateData.put("current_task_name", currentTask.getName());
            updateData.put("current_task_assignee", currentTask.getAssignee());
        }

        dynamicMapper.update(tableName, updateData);
        
        // 更新DTO对象
        dto.setProcessInstanceId(processInstance.getId());
        dto.setStatus(processingStatus);
        if (currentTask != null) {
            dto.setCurrentTaskId(currentTask.getId());
            dto.setCurrentTaskName(currentTask.getName());
            dto.setCurrentTaskAssignee(currentTask.getAssignee());
        }

        // 同步待办任务
        processTaskService.syncTasksFromFlowable(processInstance.getId());

        log.info("实体数据 {} 发起流程 {}，流程实例ID: {}",
                dto.getId(), processKey, processInstance.getId());
    }
    
    /**
     * 更新实体数据的当前任务信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentTask(String entityCode, String entityDataId, String currentTaskId, String currentTaskName, String currentTaskAssignee) {
        String tableName = dynamicTableService.getTableName(entityCode);
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", entityDataId);
        updateData.put("current_task_id", currentTaskId);
        updateData.put("current_task_name", currentTaskName);
        updateData.put("current_task_assignee", currentTaskAssignee);
        updateData.put("updated_at", LocalDateTime.now());
        dynamicMapper.update(tableName, updateData);
        log.debug("更新实体当前任务: entityCode={}, entityDataId={}, taskId={}, taskName={}, assignee={}",
                entityCode, entityDataId, currentTaskId, currentTaskName, currentTaskAssignee);
    }

    /**
     * 转换为 DTO
     */
    private EntityDataDTO convertToDTO(Map<String, Object> data, String entityCode) {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setId(getString(data, "id"));
        dto.setEntityCode(entityCode);
        dto.setDataNo(getString(data, "data_no"));
        dto.setTitle(getString(data, "title"));
        dto.setName(getString(data, "name"));
        dto.setCode(getString(data, "code"));
        dto.setStatus(getString(data, "status"));
        dto.setProcessInstanceId(getString(data, "process_instance_id"));
        dto.setProcessStartTime(getDateTime(data, "process_start_time"));
        dto.setProcessEndTime(getDateTime(data, "process_end_time"));
        dto.setCurrentTaskId(getString(data, "current_task_id"));
        dto.setCurrentTaskName(getString(data, "current_task_name"));
        dto.setCurrentTaskAssignee(getString(data, "current_task_assignee"));
        dto.setSubmitterId(getString(data, "submitter_id"));
        dto.setSubmitterName(getString(data, "submitter_name"));
        dto.setDeptId(getString(data, "dept_id"));
        dto.setSubmitTime(getDateTime(data, "submit_time"));
        dto.setCreatedAt(getDateTime(data, "created_at"));
        dto.setUpdatedAt(getDateTime(data, "updated_at"));
        dto.setCreatedBy(getString(data, "created_by"));
        dto.setUpdatedBy(getString(data, "updated_by"));

        // 提取自定义字段
        Map<String, Object> customData = extractCustomFields(data, entityCode);
        dto.setData(customData);

        // 初始化扩展数据容器
        dto.setExtData(new java.util.HashMap<>());

        return dto;
    }

    /**
     * 转换为 Map
     */
    private Map<String, Object> convertToMap(EntityDataDTO dto) {
        Map<String, Object> data = new HashMap<>();

        if (dto.getId() != null) {
            data.put("id", dto.getId());
        }
        if (dto.getDataNo() != null) {
            data.put("data_no", dto.getDataNo());
        }
        if (dto.getTitle() != null) {
            data.put("title", dto.getTitle());
        }
        if (dto.getName() != null) {
            data.put("name", dto.getName());
        }
        if (dto.getCode() != null) {
            data.put("code", dto.getCode());
        }
        if (dto.getStatus() != null) {
            data.put("status", dto.getStatus());
        }
        if (dto.getProcessInstanceId() != null) {
            data.put("process_instance_id", dto.getProcessInstanceId());
        }
        if (dto.getProcessStartTime() != null) {
            data.put("process_start_time", dto.getProcessStartTime());
        }
        if (dto.getProcessEndTime() != null) {
            data.put("process_end_time", dto.getProcessEndTime());
        }
        if (dto.getCurrentTaskId() != null) {
            data.put("current_task_id", dto.getCurrentTaskId());
        }
        if (dto.getCurrentTaskName() != null) {
            data.put("current_task_name", dto.getCurrentTaskName());
        }
        if (dto.getCurrentTaskAssignee() != null) {
            data.put("current_task_assignee", dto.getCurrentTaskAssignee());
        }
        if (dto.getSubmitterId() != null) {
            data.put("submitter_id", dto.getSubmitterId());
        }
        if (dto.getSubmitterName() != null) {
            data.put("submitter_name", dto.getSubmitterName());
        }
        if (dto.getDeptId() != null) {
            data.put("dept_id", dto.getDeptId());
        }
        if (dto.getSubmitTime() != null) {
            data.put("submit_time", dto.getSubmitTime());
        }

        // 添加自定义字段（排除系统字段，避免重复）
        if (dto.getData() != null) {
            // 系统字段集合（驼峰和下划线命名都要排除）
            Set<String> systemFields = new HashSet<>(Arrays.asList(
                    "id", "dataNo", "data_no", "title", "name", "code", "status",
                    "processInstanceId", "process_instance_id", 
                    "processStartTime", "process_start_time",
                    "processEndTime", "process_end_time",
                    "currentTaskId", "current_task_id",
                    "currentTaskName", "current_task_name",
                    "submitterId", "submitter_id",
                    "submitterName", "submitter_name",
                    "deptId", "dept_id",
                    "submitTime", "submit_time",
                    "createdAt", "created_at", "updatedAt", "updated_at",
                    "createdBy", "created_by", "updatedBy", "updated_by",
                    "deleted"
            ));
            
            for (Map.Entry<String, Object> entry : dto.getData().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                // 过滤无效的字段名
                if (key == null || key.isEmpty() || "undefined".equals(key) || "null".equals(key)) {
                    continue;
                }
                if (!systemFields.contains(key)) {
                    // 将字段名转换为下划线命名
                    String columnName = camelToUnderscore(key);
                    // 将空字符串转为 null，避免唯一约束冲突
                    if (value instanceof String && ((String) value).isEmpty()) {
                        value = null;
                    }
                    // 复杂对象（Map/List）序列化为 JSON 字符串，避免 MyBatis 默认 Java 序列化
                    if (value instanceof Map || value instanceof List) {
                        try {
                            value = objectMapper.writeValueAsString(value);
                        } catch (Exception e) {
                            log.warn("字段 {} 序列化 JSON 失败: {}", key, e.getMessage());
                        }
                    }
                    data.put(columnName, value);
                }
            }
        }

        return data;
    }
    
    /**
     * 驼峰命名转换为下划线命名
     */
    private String camelToUnderscore(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append("_").append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 提取自定义字段（排除系统字段，列名下划线转驼峰）
     */
    private Map<String, Object> extractCustomFields(Map<String, Object> data, String entityCode) {
        // 系统字段集合
        Set<String> systemFields = new HashSet<>(Arrays.asList(
                "id", "data_no", "title", "name", "code", "status",
                "process_instance_id", "process_start_time", "process_end_time",
                "current_task_id", "current_task_name", "current_task_assignee",
                "submitter_id", "submitter_name", "submit_time",
                "created_at", "updated_at", "created_by", "updated_by", "deleted"
        ));

        Map<String, Object> customData = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!systemFields.contains(entry.getKey())) {
                String camelKey = underscoreToCamel(entry.getKey());
                Object value = entry.getValue();
                // 尝试将 JSON 字符串反序列化为对象（Map/List），便于前端直接使用
                if (value instanceof String) {
                    String str = (String) value;
                    if (str.trim().startsWith("{") || str.trim().startsWith("[")) {
                        try {
                            value = objectMapper.readValue(str, Object.class);
                        } catch (Exception e) {
                            // 不是有效 JSON，保持原字符串
                        }
                    }
                }
                customData.put(camelKey, value);
            }
        }
        return customData;
    }

    /**
     * 下划线命名转驼峰命名
     */
    private String underscoreToCamel(String underscore) {
        if (underscore == null || underscore.isEmpty()) {
            return underscore;
        }
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < underscore.length(); i++) {
            char c = underscore.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                result.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private LocalDateTime getDateTime(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        return null;
    }

    private String getCurrentUserId(String defaultValue) {
        if (defaultValue != null && !defaultValue.isEmpty()) {
            return defaultValue;
        }
        String userId = UserContext.getUserId();
        return userId != null ? userId : "system";
    }

    private String getCurrentUserName(String defaultValue) {
        if (defaultValue != null && !defaultValue.isEmpty()) {
            return defaultValue;
        }
        String userName = UserContext.getUsername();
        return userName != null ? userName : "系统";
    }

    /**
     * 获取当前用户部门ID
     */
    private String getCurrentDeptId() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserService.getById(userId);
        return user != null ? user.getDeptId() : null;
    }

    /**
     * 获取当前登录用户完整信息（用于数据权限计算）
     */
    private SysUser getCurrentSysUser() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        return sysUserService.getById(userId);
    }

    /**
     * 计算数据权限结果
     */
    private DataPermissionResult getDataPermission(String entityCode) {
        SysUser user = getCurrentSysUser();
        if (user == null) {
            // 未登录用户，默认仅本人（实际上看不到任何数据，因为没有用户ID匹配）
            return DataPermissionResult.withCondition("created_by = ''");
        }
        return dataPermissionEngine.calculatePermission(entityCode, user);
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateDataNo(String entityCode) {
        // 生成业务单号：实体编码前缀 + 时间戳后8位 + 6位随机数
        // 使用纳秒时间戳增加唯一性
        String prefix = entityCode.toUpperCase();
        String timestamp = String.valueOf(System.nanoTime());
        String timePart = timestamp.substring(Math.max(0, timestamp.length() - 8));
        String random = String.format("%06d", (int) (Math.random() * 1000000));
        return prefix + "-" + timePart + random;
    }

    /**
     * 获取实体的默认状态（NEW分类的第一个状态）
     */
    private String getDefaultStatus(String entityCode) {
        try {
            List<EntityStatus> statuses = entityStatusMapper.findByCategory(entityCode, "NEW");
            if (statuses != null && !statuses.isEmpty()) {
                // 返回排序号最小的状态
                return statuses.get(0).getStatusCode();
            }
        } catch (Exception e) {
            log.warn("获取实体[{}]默认状态失败: {}", entityCode, e.getMessage());
        }
        // 默认返回 DRAFT
        return "DRAFT";
    }

    /**
     * 获取实体的流程中状态（PROCESSING分类的第一个状态）
     */
    private String getProcessingStatus(String entityCode) {
        try {
            List<EntityStatus> statuses = entityStatusMapper.findByCategory(entityCode, "PROCESSING");
            if (statuses != null && !statuses.isEmpty()) {
                // 返回排序号最小的状态
                return statuses.get(0).getStatusCode();
            }
        } catch (Exception e) {
            log.warn("获取实体[{}]流程中状态失败: {}", entityCode, e.getMessage());
        }
        // 默认返回 PENDING
        return "PENDING";
    }
}
