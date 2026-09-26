package com.workflow.process.instance.application;

import com.workflow.core.logging.LogValue;
import com.workflow.core.database.jdbc.JdbcLockedRow;
import com.workflow.core.database.port.DatabaseClockPort;
import com.workflow.process.task.application.TaskService;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.process.port.ProcessRuntimePort;
import com.workflow.contracts.process.model.ProcessStartRequest;
import com.workflow.contracts.process.model.ProcessStartResult;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.assignment.application.InitiatorOrganizationSnapshotService;
import com.workflow.process.assignment.application.RelativeOrgPositionProcessInspector;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.application.DeployedSkipExpressionSafety;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 流程发起应用服务，只负责流程引擎交互并返回运行态结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRuntimeService implements ProcessRuntimePort {

    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final IdentityService identityService;
    private final org.flowable.engine.TaskService taskService;
    private final ProcessTaskService processTaskService;
    private final MultiInstanceCollectionListener multiInstanceCollectionListener;
    private final EntityProcessLinkMapper entityProcessLinkMapper;
    private final JdbcLockedRow lockedRows;
    private final DatabaseClockPort databaseClock;

    @Autowired
    private com.workflow.process.status.application.ProcessEntityStatusPolicy statusPolicy;
    @Autowired
    private org.flowable.engine.HistoryService historyService;

    /** 可选字段注入仅用于兼容直接 new 的旧测试；生产环境必须由 Spring 提供。 */
    @Autowired
    private InitiatorOrganizationSnapshotService initiatorSnapshotService;

    @Autowired
    private RelativeOrgPositionProcessInspector relativePositionProcessInspector;

    /**
     * 启动流程运行时；结果供调用方的后续步骤使用。
     *
     * @param request 本次请求，后续经校验后用于启动流程运行时
     * @return 启动后的流程运行时结果，供调用方继续处理
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.PROCESS, action = AuditAction.START, operation = "发起实体流程", risk = AuditRiskLevel.MEDIUM, targetType = "PROCESS_INSTANCE", captureArguments = true, captureResult = true)
    public ProcessStartResult start(ProcessStartRequest request) {
        ProcessDefinitionConfig processConfig = processDefinitionConfigMapper.selectById(request.processDefinitionId());
        if (processConfig == null) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "流程定义不存在: " + request.processDefinitionId());
        }
        if (processConfig.getStatus() != ProcessDefinitionConfig.ProcessStatus.PUBLISHED) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    processConfig.getStatus() == ProcessDefinitionConfig.ProcessStatus.DISABLED
                            ? "流程已禁用，无法发起: " + processConfig.getProcessName()
                            : "流程尚未发布，无法发起: " + processConfig.getProcessName());
        }

        EntityProcessLink link = reserveLink(request, processConfig);
        if ("ACTIVE".equals(link.getState())) {
            return existingResult(link);
        }

        ProcessDefinition deployedDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processConfig.getProcessKey())
                .latestVersion()
                .singleResult();
        if (deployedDefinition == null) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "流程已发布但部署版本不存在: "
                            + processConfig.getProcessName());
        }

        Map<String, Object> variables = buildVariables(
                request, deployedDefinition.getId());
        multiInstanceCollectionListener.prepareVariables(
                deployedDefinition.getId(), variables);
        if (StringUtils.hasText(request.submitterId())) {
            identityService.setAuthenticatedUserId(request.submitterId());
        }

        ProcessInstance processInstance;
        try {
            processInstance = runtimeService.startProcessInstanceById(
                    deployedDefinition.getId(),
                    request.entityRecordId(),
                    variables);
        } finally {
            if (StringUtils.hasText(request.submitterId())) {
                identityService.setAuthenticatedUserId(null);
            }
        }
        if (entityProcessLinkMapper.activate(
                link.getId(), link.getRequestId(), processInstance.getId()) != 1) {
            throw new IllegalStateException("实体流程链接激活失败: " + link.getId());
        }
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .listPage(0, 1).stream().findFirst().orElse(null);
        processTaskService.syncTasksFromFlowable(processInstance.getId());

        log.info("实体数据 {} 发起流程 {}，流程实例ID: {}",
                LogValue.safe(request.entityRecordId()), LogValue.safe(processConfig.getProcessKey()),
                LogValue.safe(processInstance.getId()));
        return new ProcessStartResult(
                processInstance.getId(),
                statusPolicy != null && statusPolicy.usesTransitions(deployedDefinition.getId())
                        ? null : request.processingStatus(),
                currentTask == null ? null : currentTask.getId(),
                currentTask == null ? null : currentTask.getName(),
                currentTask == null ? null : currentTask.getAssignee(),
                processInstance.isEnded() ? "COMPLETED" : "RUNNING");
    }

    /**
     * 当前实体写事务内占用本代流程链接；调用方已写入/锁定实体根记录。
     * 同一代次由唯一约束和事务行锁保护，只有创建该行的请求允许首次启动引擎。
     *
     * @param request 本次请求，后续经校验后用于处理{@code reserve}链接
     * @param processConfig 流程配置内容，决定后续{@code reserve}链接的处理规则
     * @return 处理后的{@code reserve}链接结果，供调用方继续处理
     */
    private EntityProcessLink reserveLink(
            ProcessStartRequest request,
            ProcessDefinitionConfig processConfig) {
        EntityProcessLink latest = entityProcessLinkMapper.selectLatestForUpdate(
                request.entityCode(),
                request.entityRecordId());
        // 下一代必须由明确的重新发起请求创建；旧请求或重复点击不能隐式启动新一轮。
        if (StringUtils.hasText(request.previousProcessInstanceId())) {
            if (latest == null || !request.previousProcessInstanceId().equals(latest.getProcessInstanceId())
                    || !canRestart(request.entityCode(), request.entityRecordId(),
                            request.previousProcessInstanceId(), request.submitterId())) {
                throw new BusinessConflictException("PROCESS_RESTART_NOT_ALLOWED", "流程已变化或不允许重新发起，请刷新后重试");
            }
        } else if (latest != null && "ENDED".equals(latest.getState())) {
            throw new BusinessConflictException("PROCESS_RESTART_REQUIRED", "已有流程已结束，请使用重新发起操作");
        }
        int generation = latest == null
                ? 1
                : ("ENDED".equals(latest.getState())
                        ? latest.getGeneration() + 1
                        : latest.getGeneration());
        String requestId = stableRequestId(
                request.entityCode(), request.entityRecordId(), generation);
        EntityProcessLink candidate = new EntityProcessLink();
        candidate.setId(UUID.randomUUID().toString().replace("-", ""));
        candidate.setEntityCode(request.entityCode());
        candidate.setEntityRecordId(request.entityRecordId());
        candidate.setGeneration(generation);
        candidate.setProcessDefinitionKey(processConfig.getProcessKey());
        candidate.setRequestId(requestId);
        candidate.setEntityStatus(request.processingStatus());
        var now = databaseClock.utcNow();
        var initial = new LinkedHashMap<String, Object>();
        initial.put("id", candidate.getId());
        initial.put("entity_code", candidate.getEntityCode());
        initial.put("entity_record_id", candidate.getEntityRecordId());
        initial.put("generation", generation);
        initial.put("process_definition_key", candidate.getProcessDefinitionKey());
        initial.put("state", "PENDING");
        initial.put("request_id", requestId);
        initial.put("entity_status", candidate.getEntityStatus());
        initial.put("version", 0L);
        initial.put("create_time", now);
        initial.put("update_time", now);
        lockedRows.ensureAndLock("entity_process_link", initial,
                List.of("entity_code", "entity_record_id", "generation"));
        EntityProcessLink locked = entityProcessLinkMapper.selectForUpdate(
                request.entityCode(), request.entityRecordId(), generation);
        if (locked == null) {
            throw new IllegalStateException("实体流程链接写入失败: " + request.entityRecordId());
        }
        // no-op 初始化不会覆盖旧行 ID；以随机候选 ID 判定创建者，避免厂商影响行数差异。
        boolean inserted = candidate.getId().equals(locked.getId());
        if (!inserted && "ACTIVE".equals(locked.getState())) {
            if (!processConfig.getProcessKey().equals(locked.getProcessDefinitionKey())) {
                throw new BusinessConflictException(
                        "ENTITY_PROCESS_ALREADY_ACTIVE",
                        "实体已绑定其他活动流程");
            }
            return locked;
        }
        if (!inserted) {
            throw new BusinessConflictException(
                    "ENTITY_PROCESS_START_IN_PROGRESS",
                    "实体流程正在发起，请稍后重试");
        }
        return locked;
    }

    /**
     * 同一实体、最新代次、真实撤回原因与发起人必须全部匹配。实体投影即使被误改为撤回也不能放行。
     * 写入口已持有实体行及流程链接锁；只读能力查询不加锁，实际发起时会重新检查。
     */
    @Override
    public boolean canRestart(String entityCode, String entityRecordId, String previousInstanceId, String userId) {
        if (!StringUtils.hasText(previousInstanceId) || !StringUtils.hasText(userId)) return false;
        EntityProcessLink latest = entityProcessLinkMapper.selectLatest(entityCode, entityRecordId);
        if (latest == null || !previousInstanceId.equals(latest.getProcessInstanceId())
                || !"ENDED".equals(latest.getState()) || !"WITHDRAWN".equals(latest.getEndType())) return false;
        if (runtimeService.createProcessInstanceQuery().processInstanceId(previousInstanceId).singleResult() != null) return false;
        var historic = historyService.createHistoricProcessInstanceQuery().processInstanceId(previousInstanceId).singleResult();
        return historic != null && historic.getEndTime() != null && userId.equals(historic.getStartUserId())
                && "WITHDRAWN".equals(com.workflow.process.status.application.ProcessEndReason.category(historic.getDeleteReason()));
    }

    /**
     * 启动重试复用原实例；结束通知尚未消费时不能把已完成实例重新标为运行中。
     *
     * @param link 链接，作为 {@code IllegalStateException} 的输入影响后续处理
     * @return 处理后的已有结果，供调用方继续处理
     */
    private ProcessStartResult existingResult(EntityProcessLink link) {
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(link.getProcessInstanceId()).singleResult();
        if (running == null) {
            var historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(link.getProcessInstanceId()).singleResult();
            if (historic == null || historic.getEndTime() == null) {
                throw new IllegalStateException("活动流程链接缺少可确认的引擎实例: " + link.getProcessInstanceId());
            }
            return new ProcessStartResult(link.getProcessInstanceId(), null, null, null, null, "COMPLETED");
        }
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(link.getProcessInstanceId())
                .active()
                .listPage(0, 1).stream().findFirst().orElse(null);
        return new ProcessStartResult(
                link.getProcessInstanceId(),
                null, // 重放启动请求不能覆盖后续连线已经更新的业务状态。
                currentTask == null ? null : currentTask.getId(),
                currentTask == null ? null : currentTask.getName(),
                currentTask == null ? null : currentTask.getAssignee());
    }

    /**
     * 生成稳定请求ID文本，供后续匹配或展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityRecordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param generation {@code generation}，供本方法处理稳定请求ID时使用
     * @return 处理后的稳定请求ID文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String stableRequestId(
            String entityCode,
            String entityRecordId,
            int generation) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (entityCode + '\n' + entityRecordId + '\n' + generation)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 构建流程变量；结果供后续流程传递或持久化。
     *
     * @param request 本次请求，后续经校验后用于构建流程变量
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 流程变量键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private Map<String, Object> buildVariables(
            ProcessStartRequest request,
            String processDefinitionId) {
        Map<String, Object> variables = new HashMap<>();
        // 业务数据和扩展变量都属于不可信输入；先合并且移除保留键，
        // 再由服务端最后写入发起人与组织快照，防止伪造审批上下文。
        variables.putAll(WorkflowReservedVariables.sanitize(request.data()));
        variables.putAll(WorkflowReservedVariables.sanitize(request.variables()));
        variables.put("entityCode", request.entityCode());
        variables.put("entityDataId", request.entityRecordId());
        // 编号取自服务端实体记录，覆盖表单或扩展变量中的同名输入。
        variables.put("code", request.code());
        variables.put("submitterId", request.submitterId());
        variables.put("submitterName", request.submitterName());
        // 历史部署可能包含旧 UI 保存的任意 UEL；只有全部表达式通过当前
        // 发布白名单时才打开进程级开关，否则保留用户任务人工办理兜底。
        String unsafeSkipElement =
                DeployedSkipExpressionSafety.firstUnsafeElementId(
                        repositoryService.getBpmnModel(
                                processDefinitionId));
        if (unsafeSkipElement == null) {
            WorkflowReservedVariables.enableNativeSkipExpressions(
                    variables);
        } else {
            log.warn(
                    "历史部署包含不安全 skipExpression，未启用原生跳过: processDefinitionId={}, element={}",
                    LogValue.safe(processDefinitionId),
                    LogValue.safe(unsafeSkipElement));
        }
        if (StringUtils.hasText(request.submitterId())) {
            variables.put("startUserId", request.submitterId());
            variables.put("initiator", request.submitterId());
        }
        if (relativePositionProcessInspector != null
                && relativePositionProcessInspector
                .requiresInitiatorOrganizationSnapshot(
                        processDefinitionId)) {
            if (initiatorSnapshotService == null) {
                throw new IllegalStateException(
                        "相对组织职务快照服务未注册");
            }
            initiatorSnapshotService.captureTrustedSnapshot(
                    variables, request.submitterId());
        }
        return variables;
    }
}
