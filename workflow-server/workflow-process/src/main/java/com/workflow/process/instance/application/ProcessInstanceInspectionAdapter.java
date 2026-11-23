package com.workflow.process.instance.application;

import com.workflow.port.process.instance.model.ProcessRuntimeViews.InstanceCapabilities;
import com.workflow.port.process.instance.model.ProcessRuntimeViews.Progress;
import com.workflow.port.process.query.model.ActionCapability;
import com.workflow.process.query.application.ProcessQueryAdapter;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.command.application.ProcessInvocationAccess;
import com.workflow.process.task.api.request.ReceiveTaskTriggerRequest;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** 沿用流程读取范围与当前节点权限，预检不产生运行状态变更。 */
@Component
@RequiredArgsConstructor
public class ProcessInstanceInspectionAdapter {
    private final ProcessInstanceService instances;
    private final ProcessInstanceAccessService instanceAccess;
    private final ProcessInvocationAccess callers;
    private final ProcessQueryAdapter queries;
    private final NodeOperationCapabilityService operations;

    public Progress getProgress(String processInstanceId) {
        var progress = instances.getProcessProgress(processInstanceId);
        return new Progress(progress.getProcessInstanceId(), progress.getProcessDefinitionId(), progress.getProcessVersion(),
                progress.getStatus(), progress.getEndType(), progress.getEndReason(), progress.getActiveNodes(),
                progress.getCompletedNodes(), progress.getCancelledNodes(), progress.getTerminatedNodes(),
                progress.getExecutedSequenceFlows());
    }

    public InstanceCapabilities getInstanceCapabilities(String processInstanceId) {
        var caller = callers.requireActor();
        var instance = queries.requireInstance(processInstanceId);
        String starter = instance.startUserId();
        String actorKey = Objects.equals(starter, caller.username()) ? caller.username() : caller.userId();
        var withdraw = capability(operations.canWithdrawProcess(processInstanceId, actorKey), "WITHDRAW_UNAVAILABLE");
        var terminate = capability(operations.canTerminateProcess(processInstanceId, actorKey), "TERMINATE_UNAVAILABLE");
        try {
            instanceAccess.requireSignalAccess(processInstanceId);
        } catch (ForbiddenException denied) {
            return new InstanceCapabilities(withdraw, terminate, capability(false, "PROCESS_ACCESS_DENIED"), List.of());
        }
        var targets = queries.listActiveActivities(processInstanceId).stream().filter(activity -> activity.active()
                && "receiveTask".equals(activity.activityType()) && activity.executionId() != null).toList();
        return new InstanceCapabilities(withdraw, terminate, capability(!targets.isEmpty(), "NO_WAITING_RECEIVE_TASK"), targets);
    }

    private ActionCapability capability(boolean allowed, String failureCode) {
        return new ActionCapability(allowed, allowed ? null : failureCode, null);
    }

    public ActionCapability previewReceive(String processInstanceId, String executionId, String activityId,
            String messageRef) {
        var request = new ReceiveTaskTriggerRequest();
        request.setExecutionId(executionId);
        request.setActivityId(activityId);
        request.setMessageRef(messageRef);
        try {
            instances.requireWaitingReceiveTask(processInstanceId, request);
            return capability(true, null);
        } catch (ForbiddenException denied) {
            return capability(false, "PROCESS_ACCESS_DENIED");
        } catch (IllegalArgumentException invalid) {
            return new ActionCapability(false, "RECEIVE_TASK_UNAVAILABLE", invalid.getMessage());
        }
    }
}
