package com.workflow.entity.data.application;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.process.port.ProcessRuntimePort;
import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.contracts.process.ProcessStartResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.logging.LogValue;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.domain.policy.EntityProcessStatusPolicy;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 动态实体聚合的内部写入实现，只允许统一变更管道调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDataMutationService {

    private final EntityDataDynamicMapper dynamicMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityStatusMapper entityStatusMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityCodeGeneratorService codeGeneratorService;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityRelationRuntimeService relationRuntimeService;
    private final EntityMultiValueRuntimeService multiValueRuntimeService;
    private final ProcessRuntimePort processRuntimePort;
    private final SysUserService sysUserService;
    private final EntityPublishedSnapshotService snapshotService;
    private final EntityRecordTeamService entityRecordTeamService;
    private final EntityDataMutationValidator validator;
    private final EntityDataMutationPayloadMapper payloadMapper;
    @Autowired(required = false)
    private EntityUniqueValueService uniqueValueService;

    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO save(EntityDataDTO dto) {
        return save(dto, null);
    }

    /** 统一变更入口写入时显式下传不可伪造的递归唯一性计划。 */
    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO save(
            EntityDataDTO dto,
            PreparedUniqueClaims prepared) {
        log.info(
                "保存数据: entityCode={}, id={}, fieldCount={}",
                LogValue.safe(dto.getEntityCode()),
                LogValue.safe(dto.getId()),
                dto.getData() == null ? 0 : dto.getData().size());
        String entityCode = dto.getEntityCode();
        EntityDefinition definition =
                definitionMapper.findByEntityCode(entityCode)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "实体不存在: " + entityCode));
        List<EntityRelation> relations =
                relationRuntimeService.loadRelations(definition);
        Map<String, Object> originalData = dto.getData();
        Map<String, Object> parentData =
                relationRuntimeService.withoutRelationData(
                        originalData,
                        relations);
        Map<String, Object> relationData =
                relationRuntimeService.extractRelationData(
                        originalData,
                        relations);
        Map<String, List<String>> multiValueData =
                multiValueRuntimeService.extractConfiguredValues(
                        definition,
                        parentData);
        multiValueRuntimeService.validateScalarDictValues(
                definition,
                parentData);
        validator.validateProcessStart(
                Boolean.TRUE.equals(dto.getStartProcess()),
                definition);

        String tableName =
                dynamicTableService.getTableName(entityCode);
        if (!dynamicTableService.tableExists(entityCode)) {
            dynamicTableService.createEntityTable(definition);
        }
        String currentUserId =
                getCurrentUserId(dto.getSubmitterId());
        String currentUserName =
                getCurrentUserName(dto.getSubmitterName());

        boolean creating = !StringUtils.hasText(dto.getId());
        // 创建场景必须在唯一值预留前生成稳定ID，确保预留和业务写入属于同一事务主体。
        if (creating) {
            dto.setId(generateId());
        }
        dto.setData(parentData);
        Map<String, Object> data =
                recordMapper.toStorageMap(dto);
        dto.setData(originalData);
        validator.validatePublishedFields(
                entityCode,
                data,
                data.get("id") == null
                        ? null
                        : String.valueOf(data.get("id")));
        relationRuntimeService.validateSelfRelationWrite(
                definition,
                dto.getId(),
                data,
                creating);

        if (creating) {
            insert(
                    dto,
                    tableName,
                    data,
                    currentUserId,
                    currentUserName);
        } else {
            data.put("update_by", currentUserId);
            data.put("update_time", LocalDateTime.now());
            data.remove("submitter_id");
            data.remove("submitter_name");
            dynamicMapper.update(tableName, data);
            entityRecordTeamService.record(
                    entityCode,
                    dto.getId(),
                    "EDIT",
                    "编辑数据",
                    dto.getProcessInstanceId(),
                    dto.getCurrentTaskId());
        }

        relationRuntimeService.saveRelationData(
                dto.getId(),
                relations,
                relationData,
                prepared);
        multiValueRuntimeService.save(
                definition,
                dto.getId(),
                multiValueData);
        if (Boolean.TRUE.equals(dto.getStartProcess())
                && definition.getProcessDefinitionId() != null) {
            startWorkflow(dto);
        }
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO update(
            String entityCode,
            String id,
            Map<String, Object> formData) {
        return update(entityCode, id, formData, null);
    }

    /** 统一变更入口更新时显式下传不可伪造的递归唯一性计划。 */
    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO update(
            String entityCode,
            String id,
            Map<String, Object> formData,
            PreparedUniqueClaims prepared) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        EntityDefinition definition =
                definitionMapper.findByEntityCode(entityCode)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "实体不存在: " + entityCode));
        List<EntityRelation> relations =
                relationRuntimeService.loadRelations(definition);
        Map<String, Object> parentFormData =
                relationRuntimeService
                        .withoutRelationDataFromRequest(
                                formData,
                                relations);
        Map<String, Object> relationData =
                relationRuntimeService
                        .extractRelationDataFromRequest(
                                formData,
                                relations);
        Map<String, Object> multiValueSource =
                payloadMapper.requestCustomData(
                        parentFormData);
        Map<String, List<String>> multiValueData =
                multiValueRuntimeService.extractConfiguredValues(
                        definition,
                        multiValueSource);
        multiValueRuntimeService.validateScalarDictValues(
                definition,
                multiValueSource);
        payloadMapper.removeFields(
                parentFormData,
                multiValueData.keySet());

        Map<String, Object> existingData =
                dynamicMapper.selectById(tableName, id);
        if (existingData == null) {
            throw new RuntimeException(
                    "数据不存在: " + id);
        }
        Map<String, Object> updateData =
                payloadMapper.buildUpdateData(
                        entityCode,
                        id,
                        parentFormData,
                        existingData);
        validator.validatePublishedFields(
                entityCode,
                updateData,
                id);
        relationRuntimeService.validateSelfRelationWrite(
                definition,
                id,
                updateData,
                false);

        dynamicMapper.update(tableName, updateData);
        entityRecordTeamService.record(
                entityCode,
                id,
                "EDIT",
                "编辑数据",
                asText(existingData.get(
                        "process_instance_id")),
                asText(existingData.get(
                        "current_task_id")));
        relationRuntimeService.saveRelationData(
                id,
                relations,
                relationData,
                prepared);
        multiValueRuntimeService.save(
                definition,
                id,
                multiValueData);

        EntityDataDTO dto =
                payloadMapper.toRuntimeDto(
                        updateData,
                        entityCode);
        enrichMultiValues(
                entityCode,
                List.of(dto));
        if (dto.getData() != null) {
            dto.getData().putAll(relationData);
        }
        return startWorkflowIfRequested(
                entityCode,
                id,
                formData,
                definition,
                existingData,
                relationData,
                dto);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(
            String entityCode,
            String id) {
        // 删除也必须与保存、更新使用同一发布守卫。尤其在尚无自关联的实体上，
        // 先共享锁定义行才能关闭“判断无关系后首次发布自关联”的并发窗口。
        relationRuntimeService.lockSelfRelationGuard(entityCode);
        EntityDefinition definition =
                definitionMapper
                        .findByEntityCode(entityCode)
                        .orElseThrow(() ->
                                new BusinessConflictException(
                                        "ENTITY_DEFINITION_NOT_FOUND",
                                        "实体不存在: " + entityCode));
        String tableName =
                dynamicTableService.getTableName(entityCode);
        lockDeleteRoot(tableName, id);
        relationRuntimeService.cascadeDeleteRelations(
                definition,
                id,
                false);
        multiValueRuntimeService.delete(entityCode, id);
        dynamicMapper.deleteById(
                tableName,
                id);
        if (uniqueValueService != null) {
            uniqueValueService.release(entityCode, id);
        }
        entityRecordTeamService.record(
                entityCode,
                id,
                "DELETE",
                "删除数据",
                null,
                null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void physicalDelete(
            String entityCode,
            String id) {
        // 物理删除会递归触发多张业务表写锁，必须仍从定义/发布守卫开始，
        // 避免与发布事务或普通聚合写形成反向锁序。
        relationRuntimeService.lockSelfRelationGuard(entityCode);
        EntityDefinition definition =
                definitionMapper
                        .findByEntityCode(entityCode)
                        .orElseThrow(() ->
                                new BusinessConflictException(
                                        "ENTITY_DEFINITION_NOT_FOUND",
                                        "实体不存在: " + entityCode));
        String tableName =
                dynamicTableService.getTableName(entityCode);
        lockDeleteRoot(tableName, id);
        relationRuntimeService.cascadeDeleteRelations(
                definition,
                id,
                true);
        multiValueRuntimeService.delete(entityCode, id);
        dynamicMapper.physicalDeleteById(
                tableName,
                id);
        if (uniqueValueService != null) {
            uniqueValueService.release(entityCode, id);
        }
    }

    /**
     * 在级联递归前锁定聚合根，维持“根定义/发布守卫 → 根业务行 → 子定义/发布
     * 守卫 → 子业务行”的固定顺序；目标不存在时终止，不能删除孤立子记录。
     */
    private void lockDeleteRoot(
            String tableName,
            String id) {
        if (dynamicMapper.selectByIdForUpdate(tableName, id) == null) {
            throw new BusinessConflictException(
                    "ENTITY_DATA_NOT_FOUND",
                    "数据不存在: " + id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentTask(
            String entityCode,
            String entityDataId,
            String currentTaskId,
            String currentTaskName,
            String currentTaskAssignee) {
        dynamicMapper.updateCurrentTask(
                dynamicTableService.getTableName(
                        entityCode),
                entityDataId,
                currentTaskId,
                currentTaskName,
                currentTaskAssignee);
    }

    /** 兼容内部旧调用；跨模块事件必须使用携带实例 ID 的重载。 */
    @Transactional(rollbackFor = Exception.class)
    public void markProcessEnded(String entityCode, String entityDataId,
            String statusCategory, String fallbackStatus) {
        markProcessEnded(null, entityCode, entityDataId, statusCategory, fallbackStatus);
    }

    /**
     * 回写流程结束投影。正常结束只更新新版本生命周期；fallbackStatus 用于旧版兼容及明确的终止等特殊操作。
     * 延迟事件必须匹配当前实例，避免旧一代流程结束覆盖重新发起的流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markProcessEnded(String processInstanceId, String entityCode, String entityDataId,
            String statusCategory, String fallbackStatus) {
        String tableName =
                dynamicTableService.getTableName(entityCode);
        LocalDateTime endedAt = LocalDateTime.now();
        Map<String, Object> existingData =
                dynamicMapper.selectByIdForUpdate(
                        tableName,
                        entityDataId);
        if (processInstanceId != null && (existingData == null
                || !processInstanceId.equals(asText(existingData.get("process_instance_id"))))) {
            throw new com.workflow.entity.data.domain.policy.StaleProcessEventException(processInstanceId);
        }
        Map<String, Object> updateData =
                new HashMap<>();
        updateData.put("id", entityDataId);
        updateData.put("process_status", "COMPLETED");
        // 空 fallback 表示连线模式，不按流程结束结果猜测业务状态。
        if (StringUtils.hasText(fallbackStatus)) {
            String currentStatus = existingData == null
                    ? null
                    : asText(existingData.get("status"));
            EntityStatus currentDefinition =
                    StringUtils.hasText(currentStatus)
                            ? entityStatusMapper
                                    .findByEntityAndCode(
                                            entityCode,
                                            currentStatus)
                            : null;
            String statusCode =
                    EntityProcessStatusPolicy.shouldPreserve(
                                    currentDefinition == null
                                            ? null
                                            : currentDefinition
                                                    .getStatusCategory(),
                                    statusCategory)
                            ? currentStatus
                            : getStatusByCategory(
                                    entityCode,
                                    statusCategory,
                                    fallbackStatus);
            updateData.put("status", statusCode);
        }
        updateData.put(
                "process_end_time",
                endedAt);
        updateData.put("update_time", endedAt);
        if (StringUtils.hasText(fallbackStatus) && "COMPLETED".equals(statusCategory)) {
            putPublishedTimestampIfPresent(
                    entityCode,
                    updateData,
                    "approved_at",
                    endedAt);
        }
        dynamicMapper.update(tableName, updateData);
        dynamicMapper.updateCurrentTask(
                tableName,
                entityDataId,
                null,
                null,
                null);
    }

    /** 新记录统一生成 code 作为业务编号，独立实体与流程实体使用同一套编码规则。 */
    private void insert(
            EntityDataDTO dto,
            String tableName,
            Map<String, Object> data,
            String currentUserId,
            String currentUserName) {
        String id = StringUtils.hasText(dto.getId()) ? dto.getId() : generateId();
        LocalDateTime now = LocalDateTime.now();
        data.put("id", id);
        data.put("create_by", currentUserId);
        data.put("create_time", now);
        data.put("update_by", currentUserId);
        data.put("update_time", now);
        data.put("deleted", 0);
        data.put("submitter_id", currentUserId);
        data.put("submitter_name", currentUserName);
        dto.setSubmitterId(currentUserId);
        dto.setSubmitterName(currentUserName);

        String currentDeptId = getCurrentDeptId();
        if (currentDeptId != null) {
            data.put("dept_id", currentDeptId);
        }
        String defaultStatus =
                getDefaultStatus(dto.getEntityCode());
        data.put("process_status", "NOT_STARTED");
        dto.setProcessStatus("NOT_STARTED");
        data.put("status", defaultStatus);
        dto.setStatus(defaultStatus);
        String code =
                codeGeneratorService.generateCode(
                        dto.getEntityCode());
        data.put("code", code);
        dto.setCode(code);
        if (dto.getData() != null
                && dto.getData().get("name") != null) {
            String name =
                    String.valueOf(
                            dto.getData().get("name"));
            data.put("name", name);
            dto.setName(name);
        }
        dynamicMapper.insert(tableName, data);
        dto.setId(id);
        entityRecordTeamService.record(
                dto.getEntityCode(),
                id,
                "CREATE",
                "创建数据",
                null,
                null);
    }

    private EntityDataDTO startWorkflowIfRequested(
            String entityCode,
            String id,
            Map<String, Object> formData,
            EntityDefinition definition,
            Map<String, Object> existingData,
            Map<String, Object> relationData,
            EntityDataDTO dto) {
        Object requested =
                formData.get("startProcess");
        boolean startProcess =
                Boolean.TRUE.equals(requested)
                        || "true".equalsIgnoreCase(
                                String.valueOf(requested));
        if (!startProcess) {
            return dto;
        }
        validator.validateProcessStart(
                true,
                definition);
        String existingProcessInstanceId =
                asText(existingData.get(
                        "process_instance_id"));
        if (StringUtils.hasText(
                existingProcessInstanceId)) {
            return dto;
        }
        dto.setStartProcess(true);
        dto.setEntityCode(entityCode);
        dto.setSubmitterId(asText(
                existingData.get("submitter_id")));
        dto.setSubmitterName(asText(
                existingData.get("submitter_name")));
        dto.setProcessVariables(null);
        startWorkflow(dto);
        Map<String, Object> refreshedData =
                dynamicMapper.selectById(
                        dynamicTableService.getTableName(
                                entityCode),
                        id);
        EntityDataDTO refreshed =
                payloadMapper.toRuntimeDto(
                        refreshedData,
                        entityCode);
        enrichMultiValues(
                entityCode,
                List.of(refreshed));
        if (refreshed.getData() != null) {
            refreshed.getData().putAll(relationData);
        }
        return refreshed;
    }

    private String getCurrentUserId(
            String defaultValue) {
        if (StringUtils.hasText(defaultValue)) {
            return defaultValue;
        }
        String userId = UserContext.getUserId();
        return userId == null ? "system" : userId;
    }

    private String getCurrentUserName(
            String defaultValue) {
        if (StringUtils.hasText(defaultValue)) {
            return defaultValue;
        }
        String userName = UserContext.getUsername();
        return userName == null ? "系统" : userName;
    }

    private String getCurrentDeptId() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserService.getById(userId);
        return user == null ? null : user.getDeptId();
    }

    private void enrichMultiValues(
            String entityCode,
            Collection<EntityDataDTO> records) {
        EntityDefinition definition =
                definitionMapper
                        .findByEntityCode(entityCode)
                        .orElse(null);
        if (definition == null
                || definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        multiValueRuntimeService.enrich(
                definition,
                records);
    }

    private String generateId() {
        return UUID.randomUUID().toString()
                .replace("-", "");
    }

    private String asText(Object value) {
        return value == null
                ? null : String.valueOf(value);
    }

    private String getDefaultStatus(
            String entityCode) {
        try {
            List<EntityStatus> statuses =
                    entityStatusMapper.findByCategory(
                            entityCode,
                            "NEW");
            if (statuses != null
                    && !statuses.isEmpty()) {
                return statuses.get(0)
                        .getStatusCode();
            }
        } catch (Exception exception) {
            log.warn(
                    "获取实体[{}]默认状态失败: {}",
                    LogValue.safe(entityCode),
                    LogValue.safe(exception.getMessage()));
        }
        return "DRAFT";
    }

    private void startWorkflow(EntityDataDTO dto) {
        EntityPublishedSnapshot snapshot =
                snapshotService.getLatestByEntityCode(
                        dto.getEntityCode());
        String processDefinitionId =
                snapshot.getProcessDefinitionId();
        if (!StringUtils.hasText(
                processDefinitionId)) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "实体发布快照未绑定流程定义: "
                            + dto.getEntityCode());
        }
        ProcessStartResult result =
                processRuntimePort.start(
                        new ProcessStartRequest(
                                processDefinitionId,
                                dto.getEntityCode(),
                                dto.getId(),
                                dto.getCode(),
                                dto.getSubmitterId(),
                                dto.getSubmitterName(),
                                getStatusByCategory(
                                        dto.getEntityCode(),
                                        "PROCESSING",
                                        "PENDING"),
                                dto.getData(),
                                dto.getProcessVariables()));

        LocalDateTime startedAt =
                LocalDateTime.now();
        Map<String, Object> updateData =
                new HashMap<>();
        updateData.put("id", dto.getId());
        updateData.put(
                "process_instance_id",
                result.processInstanceId());
        updateData.put(
                "process_start_time",
                startedAt);
        updateData.put("process_status", result.processStatus());
        // 新版本的开始连线可能已更新业务状态，禁止用启动前的状态覆盖。
        if (result.entityStatus() != null) {
            updateData.put("status", result.entityStatus());
        }
        updateData.put("process_end_time", "COMPLETED".equals(result.processStatus())
                ? startedAt : null);
        updateData.put("update_time", startedAt);
        updateData.put(
                "current_task_id",
                result.currentTaskId());
        updateData.put(
                "current_task_name",
                result.currentTaskName());
        updateData.put(
                "current_task_assignee",
                result.currentTaskAssignee());
        putPublishedTimestampIfPresent(
                snapshot,
                updateData,
                "submitted_at",
                startedAt);
        dynamicMapper.update(
                dynamicTableService.getTableName(
                        dto.getEntityCode()),
                updateData);

        dto.setProcessInstanceId(
                result.processInstanceId());
        Map<String, Object> current = dynamicMapper.selectById(
                dynamicTableService.getTableName(dto.getEntityCode()), dto.getId());
        dto.setStatus(current == null ? result.entityStatus() : asText(current.get("status")));
        dto.setProcessStatus(result.processStatus());
        dto.setProcessStartTime(startedAt);
        dto.setProcessEndTime("COMPLETED".equals(result.processStatus()) ? startedAt : null);
        dto.setCurrentTaskId(result.currentTaskId());
        dto.setCurrentTaskName(result.currentTaskName());
        dto.setCurrentTaskAssignee(
                result.currentTaskAssignee());
        entityRecordTeamService.record(
                dto.getEntityCode(),
                dto.getId(),
                "START_PROCESS",
                "发起流程",
                result.processInstanceId(),
                result.currentTaskId());
    }

    private void putPublishedTimestampIfPresent(
            String entityCode,
            Map<String, Object> updateData,
            String fieldCode,
            LocalDateTime value) {
        try {
            putPublishedTimestampIfPresent(
                    snapshotService
                            .getLatestByEntityCode(
                                    entityCode),
                    updateData,
                    fieldCode,
                    value);
        } catch (RuntimeException exception) {
            log.debug(
                    "读取实体发布字段失败，跳过业务时间同步: entityCode={}, fieldCode={}, reason={}",
                    LogValue.safe(entityCode),
                    LogValue.safe(fieldCode),
                    LogValue.safe(exception.getMessage()));
        }
    }

    private void putPublishedTimestampIfPresent(
            EntityPublishedSnapshot snapshot,
            Map<String, Object> updateData,
            String fieldCode,
            LocalDateTime value) {
        if (snapshot == null
                || snapshot.getFields() == null) {
            return;
        }
        snapshot.getFields().stream()
                .filter(field ->
                        fieldCode.equals(
                                field.getFieldCode()))
                .filter(field ->
                        !validator.isRelationField(field))
                .findFirst()
                .ifPresent(field -> {
                    String columnName =
                            StringUtils.hasText(
                                    field.getDbColumnName())
                                    ? field.getDbColumnName()
                                    : recordMapper.toColumnName(
                                            field.getFieldCode());
                    updateData.put(columnName, value);
                });
    }

    private String getStatusByCategory(
            String entityCode,
            String category,
            String fallback) {
        try {
            List<EntityStatus> statuses =
                    entityStatusMapper.findByCategory(
                            entityCode,
                            category);
            if (statuses != null
                    && !statuses.isEmpty()) {
                return statuses.get(0)
                        .getStatusCode();
            }
        } catch (Exception exception) {
            log.warn(
                    "获取实体[{}]状态分类[{}]失败: {}",
                    LogValue.safe(entityCode),
                    LogValue.safe(category),
                    LogValue.safe(exception.getMessage()));
        }
        return fallback;
    }
}
