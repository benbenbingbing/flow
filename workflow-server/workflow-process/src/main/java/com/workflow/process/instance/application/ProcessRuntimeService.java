package com.workflow.process.instance.application;

import com.workflow.process.task.application.TaskService;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.process.ProcessRuntimePort;
import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.contracts.process.ProcessStartResult;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.WorkflowAutoSkipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程发起应用服务，只负责流程引擎交互并返回运行态结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRuntimeService implements ProcessRuntimePort {

    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final RuntimeService runtimeService;
    private final IdentityService identityService;
    private final org.flowable.engine.TaskService taskService;
    private final ProcessTaskService processTaskService;
    private final WorkflowAutoSkipService workflowAutoSkipService;
    private final MultiInstanceCollectionListener multiInstanceCollectionListener;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.START,
            operation = "发起实体流程",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "PROCESS_INSTANCE",
            captureArguments = true,
            captureResult = true)
    public ProcessStartResult start(ProcessStartRequest request) {
        ProcessDefinitionConfig processConfig =
                processDefinitionConfigMapper.selectById(request.processDefinitionId());
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

        Map<String, Object> variables = buildVariables(request);
        multiInstanceCollectionListener.prepareVariables(processConfig.getId(), variables);
        if (StringUtils.hasText(request.submitterId())) {
            identityService.setAuthenticatedUserId(request.submitterId());
        }

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                processConfig.getProcessKey(),
                request.entityRecordId(),
                variables);
        workflowAutoSkipService.autoSkipNodes(processInstance.getId(), processConfig.getId());
        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();
        processTaskService.syncTasksFromFlowable(processInstance.getId());

        log.info("实体数据 {} 发起流程 {}，流程实例ID: {}",
                request.entityRecordId(), processConfig.getProcessKey(), processInstance.getId());
        return new ProcessStartResult(
                processInstance.getId(),
                request.processingStatus(),
                currentTask == null ? null : currentTask.getId(),
                currentTask == null ? null : currentTask.getName(),
                currentTask == null ? null : currentTask.getAssignee());
    }

    private Map<String, Object> buildVariables(ProcessStartRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("entityCode", request.entityCode());
        variables.put("entityDataId", request.entityRecordId());
        variables.put("dataNo", request.dataNo());
        variables.put("submitterId", request.submitterId());
        variables.put("submitterName", request.submitterName());
        variables.put("skipNodeEnabled", true);
        if (StringUtils.hasText(request.submitterId())) {
            variables.put("initiator", request.submitterId());
        }
        variables.putAll(request.data());
        variables.putAll(request.variables());
        return variables;
    }
}
