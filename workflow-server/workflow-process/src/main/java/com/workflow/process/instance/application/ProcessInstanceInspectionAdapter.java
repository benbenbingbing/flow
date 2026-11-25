package com.workflow.process.instance.application;

import com.workflow.port.process.model.instance.ProcessRuntimeViews.InstanceCapabilities;
import com.workflow.port.process.model.instance.ProcessRuntimeViews.Progress;
import com.workflow.port.process.model.query.ActionCapability;
import com.workflow.process.query.application.ProcessQueryAdapter;
import com.workflow.core.error.ForbiddenException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.process.task.api.request.ReceiveTaskTriggerRequest;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 查询调用方已授权实例的运行状态和发布规则，预检不产生副作用。 */
@Component
@RequiredArgsConstructor
public class ProcessInstanceInspectionAdapter {
    private final ProcessInstanceService instances;
    private final ProcessQueryAdapter queries;
    private final NodeOperationCapabilityService operations;

    public Progress getProgress(String processInstanceId) {
        var progress = instances.getProgressForPort(processInstanceId);
        return new Progress(progress.getProcessInstanceId(), progress.getProcessDefinitionId(), progress.getProcessVersion(),
                progress.getStatus(), progress.getEndType(), progress.getEndReason(), progress.getActiveNodes(),
                progress.getCompletedNodes(), progress.getCancelledNodes(), progress.getTerminatedNodes(),
                progress.getExecutedSequenceFlows());
    }

    public InstanceCapabilities getInstanceCapabilities(String processInstanceId) {
        String operatorId = UserContext.getUserId();
        var targets = queries.listActiveActivities(processInstanceId).stream().filter(activity -> activity.active()
                && "receiveTask".equals(activity.activityType()) && activity.executionId() != null).toList();
        var withdraw = capability(operations.canWithdrawForPort(processInstanceId, operatorId), "WITHDRAW_UNAVAILABLE");
        var terminate = capability(operations.canTerminateForPort(processInstanceId, operatorId), "TERMINATE_UNAVAILABLE");
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
