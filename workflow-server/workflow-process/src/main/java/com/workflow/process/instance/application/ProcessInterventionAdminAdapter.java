package com.workflow.process.instance.application;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.port.process.instance.model.ProcessInterventionModels.*;
import com.workflow.port.process.ProcessActionExecutionPort;
import com.workflow.port.process.ProcessAssigneeIncidentPort;
import com.workflow.port.process.ProcessTaskHandoverPort;
import com.workflow.port.process.model.ProcessPage;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.action.application.FlowActionExecutionService;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import com.workflow.process.assignment.application.AssigneeIncidentService;
import com.workflow.process.task.api.request.TaskHandoverRequest;
import com.workflow.process.task.application.TaskHandoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 对其他模块复用事件处置、动作重放与管理员交接的原有规则和事务。 */
@Component
@RequiredArgsConstructor
public class ProcessInterventionAdminAdapter implements ProcessAssigneeIncidentPort, ProcessActionExecutionPort, ProcessTaskHandoverPort {
    private final CurrentActorPort authorization;
    private final CurrentUserRoleService roles;
    private final AssigneeIncidentService incidents;
    private final FlowActionExecutionService executions;
    private final TaskHandoverService handovers;

    @Override
    public ProcessPage<Map<String, Object>> pageIncidents(String status, int pageNumber, int pageSize) {
        require("process:assignee-incident:list");
        var result = incidents.list(status, pageNumber, pageSize);
        return new ProcessPage<>(result.getRecords(), result.getTotal(), (int) result.getPageNum(), (int) result.getPageSize());
    }

    @Override
    public Map<String, Object> findIncident(String incidentId) {
        require("process:assignee-incident:list");
        return incidents.detail(incidentId);
    }

    @Override
    public Map<String, Object> handleIncident(HandleAssigneeIncident command) {
        require("process:assignee-incident:handle");
        var request = new AssigneeIncidentHandleRequest();
        request.setRequestId(command.requestId());
        request.setAction(command.action().name());
        request.setUserId(command.userId());
        request.setGroupCode(command.groupCode());
        request.setReason(command.reason());
        return incidents.handle(command.incidentId(), request);
    }

    @Override
    public List<ActionExecution> listExecutions(String processInstanceId) {
        roles.requireSuperAdmin();
        return executions.findDetailsByProcessInstanceId(processInstanceId).stream().map(item -> new ActionExecution(
                item.getId(), item.getActionName(), item.getTaskId(), item.getVersionId(), item.getStatus(),
                item.getResolutionStatus(), item.getAttemptNo(), item.getTerminationReason(), item.getErrorMessage(),
                item.getCreatedAt())).toList();
    }

    @Override
    public void retryExecution(String executionId) {
        roles.requireSuperAdmin();
        executions.retry(executionId);
    }

    @Override
    public void resolveExecution(String executionId, String note) {
        roles.requireSuperAdmin();
        executions.resolve(executionId, note);
    }

    @Override
    public ProcessPage<HandoverTask> pageHandoverTasks(String sourceUserId, int pageNumber, int pageSize) {
        require("system:task-handover:view");
        var result = handovers.findPage(sourceUserId, pageNumber, pageSize);
        return new ProcessPage<>(result.getRecords().stream().map(item -> new HandoverTask(item.getTaskId(),
                item.getTaskName(), item.getProcessInstanceId(), item.getEntityCode(), item.getEntityDataId(),
                item.getAssignmentType(), item.getStatus(), item.getCreateTime())).toList(), result.getTotal(),
                (int) result.getPageNum(), (int) result.getPageSize());
    }

    @Override
    public int handover(HandoverCommand command) {
        require("system:task-handover:transfer");
        return handovers.transfer(new TaskHandoverRequest(command.sourceUserId(), command.targetUserId(),
                command.taskIds(), command.all(), command.reason()));
    }

    private void require(String permission) {
        if (!authorization.hasPermission(permission)) throw new ForbiddenException("缺少流程干预权限");
    }
}
