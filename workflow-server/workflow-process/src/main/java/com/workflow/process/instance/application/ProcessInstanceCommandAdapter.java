package com.workflow.process.instance.application;

import com.workflow.port.process.error.ProcessOperationException;
import com.workflow.port.process.model.instance.*;
import com.workflow.port.process.model.ProcessCommandReceipt;
import com.workflow.process.command.application.ProcessCommandExecutor;
import com.workflow.process.command.application.ProcessCommandFailures;
import com.workflow.process.command.application.ProcessInvocationAccess;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.api.request.ReceiveTaskTriggerRequest;
import com.workflow.process.variable.application.ProcessVariablePolicy;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.HistoryService;
import org.springframework.stereotype.Component;

/** 实例控制共享原平台业务执行路径，外层统一增加实际身份、事务回执与稳定错误。 */
@Component
@RequiredArgsConstructor
public class ProcessInstanceCommandAdapter {
    private final ProcessCommandExecutor commands;
    private final ProcessInvocationAccess access;
    private final TaskActionService taskActions;
    private final ProcessTerminationService termination;
    private final ProcessInstanceService instances;
    private final RuntimeService runtime;
    private final HistoryService history;

    /** 撤回仍由原任务应用服务校验所有活动节点和实体目标状态，不能只删除引擎实例。 */
    public ProcessCommandReceipt withdraw(WithdrawProcessCommand command) {
        return withdraw(command, false);
    }

    /** 用户入口沿用当前身份的对象授权，同时复用命令事务和回执。 */
    public ProcessCommandReceipt withdrawForUser(WithdrawProcessCommand command) {
        requireInitiator(command.processInstanceId(), false);
        return withdraw(command, true);
    }

    private ProcessCommandReceipt withdraw(WithdrawProcessCommand command, boolean checkAccess) {
        access.requireActor();
        return ProcessCommandFailures.translate(command.processInstanceId(), () -> commands.execute(command.meta(),
                "PROCESS_WITHDRAW", command, Boolean.class, () -> {
                    requireActive(command.processInstanceId());
                    if (checkAccess) {
                        taskActions.withdrawProcess(command.processInstanceId(), actorKey(command.processInstanceId()), command.reason());
                    } else {
                        taskActions.withdrawForPort(command.processInstanceId(), operatorId(), command.reason());
                    }
                    return new ProcessCommandExecutor.Outcome<>(command.processInstanceId(), null, true, true);
                }).receipt());
    }

    /** 原终止服务的错误 Result 必须变为异常，才能阻止回执与外层事务被误当作成功提交。 */
    public ProcessCommandReceipt terminate(TerminateProcessCommand command) {
        return terminate(command, false);
    }

    /** 用户入口沿用当前身份的对象授权，同时复用命令事务和回执。 */
    public ProcessCommandReceipt terminateForUser(TerminateProcessCommand command) {
        requireInitiator(command.processInstanceId(), true);
        return terminate(command, true);
    }

    private ProcessCommandReceipt terminate(TerminateProcessCommand command, boolean checkAccess) {
        access.requireActor();
        return ProcessCommandFailures.translate(command.processInstanceId(), () -> commands.execute(command.meta(),
                "PROCESS_TERMINATE", command, Boolean.class, () -> {
                    requireActive(command.processInstanceId());
                    var result = checkAccess
                            ? termination.terminateProcess(command.processInstanceId(), actorKey(command.processInstanceId()), command.reason())
                            : termination.terminateForPort(command.processInstanceId(), operatorId(), command.reason());
                    if (result.getCode() != 200) throw new ProcessOperationException(
                            result.getErrorCode() != null ? result.getErrorCode() : result.getCode() == 403
                                    ? "PROCESS_ACCESS_DENIED" : "PROCESS_OPERATION_FAILED",
                            result.getMessage(), command.processInstanceId());
                    return new ProcessCommandExecutor.Outcome<>(command.processInstanceId(), null, true, true);
                }).receipt());
    }

    /**
     * 接收任务的根变量与引擎推进由原实例服务在同一事务完成；不能在方法外先写变量。
     * 已消费 executionId 的重试先读持久化回执，不重新查找一个后继执行来代替原目标。
     */
    public ReceiveTaskTriggerResult triggerReceiveTask(TriggerReceiveTaskCommand command) {
        access.requireActor();
        ProcessVariablePolicy.requireWritable(command.variables().keySet(), command.processInstanceId());
        return ProcessCommandFailures.translate(command.processInstanceId(), () -> {
            var result = commands.execute(command.meta(), "RECEIVE_TASK_TRIGGER", command, String.class, () -> {
                requireActive(command.processInstanceId());
                var request = new ReceiveTaskTriggerRequest();
                request.setExecutionId(command.executionId());
                request.setActivityId(command.activityId());
                request.setMessageRef(command.messageRef());
                request.setVariables(command.variables());
                String executionId = instances.triggerReceiveTaskForPort(command.processInstanceId(), request);
                return new ProcessCommandExecutor.Outcome<>(command.processInstanceId(), null, true, executionId);
            });
            return new ReceiveTaskTriggerResult(result.receipt(), result.value());
        });
    }

    /** 用户入口沿用当前身份的对象授权，同时复用命令事务和回执。 */
    public ReceiveTaskTriggerResult triggerReceiveTaskForUser(TriggerReceiveTaskCommand command) {
        return ProcessCommandFailures.translate(command.processInstanceId(), () -> {
            // 回执可能由已授权的后台任务创建，用户入口要先完成自己的对象授权。
            instances.requireSignalAccess(command.processInstanceId());
            return triggerReceiveTask(command);
        });
    }

    /** 结束后的重试同样要核对历史发起人，后台命令回执不能替代用户入口授权。 */
    private void requireInitiator(String instanceId, boolean preferHistoricalStarter) {
        var actor = access.requireActor();
        var live = runtime.createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        String starter = live == null ? null : live.getStartUserId();
        if (preferHistoricalStarter || starter == null || starter.isBlank()) {
            var archived = history.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
            if (live == null && archived == null) {
                throw new ProcessOperationException("PROCESS_NOT_FOUND", "流程实例不存在", instanceId);
            }
            if (archived != null) starter = archived.getStartUserId();
        }
        if (starter == null || !starter.equals(actor.userId()) && !starter.equals(actor.username())) {
            throw new ProcessOperationException("PROCESS_ACCESS_DENIED", "只有发起人可以撤回或终止流程", instanceId);
        }
    }

    private String operatorId() {
        var actor = access.requireActor();
        return actor.userId() != null && !actor.userId().isBlank() ? actor.userId() : actor.username();
    }

    /** ID 和用户名仅用于匹配同一真实操作人，不允许请求声明另一个发起人身份。 */
    private String actorKey(String instanceId) {
        var actor = access.requireActor();
        var instance = history.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        String starter = instance == null ? null : instance.getStartUserId();
        if (starter != null && (starter.equals(actor.userId()) || starter.equals(actor.username()))) return starter;
        return actor.userId() != null && !actor.userId().isBlank() ? actor.userId() : actor.username();
    }

    private void requireActive(String instanceId) {
        var instance = runtime.createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (instance != null) return;
        var archived = history.createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        throw new ProcessOperationException(archived == null ? "PROCESS_NOT_FOUND" : "PROCESS_ALREADY_ENDED",
                archived == null ? "流程实例不存在" : "流程已结束", instanceId);
    }
}
