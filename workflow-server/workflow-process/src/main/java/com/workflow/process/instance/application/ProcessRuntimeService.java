package com.workflow.process.instance.application;

import com.workflow.core.logging.LogValue;
import com.workflow.process.task.application.TaskService;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.entity.mutation.EntityChangeTargetFreezeCommand;
import com.workflow.contracts.entity.mutation.port.EntityChangeTargetPort;
import com.workflow.contracts.process.port.ProcessRuntimePort;
import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.contracts.process.ProcessStartResult;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.assignment.relative.InitiatorOrganizationSnapshotService;
import com.workflow.process.assignment.relative.RelativeOrgPositionProcessInspector;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
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
    private final ObjectProvider<EntityChangeTargetPort> changeTargetPortProvider;

    /** 可选字段注入仅用于兼容直接 new 的旧测试；生产环境必须由 Spring 提供。 */
    @Autowired
    private InitiatorOrganizationSnapshotService initiatorSnapshotService;

    @Autowired
    private RelativeOrgPositionProcessInspector relativePositionProcessInspector;

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
        EntityChangeTargetPort changeTargetPort = changeTargetPortProvider.getIfAvailable();
        if (changeTargetPort != null) {
            changeTargetPort.freeze(
                    new EntityChangeTargetFreezeCommand(
                            request.entityCode(),
                            request.entityRecordId(),
                            processConfig.getId(),
                            processInstance.getId(),
                            request.submitterId(),
                            WorkflowReservedVariables.sanitize(
                                    request.variables())));
        }
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();
        processTaskService.syncTasksFromFlowable(processInstance.getId());

        log.info("实体数据 {} 发起流程 {}，流程实例ID: {}",
                LogValue.safe(request.entityRecordId()), LogValue.safe(processConfig.getProcessKey()),
                LogValue.safe(processInstance.getId()));
        return new ProcessStartResult(
                processInstance.getId(),
                request.processingStatus(),
                currentTask == null ? null : currentTask.getId(),
                currentTask == null ? null : currentTask.getName(),
                currentTask == null ? null : currentTask.getAssignee());
    }

    private EntityProcessLink reserveLink(
            ProcessStartRequest request,
            ProcessDefinitionConfig processConfig) {
        EntityProcessLink latest = entityProcessLinkMapper.selectLatestForUpdate(
                request.entityCode(),
                request.entityRecordId());
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
        int inserted = entityProcessLinkMapper.insertPending(candidate);
        EntityProcessLink locked = entityProcessLinkMapper.selectForUpdate(
                request.entityCode(), request.entityRecordId(), generation);
        if (locked == null) {
            throw new IllegalStateException("实体流程链接写入失败: " + request.entityRecordId());
        }
        if (inserted == 0 && "ACTIVE".equals(locked.getState())) {
            if (!processConfig.getProcessKey().equals(locked.getProcessDefinitionKey())) {
                throw new BusinessConflictException(
                        "ENTITY_PROCESS_ALREADY_ACTIVE",
                        "实体已绑定其他活动流程");
            }
            return locked;
        }
        if (inserted == 0) {
            throw new BusinessConflictException(
                    "ENTITY_PROCESS_START_IN_PROGRESS",
                    "实体流程正在发起，请稍后重试");
        }
        return locked;
    }

    private ProcessStartResult existingResult(EntityProcessLink link) {
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(link.getProcessInstanceId())
                .active()
                .singleResult();
        return new ProcessStartResult(
                link.getProcessInstanceId(),
                link.getEntityStatus(),
                currentTask == null ? null : currentTask.getId(),
                currentTask == null ? null : currentTask.getName(),
                currentTask == null ? null : currentTask.getAssignee());
    }

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
        variables.put("dataNo", request.dataNo());
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
