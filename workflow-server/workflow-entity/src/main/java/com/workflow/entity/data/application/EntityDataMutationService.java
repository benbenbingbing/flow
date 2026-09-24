package com.workflow.entity.data.application;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.process.port.ProcessRuntimePort;
import com.workflow.contracts.process.model.ProcessStartRequest;
import com.workflow.contracts.process.model.ProcessStartResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.logging.LogValue;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.domain.policy.EntityProcessStatusPolicy;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.code.EntityCodeGenerationInput;
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

    @org.springframework.beans.factory.annotation.Autowired
    private EntityTaskSummaryRefresh taskSummaryRefresh;

    /** 与实体事务合并登记，避免一个审批动作多次改状态时反复读取相同摘要。 */
    private void refreshTaskSummary(String entityCode, String recordId) {
        if (taskSummaryRefresh != null) taskSummaryRefresh.changed(entityCode, recordId);
    }


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

    /**
     * 保存实体数据变更；后续读取或执行将使用更新后的状态。
     *
     * @param dto DTO，供本方法保存实体数据变更时使用
     * @return 保存后的实体数据变更结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO save(EntityDataDTO dto) {
        return save(dto, null);
    }

    /**
     * 统一变更入口写入时显式下传不可伪造的递归唯一性计划。
     *
     * @param dto DTO，作为 {@code LogValue.safe} 的输入影响后续处理
     * @param prepared 已准备，供本方法保存实体数据变更时使用
     * @return 保存后的实体数据变更结果，供调用方继续处理
     */
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
        relationRuntimeService.stripUnpublishedRelationKeys(
                definition, originalData, parentData, relations);
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
        // 业务编号只由服务器创建，客户端值不应进入写前唯一性预留或字段规则校验。
        data.remove("code");
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
                    currentUserName,
                    multiValueData);
        } else {
            // 编码只在创建时分配，更新入口不能通过 DTO 覆写。
            data.remove("code");
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
        refreshTaskSummary(entityCode, dto.getId());
        return dto;
    }

    /**
     * 更新实体数据变更；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param formData 表单数据，供本方法更新实体数据变更时使用
     * @return 更新后的实体数据变更结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO update(
            String entityCode,
            String id,
            Map<String, Object> formData) {
        return update(entityCode, id, formData, null);
    }

    /**
     * 统一变更入口更新时显式下传不可伪造的递归唯一性计划。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param formData 表单数据，作为 {@code withoutRelationDataFromRequest} 的输入影响后续处理
     * @param prepared 已准备，作为 {@code relationRuntimeService.saveRelationData} 的输入影响后续处理
     * @return 更新后的实体数据变更结果，供调用方继续处理
     */
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
        relationRuntimeService.stripUnpublishedRelationKeys(
                definition, formData, parentFormData, relations);
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

        boolean restart = formData != null
                && Boolean.parseBoolean(String.valueOf(formData.get("restartProcess")));
        String previousInstanceId = formData == null ? null : asText(formData.get("previousProcessInstanceId"));
        if (restart) {
            EntityProcessRestartContext.require(entityCode, id, previousInstanceId);
        }
        // 重新发起和表单修改必须在同一记录锁及事务内，避免并发保存覆盖新一轮流程。
        Map<String, Object> existingData = restart
                ? dynamicMapper.selectByIdForUpdate(tableName, id)
                : dynamicMapper.selectById(tableName, id);
        if (existingData == null) {
            throw new RuntimeException(
                    "数据不存在: " + id);
        }
        if (restart) {
            EntityStatus status = entityStatusMapper.findByEntityAndCode(entityCode, asText(existingData.get("status")));
            if (!java.util.Objects.equals(previousInstanceId, asText(existingData.get("process_instance_id")))
                    || !"COMPLETED".equals(asText(existingData.get("process_status")))
                    || status == null || !"WITHDRAWN".equals(status.getStatusCategory())
                    || !processRuntimePort.canRestart(entityCode, id, previousInstanceId, UserContext.getUserId())) {
                throw new BusinessConflictException("ENTITY_PROCESS_RESTART_DENIED",
                        "仅原发起人可重新发起最新的已撤回流程，请刷新后重试");
            }
        }
        Map<String, Object> updateData =
                payloadMapper.buildUpdateData(
                        entityCode,
                        id,
                        parentFormData,
                        existingData);
        updateData.remove("code");
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
        refreshTaskSummary(entityCode, id);
        return startWorkflowIfRequested(
                entityCode,
                id,
                formData,
                definition,
                existingData,
                relationData,
                dto);
    }

    /**
     * 删除实体数据变更；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     */
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
        refreshTaskSummary(entityCode, id);
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

    /**
     * 处理物理删除，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     */
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
        refreshTaskSummary(entityCode, id);
        if (uniqueValueService != null) {
            uniqueValueService.release(entityCode, id);
        }
    }

    /**
     * 在级联递归前锁定聚合根，维持“根定义/发布守卫 → 根业务行 → 子定义/发布
     * 守卫 → 子业务行”的固定顺序；目标不存在时终止，不能删除孤立子记录。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param id 目标记录 ID，后续用于定位具体数据或配置
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

    /**
     * 更新当前任务；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param currentTaskId 当前任务ID，写入当前任务信息供后续待办展示和状态同步
     * @param currentTaskName 当前任务名称，写入当前任务信息供后续待办展示和状态同步
     * @param currentTaskAssignee 当前任务办理人，写入当前任务信息供后续待办展示和状态同步
     */
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
        refreshTaskSummary(entityCode, entityDataId);
    }

    /**
     * 兼容内部旧调用；跨模块事件必须使用携带实例 ID 的重载。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @param fallbackStatus 状态类别无法映射时写入实体的后备状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void markProcessEnded(String entityCode, String entityDataId,
            String statusCategory, String fallbackStatus) {
        markProcessEnded(null, entityCode, entityDataId, statusCategory, fallbackStatus);
    }

    /**
     * 回写流程结束投影。正常结束只更新新版本生命周期；fallbackStatus 用于旧版兼容及明确的终止等特殊操作。
     * 延迟事件必须匹配当前实例，避免旧一代流程结束覆盖重新发起的流程。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param statusCategory 状态类别，决定后续状态或结果的归类
     * @param fallbackStatus 状态类别无法映射时写入实体的后备状态
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
            if (com.workflow.entity.definition.application.EntitySpecialStatusPolicy.CATEGORIES.contains(statusCategory)) {
                getStatusByCategory(entityCode, statusCategory, fallbackStatus);
            }
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
        refreshTaskSummary(entityCode, entityDataId);
    }

    /**
     * 新记录统一生成 code 作为业务编号，独立实体与流程实体使用同一套编码规则。
     *
     * @param dto DTO，作为 {@code getDefaultStatus} 的输入影响后续处理
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param data 数据，后续用于插入实体数据变更并传递处理结果
     * @param currentUserId 当前用户ID，后续用于插入实体数据变更时定位或关联目标
     * @param currentUserName 当前用户名称，后续用于插入实体数据变更时匹配或展示
     */
    private void insert(
            EntityDataDTO dto,
            String tableName,
            Map<String, Object> data,
            String currentUserId,
            String currentUserName,
            Map<String, List<String>> multiValueData) {
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
        if (dto.getData() != null
                && dto.getData().get("name") != null) {
            String name =
                    String.valueOf(
                            dto.getData().get("name"));
            data.put("name", name);
            dto.setName(name);
        }
        // 多值字段稍后写关联表，但生成器仍应拿到本次表单的完整标量/多值输入。
        Map<String, Object> generationData = new HashMap<>(data);
        multiValueData.forEach((field, values) -> generationData.put(recordMapper.toColumnName(field), values));
        String code =
                codeGeneratorService.generateCode(new EntityCodeGenerationInput(
                        dto.getEntityCode(), id, generationData, null, null, "root", null));
        data.put("code", code);
        dto.setCode(code);
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

    /**
     * 启动工作流条件请求；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param formData 表单数据，供本方法启动工作流条件请求时使用
     * @param definition 定义，作为 {@code validator.validateProcessStart} 的输入影响后续处理
     * @param existingData 已有数据，作为 {@code asText} 的输入影响后续处理
     * @param relationData 关系数据，供本方法启动工作流条件请求时使用
     * @param dto DTO，作为 {@code startWorkflow} 的输入影响后续处理
     * @return 启动后的工作流条件请求结果，供调用方继续处理
     */
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
        boolean restart = Boolean.parseBoolean(String.valueOf(formData.get("restartProcess")));
        if (!startProcess && !restart) {
            return dto;
        }
        validator.validateProcessStart(
                true,
                definition);
        String existingProcessInstanceId =
                asText(existingData.get(
                        "process_instance_id"));
        if (!restart && StringUtils.hasText(
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
        // 更新 DTO 只含补丁字段；新实例必须使用保存后的完整数据和原业务编号。
        EntityDataDTO persisted = payloadMapper.toRuntimeDto(dynamicMapper.selectById(
                dynamicTableService.getTableName(entityCode), id), entityCode);
        dto.setCode(persisted.getCode());
        dto.setData(persisted.getData());
        enrichMultiValues(entityCode, List.of(dto));
        if (dto.getData() != null) dto.getData().putAll(relationData);
        startWorkflow(dto, restart ? existingProcessInstanceId : null);
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

    /**
     * 读取当前用户ID；查询结果供调用方展示或继续处理。
     *
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 读取后的当前用户ID文本，供调用方比较或展示
     */
    private String getCurrentUserId(
            String defaultValue) {
        if (StringUtils.hasText(defaultValue)) {
            return defaultValue;
        }
        String userId = UserContext.getUserId();
        return userId == null ? "system" : userId;
    }

    /**
     * 读取当前用户名称；查询结果供调用方展示或继续处理。
     *
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 读取后的当前用户名称文本，供调用方比较或展示
     */
    private String getCurrentUserName(
            String defaultValue) {
        if (StringUtils.hasText(defaultValue)) {
            return defaultValue;
        }
        String userName = UserContext.getUsername();
        return userName == null ? "系统" : userName;
    }

    /**
     * 读取当前部门ID；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的当前部门ID文本，供调用方比较或展示
     */
    private String getCurrentDeptId() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserService.getById(userId);
        return user == null ? null : user.getDeptId();
    }

    /**
     * 补充多实例值集合；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param records 记录集合，作为 {@code multiValueRuntimeService.enrich} 的输入影响后续处理
     */
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

    /**
     * 生成ID；结果供调用方的后续步骤使用。
     *
     * @return 生成后的ID文本，供调用方比较或展示
     */
    private String generateId() {
        return UUID.randomUUID().toString()
                .replace("-", "");
    }

    /**
     * 转换为文本；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为文本的原始输入，结果供调用方继续使用
     * @return 转换为后的文本文本，供调用方比较或展示
     */
    private String asText(Object value) {
        return value == null
                ? null : String.valueOf(value);
    }

    /**
     * 读取默认状态；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 读取后的默认状态文本，供调用方比较或展示
     */
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

    /**
     * 启动工作流；结果供调用方的后续步骤使用。
     *
     * @param dto DTO，作为 {@code snapshotService.getLatestByEntityCode} 的输入影响后续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private void startWorkflow(EntityDataDTO dto) {
        startWorkflow(dto, null);
    }

    /** 原记录新建一轮实例时传入旧实例坐标，由流程关联锁保证只能启动一次。 */
    private void startWorkflow(EntityDataDTO dto, String previousInstanceId) {
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
                                dto.getProcessVariables(),
                                previousInstanceId));

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
        refreshTaskSummary(dto.getEntityCode(), dto.getId());

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

    /**
     * 写入已发布时间戳条件存在；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param updateData 更新数据，供本方法写入已发布时间戳条件存在时使用
     * @param fieldCode 字段编码，后续用于写入已发布时间戳条件存在时定位或关联目标
     * @param value 待写入已发布时间戳条件存在的原始输入，结果供调用方继续使用
     */
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

    /**
     * 写入已发布时间戳条件存在；后续读取或执行将使用更新后的状态。
     *
     * @param snapshot 快照，供本方法写入已发布时间戳条件存在时使用
     * @param updateData 更新数据，供本方法写入已发布时间戳条件存在时使用
     * @param fieldCode 字段编码，后续用于写入已发布时间戳条件存在时定位或关联目标
     * @param value 待写入已发布时间戳条件存在的原始输入，结果供调用方继续使用
     */
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

    /**
     * 按类别查询实体数据变更；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param category 类别，决定后续状态或结果的归类
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 读取后的状态类别文本，供调用方比较或展示
     */
    private String getStatusByCategory(
            String entityCode,
            String category,
            String fallback) {
        if (com.workflow.entity.definition.application.EntitySpecialStatusPolicy.CATEGORIES.contains(category)) {
            return com.workflow.entity.definition.application.EntitySpecialStatusPolicy.requireTarget(
                    category, entityStatusMapper.findByCategory(entityCode, category));
        }
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
