package com.workflow.service;

import com.workflow.common.UserContext;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.common.BusinessConflictException;
import com.workflow.common.ForbiddenException;
import com.workflow.common.PageResult;
import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.DataPermissionResult;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityRelation;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.SysUser;
import com.workflow.entity.publish.EntityPublishedSnapshot;
import com.workflow.entity.publish.EntityPublishedSnapshotService;
import com.workflow.entity.runtime.EntityRelationRuntimeService;
import com.workflow.entity.runtime.EntityMultiValueRuntimeService;
import com.workflow.entity.runtime.EntityRuntimeRecordMapper;
import com.workflow.entity.runtime.EntityWorkflowRuntimePort;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.service.permission.DataPermissionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityStatusMapper entityStatusMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityCodeGeneratorService codeGeneratorService;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityRelationRuntimeService relationRuntimeService;
    private final EntityMultiValueRuntimeService multiValueRuntimeService;
    private final EntityWorkflowRuntimePort workflowRuntimeService;
    private final DataPermissionEngine dataPermissionEngine;
    private final SysUserService sysUserService;
    private final EntityPublishedSnapshotService snapshotService;
    private final EntityRecordTeamService entityRecordTeamService;

    /**
     * 查询某实体的所有数据（带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByEntityCode(String entityCode) {
        return findByEntityCode(entityCode, null);
    }

    /**
     * 查询某实体列表的所有数据（带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByEntityCode(String entityCode, String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode, listKey);

        List<Map<String, Object>> dataList;
        if (!permission.isHasPermission()) {
            dataList = new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            dataList = dynamicMapper.selectList(tableName);
        } else {
            dataList = dynamicMapper.selectListWithPermission(tableName, permission.getSqlCondition());
        }

        List<EntityDataDTO> records = dataList.stream()
                .map(data -> recordMapper.toDto(data, entityCode))
                .collect(Collectors.toList());
        enrichMultiValues(entityCode, records);
        return records;
    }

    /**
     * 查询某实体的所有数据（返回原始Map，用于选择器，带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByEntityCodeSimple(String entityCode) {
        return findByEntityCodeSimple(entityCode, null);
    }

    /**
     * 查询某实体列表的所有数据（返回原始Map，用于选择器，带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByEntityCodeSimple(String entityCode, String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode, listKey);

        if (!permission.isHasPermission()) {
            return new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            return dynamicMapper.selectList(tableName);
        } else {
            return dynamicMapper.selectListWithPermission(tableName, permission.getSqlCondition());
        }
    }

    /**
     * 分页查询实体数据，基于当前用户上下文计算数据权限。
     *
     * @param entityCode        实体编码
     * @param listKey          列表编码
     * @param condition        查询条件
     * @param requestedPageNum 请求页码
     * @param requestedPageSize 请求每页数量
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPage(
            String entityCode,
            String listKey,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize) {
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                getDataPermission(entityCode, listKey));
    }

    /**
     * 分页查询实体数据，基于指定用户计算数据权限。
     *
     * @param entityCode        实体编码
     * @param listKey          列表编码
     * @param condition        查询条件
     * @param requestedPageNum 请求页码
     * @param requestedPageSize 请求每页数量
     * @param user             用于权限计算的用户
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPageForUser(
            String entityCode,
            String listKey,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            SysUser user) {
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                dataPermissionEngine.calculatePermission(entityCode, listKey, user));
    }

    /**
     * 分页查询实体数据，使用外部传入的数据范围计划（用于可信服务端链路）。
     *
     * @param entityCode        实体编码
     * @param condition        查询条件
     * @param requestedPageNum 请求页码
     * @param requestedPageSize 请求每页数量
     * @param plan             数据范围权限计划
     * @return 分页结果
     * @throws IllegalArgumentException 数据权限计划为空时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPageWithDataScopePlan(
            String entityCode,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            DataScopePlan plan) {
        if (plan == null || plan.sqlFragment() == null
                || plan.sqlFragment().isBlank()) {
            throw new IllegalArgumentException("数据权限计划不能为空");
        }
        DataPermissionResult permission = plan.allowed()
                ? ("1=1".equals(plan.sqlFragment())
                        ? DataPermissionResult.allowAll()
                        : DataPermissionResult.withCondition(
                                plan.sqlFragment()))
                : DataPermissionResult.denyAll();
        permission.setMatchedRuleNames(plan.matchedPolicies());
        permission.setReleaseVersion(plan.releaseVersion());
        permission.setExplanation(plan.explanation());
        return findPageWithPermission(
                entityCode,
                condition,
                requestedPageNum,
                requestedPageSize,
                permission);
    }

    private PageResult<EntityDataDTO> findPageWithPermission(
            String entityCode,
            Map<String, Object> condition,
            long requestedPageNum,
            long requestedPageSize,
            DataPermissionResult permission) {
        long pageNum = Math.max(1, requestedPageNum);
        long pageSize = Math.max(1, Math.min(200, requestedPageSize));
        long offset = (pageNum - 1) * pageSize;
        String tableName = dynamicTableService.getTableName(entityCode);
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new RuntimeException("实体不存在: " + entityCode));
        EntityMultiValueRuntimeService.PreparedConditions prepared =
                multiValueRuntimeService.prepareConditions(definition, condition);
        condition = prepared.condition();
        permission.intersect(prepared.sqlCondition());
        if (!permission.isHasPermission()) {
            return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }

        boolean hasCondition = condition != null && !condition.isEmpty();
        long total;
        List<Map<String, Object>> rows;
        if (hasCondition) {
            if (permission.isNeedFilter()) {
                total = dynamicMapper.countByConditionWithPermission(
                        tableName,
                        condition,
                        permission.getSqlCondition());
                rows = dynamicMapper.selectPageByConditionWithPermission(
                        tableName,
                        condition,
                        permission.getSqlCondition(),
                        offset,
                        pageSize);
            } else {
                total = dynamicMapper.countByCondition(tableName, condition);
                rows = dynamicMapper.selectPageByCondition(
                        tableName,
                        condition,
                        offset,
                        pageSize);
            }
        } else if (permission.isNeedFilter()) {
            total = dynamicMapper.countWithPermission(
                    tableName,
                    permission.getSqlCondition());
            rows = dynamicMapper.selectPageWithPermission(
                    tableName,
                    permission.getSqlCondition(),
                    offset,
                    pageSize);
        } else {
            total = dynamicMapper.count(tableName);
            rows = dynamicMapper.selectPage(
                    tableName,
                    offset,
                    pageSize);
        }

        List<EntityDataDTO> records = rows.stream()
                .map(data -> recordMapper.toDto(data, entityCode))
                .toList();
        enrichMultiValues(entityCode, records);
        return new PageResult<>(records, total, pageNum, pageSize);
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
        EntityDataDTO dto = recordMapper.toDto(data, entityCode);
        enrichMultiValues(entityCode, List.of(dto));
        relationRuntimeService.loadRelationData(dto);
        return dto;
    }

    /**
     * 按列表数据权限查询单条数据。
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findAccessibleById(String entityCode, String id, String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode, listKey);
        if (!permission.isHasPermission()) {
            throw new ForbiddenException("数据不存在或无权访问");
        }
        Map<String, Object> data = permission.isNeedFilter()
                ? dynamicMapper.selectByIdWithPermission(tableName, id, permission.getSqlCondition())
                : dynamicMapper.selectById(tableName, id);
        if (data == null) {
            throw new ForbiddenException("数据不存在或不在当前数据权限范围内");
        }
        EntityDataDTO dto = recordMapper.toDto(data, entityCode);
        enrichMultiValues(entityCode, List.of(dto));
        relationRuntimeService.loadRelationData(dto);
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
        EntityDataDTO dto = recordMapper.toDto(data, entityCode);
        enrichMultiValues(entityCode, List.of(dto));
        relationRuntimeService.loadRelationData(dto);
        return dto;
    }

    /**
     * 按数据权限查询流程实例关联的实体数据。
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findAccessibleByProcessInstanceId(
            String entityCode,
            String processInstanceId,
            String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        Map<String, Object> data = dynamicMapper.selectByProcessInstanceId(tableName, processInstanceId);
        if (data == null || data.get("id") == null) {
            throw new ForbiddenException("数据不存在或无权访问");
        }
        return findAccessibleById(
                entityCode,
                String.valueOf(data.get("id")),
                listKey);
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
        List<EntityRelation> relations = relationRuntimeService.loadRelations(definition);
        Map<String, Object> originalData = dto.getData();
        Map<String, Object> parentData = relationRuntimeService.withoutRelationData(originalData, relations);
        Map<String, Object> relationData = relationRuntimeService.extractRelationData(originalData, relations);
        Map<String, List<String>> multiValueData =
                multiValueRuntimeService.extractConfiguredValues(definition, parentData);
        multiValueRuntimeService.validateScalarDictValues(definition, parentData);

        // 验证实体是否启用流程
        if (Boolean.TRUE.equals(dto.getStartProcess()) &&
                definition.getLifecycleMode() != EntityDefinition.LifecycleMode.WORKFLOW) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_SUPPORTED",
                    "独立业务实体不支持发起流程");
        }
        if (Boolean.TRUE.equals(dto.getStartProcess())
                && !org.springframework.util.StringUtils.hasText(definition.getProcessDefinitionId())) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "流程实体尚未绑定流程，不能发起");
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
        Map<String, Object> data = recordMapper.toStorageMap(dto);
        dto.setData(originalData);
        validatePublishedRequiredFields(entityCode, data);
        validatePublishedUniqueFields(entityCode, data, data.get("id") != null ? data.get("id").toString() : null);

        if (dto.getId() == null || dto.getId().isEmpty()) {
            // ========== 新增数据 ==========
            String id = generateId();
            data.put("id", id);
            data.put("create_by", currentUserId);
            data.put("create_time", LocalDateTime.now());
            data.put("update_by", currentUserId);
            data.put("update_time", LocalDateTime.now());
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
            if (definition.getLifecycleMode() == EntityDefinition.LifecycleMode.WORKFLOW) {
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
            entityRecordTeamService.record(
                    entityCode, id, "CREATE", "创建数据", null, null);
        } else {
            // ========== 更新数据 ==========
            // 更新时不能修改提交人
            data.put("update_by", currentUserId);
            data.put("update_time", LocalDateTime.now());
            
            // 移除可能传入的提交人字段，防止被修改
            data.remove("submitter_id");
            data.remove("submitter_name");
            
            dynamicMapper.update(tableName, data);
            entityRecordTeamService.record(
                    entityCode, dto.getId(), "EDIT", "编辑数据",
                    dto.getProcessInstanceId(), dto.getCurrentTaskId());
        }

        relationRuntimeService.saveRelationData(dto.getId(), relations, relationData);
        multiValueRuntimeService.save(definition, dto.getId(), multiValueData);

        // 如果需要发起流程
        if (Boolean.TRUE.equals(dto.getStartProcess()) &&
                definition.getProcessDefinitionId() != null) {
            workflowRuntimeService.startProcess(dto, definition);
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
        List<EntityRelation> relations = relationRuntimeService.loadRelations(definition);
        Map<String, Object> parentFormData = relationRuntimeService.withoutRelationDataFromRequest(formData, relations);
        Map<String, Object> relationData = relationRuntimeService.extractRelationDataFromRequest(formData, relations);
        Map<String, Object> multiValueSource = requestCustomData(parentFormData);
        Map<String, List<String>> multiValueData =
                multiValueRuntimeService.extractConfiguredValues(definition, multiValueSource);
        multiValueRuntimeService.validateScalarDictValues(definition, multiValueSource);
        removeMultiValuesFromRequest(parentFormData, multiValueData.keySet());

        // 查询原数据
        Map<String, Object> existingData = dynamicMapper.selectById(tableName, id);
        if (existingData == null) {
            throw new RuntimeException("数据不存在: " + id);
        }

        // 构建更新数据：以 existingData 的列为基础（确保不更新不存在的列）
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", id);
        updateData.put("update_by", UserContext.getUserId());
        updateData.put("update_time", LocalDateTime.now());

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

        // 1. 从 formData 顶层或 formData.data 中提取标准字段
        Object dataObj = parentFormData.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> customData = (dataObj instanceof Map) ? (Map<String, Object>) dataObj : null;
        standardFieldMap.forEach((frontendKey, dbKey) -> {
            Object value = null;
            boolean hasValue = false;
            if (parentFormData.containsKey(frontendKey)) {
                value = parentFormData.get(frontendKey);
                hasValue = true;
            } else if (customData != null && customData.containsKey(frontendKey)) {
                value = customData.get(frontendKey);
                hasValue = true;
            }
            if (hasValue) {
                if (value instanceof String && ((String) value).isEmpty()) {
                    value = null;
                }
                updateData.put(dbKey, value);
            }
        });

        // 2. 从 formData.data 提取自定义字段
        updateData.putAll(recordMapper.extractRequestCustomData(parentFormData));

        // 3. 补充 existingData 中缺失的字段（保持原有值）
        existingData.forEach((key, value) -> {
            if (!updateData.containsKey(key)) {
                updateData.put(key, value);
            }
        });
        validatePublishedRequiredFields(entityCode, updateData);
        validatePublishedUniqueFields(entityCode, updateData, id);

        dynamicMapper.update(tableName, updateData);
        entityRecordTeamService.record(
                entityCode, id, "EDIT", "编辑数据",
                asText(existingData.get("process_instance_id")),
                asText(existingData.get("current_task_id")));
        relationRuntimeService.saveRelationData(id, relations, relationData);
        multiValueRuntimeService.save(definition, id, multiValueData);

        EntityDataDTO dto = recordMapper.toDto(updateData, entityCode);
        enrichMultiValues(entityCode, List.of(dto));
        if (dto.getData() != null) {
            dto.getData().putAll(relationData);
        }

        // 编辑时发起流程：如果请求要求发起流程且数据尚未绑定流程实例
        Object startProcessObj = formData.get("startProcess");
        boolean startProcess = Boolean.TRUE.equals(startProcessObj) || "true".equalsIgnoreCase(String.valueOf(startProcessObj));
        if (startProcess) {
            if (definition.getLifecycleMode() != EntityDefinition.LifecycleMode.WORKFLOW) {
                throw new BusinessConflictException(
                        "ENTITY_WORKFLOW_NOT_SUPPORTED",
                        "独立业务实体不支持发起流程");
            }
            if (!StringUtils.hasText(definition.getProcessDefinitionId())) {
                throw new BusinessConflictException(
                        "ENTITY_WORKFLOW_NOT_READY",
                        "流程实体尚未绑定流程，不能发起");
            }
            Object existingProcessInstanceIdObj = existingData.get("process_instance_id");
            String existingProcessInstanceId = existingProcessInstanceIdObj != null ? existingProcessInstanceIdObj.toString() : null;
            if (existingProcessInstanceId == null || existingProcessInstanceId.isEmpty()) {
                dto.setStartProcess(true);
                dto.setEntityCode(entityCode);
                Object submitterIdObj = existingData.get("submitter_id");
                Object submitterNameObj = existingData.get("submitter_name");
                dto.setSubmitterId(submitterIdObj != null ? submitterIdObj.toString() : null);
                dto.setSubmitterName(submitterNameObj != null ? submitterNameObj.toString() : null);
                dto.setProcessVariables(null);
                workflowRuntimeService.startProcess(dto, definition);
                // 重新加载最新数据返回
                Map<String, Object> refreshedData = dynamicMapper.selectById(tableName, id);
                dto = recordMapper.toDto(refreshedData, entityCode);
                enrichMultiValues(entityCode, List.of(dto));
                if (dto.getData() != null) {
                    dto.getData().putAll(relationData);
                }
            }
        }

        return dto;
    }

    /**
     * 删除数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String entityCode, String id) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        relationRuntimeService.cascadeDeleteRelations(definition, id, false);
        multiValueRuntimeService.delete(entityCode, id);
        String tableName = dynamicTableService.getTableName(entityCode);
        dynamicMapper.deleteById(tableName, id);
        entityRecordTeamService.record(
                entityCode, id, "DELETE", "删除数据", null, null);
    }

    /**
     * 物理删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void physicalDelete(String entityCode, String id) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        relationRuntimeService.cascadeDeleteRelations(definition, id, true);
        multiValueRuntimeService.delete(entityCode, id);
        String tableName = dynamicTableService.getTableName(entityCode);
        dynamicMapper.physicalDeleteById(tableName, id);
    }

    /**
     * 条件查询（带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByCondition(String entityCode, Map<String, Object> condition) {
        return findByCondition(entityCode, null, condition);
    }

    /**
     * 条件查询（带数据权限过滤，支持列表配置）
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByCondition(String entityCode, String listKey, Map<String, Object> condition) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode, listKey);
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new RuntimeException("实体不存在: " + entityCode));
        EntityMultiValueRuntimeService.PreparedConditions prepared =
                multiValueRuntimeService.prepareConditions(definition, condition);
        condition = prepared.condition();
        permission.intersect(prepared.sqlCondition());

        List<Map<String, Object>> dataList;
        if (!permission.isHasPermission()) {
            dataList = new ArrayList<>();
        } else if (!permission.isNeedFilter()) {
            dataList = dynamicMapper.selectByCondition(tableName, condition);
        } else {
            dataList = dynamicMapper.selectByConditionWithPermission(tableName, condition, permission.getSqlCondition());
        }

        List<EntityDataDTO> records = dataList.stream()
                .map(data -> recordMapper.toDto(data, entityCode))
                .collect(Collectors.toList());
        enrichMultiValues(entityCode, records);
        return records;
    }

    /**
     * 统计数量（带数据权限过滤）
     */
    @Transactional(readOnly = true)
    public long count(String entityCode) {
        return count(entityCode, null);
    }

    /**
     * 统计数量（带数据权限过滤，支持列表配置）
     */
    @Transactional(readOnly = true)
    public long count(String entityCode, String listKey) {
        String tableName = dynamicTableService.getTableName(entityCode);
        DataPermissionResult permission = getDataPermission(entityCode, listKey);

        if (!permission.isHasPermission()) {
            return 0;
        } else if (!permission.isNeedFilter()) {
            return dynamicMapper.count(tableName);
        } else {
            return dynamicMapper.countWithPermission(tableName, permission.getSqlCondition());
        }
    }

    /**
     * 更新实体数据的当前任务信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentTask(String entityCode, String entityDataId, String currentTaskId, String currentTaskName, String currentTaskAssignee) {
        workflowRuntimeService.updateCurrentTask(entityCode, entityDataId, currentTaskId, currentTaskName, currentTaskAssignee);
    }

    /**
     * 标记实体流程已撤回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markWithdrawn(String entityCode, String entityDataId) {
        String tableName = dynamicTableService.getTableName(entityCode);
        List<EntityStatus> statuses = entityStatusMapper.findByCategory(entityCode, "WITHDRAWN");
        String statusCode = statuses == null || statuses.isEmpty()
                ? "WITHDRAWN"
                : statuses.get(0).getStatusCode();
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", entityDataId);
        updateData.put("status", statusCode);
        updateData.put("process_end_time", LocalDateTime.now());
        updateData.put("update_time", LocalDateTime.now());
        dynamicMapper.update(tableName, updateData);
        dynamicMapper.updateCurrentTask(tableName, entityDataId, null, null, null);
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

    private void enrichMultiValues(String entityCode, Collection<EntityDataDTO> records) {
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        if (definition == null || definition.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        multiValueRuntimeService.enrich(definition, records);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestCustomData(Map<String, Object> request) {
        if (request == null) {
            return new HashMap<>();
        }
        Object nested = request.get("data");
        if (nested instanceof Map<?, ?> nestedMap) {
            return new HashMap<>((Map<String, Object>) nestedMap);
        }
        return new HashMap<>(request);
    }

    @SuppressWarnings("unchecked")
    private void removeMultiValuesFromRequest(Map<String, Object> request, Set<String> fieldCodes) {
        if (request == null || fieldCodes == null || fieldCodes.isEmpty()) {
            return;
        }
        for (String fieldCode : fieldCodes) {
            request.remove(fieldCode);
        }
        Object nested = request.get("data");
        if (nested instanceof Map<?, ?> nestedMap) {
            Map<String, Object> customData = (Map<String, Object>) nestedMap;
            for (String fieldCode : fieldCodes) {
                customData.remove(fieldCode);
            }
        }
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
    private DataPermissionResult getDataPermission(String entityCode, String listKey) {
        SysUser user = getCurrentSysUser();
        if (user == null) {
            // 未登录用户，默认仅本人（实际上看不到任何数据，因为没有用户ID匹配）
            return DataPermissionResult.withCondition("create_by = ''");
        }
        return dataPermissionEngine.calculatePermission(entityCode, listKey, user);
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

    private void validatePublishedRequiredFields(String entityCode, Map<String, Object> storageData) {
        EntityPublishedSnapshot snapshot = snapshotService.getLatestByEntityCode(entityCode);
        if (snapshot.getFields() == null || snapshot.getFields().isEmpty()) {
            return;
        }
        for (EntityField field : snapshot.getFields()) {
            if (!Boolean.TRUE.equals(field.getIsRequired()) || isRelationField(field)) {
                continue;
            }
            String columnName = recordMapper.toColumnName(field.getFieldCode());
            Object value = storageData.get(columnName);
            if (isBlankValue(value)) {
                throw new RuntimeException("字段必填: " + field.getFieldName());
            }
        }
    }

    private boolean isRelationField(EntityField field) {
        return field.getFieldType() == EntityField.FieldType.SUB_FORM
                || field.getFieldType() == EntityField.FieldType.SUB_FORM_LIST;
    }

    private void validatePublishedUniqueFields(String entityCode, Map<String, Object> storageData, String excludeId) {
        EntityPublishedSnapshot snapshot = snapshotService.getLatestByEntityCode(entityCode);
        if (snapshot.getFields() == null || snapshot.getFields().isEmpty()) {
            return;
        }
        String tableName = dynamicTableService.getTableName(entityCode);
        for (EntityField field : snapshot.getFields()) {
            if (!Boolean.TRUE.equals(field.getIsUnique()) || isRelationField(field)) {
                continue;
            }
            String columnName = recordMapper.toColumnName(field.getFieldCode());
            Object value = storageData.get(columnName);
            if (isBlankValue(value)) {
                continue;
            }
            Map<String, Object> condition = new HashMap<>();
            condition.put(columnName, value);
            condition.put(columnName + "_op", "EQ");
            if (excludeId != null && !excludeId.isEmpty()) {
                condition.put("id", excludeId);
                condition.put("id_op", "NE");
            }
            long count = dynamicMapper.countByCondition(tableName, condition);
            if (count > 0) {
                throw new RuntimeException("字段值已存在: " + field.getFieldName());
            }
        }
    }

    private boolean isBlankValue(Object value) {
        return value == null || (value instanceof String str && str.isBlank());
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
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

}
