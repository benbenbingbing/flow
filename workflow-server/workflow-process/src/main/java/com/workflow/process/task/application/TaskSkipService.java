package com.workflow.process.task.application;

import com.workflow.port.process.error.ProcessOperationException;
import com.workflow.port.process.model.ProcessCommandReceipt;
import com.workflow.port.process.model.task.SkipTaskCommand;
import com.workflow.port.system.SysUserLookupPort;
import com.workflow.port.process.model.ProcessValues;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.command.application.ProcessCommandExecutor;
import com.workflow.process.command.application.ProcessCommandFailures;
import com.workflow.process.command.application.ProcessInvocationAccess;
import com.workflow.process.task.infrastructure.flowable.FlowableActiveTaskSkipper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaskSkipService {
    private final ProcessInvocationAccess access;
    private final ProcessCommandExecutor commands;
    private final TaskService tasks;
    private final ProcessTaskService mirrors;
    private final TaskAddSignService addSigns;
    private final FlowableActiveTaskSkipper engine;
    private final ProcessOperationLogMapper logs;
    private final SysUserLookupPort identities;

    /** 调用方授权后执行或重放跳过命令。待办、引擎、日志与回执在同一事务内更新。 */
    public ProcessCommandReceipt skip(SkipTaskCommand command) {
        var actor = access.requireActor();
        String operator = StringUtils.hasText(actor.userId()) ? actor.userId() : actor.username();
        String taskId = command.taskId();
        return ProcessCommandFailures.translate(taskId, () -> commands.execute(
                command.meta(), "TASK_SKIP", command, Boolean.class,
                () -> TaskCommandScope.run(taskId, () -> advance(command, operator))).receipt());
    }

    /** 后台调用显式提供记入历史的操作人，授权由调用模块完成。 */
    public ProcessCommandReceipt skip(SkipTaskCommand command, String operatorId) {
        String operator = ProcessValues.required(operatorId, "operatorId").strip();
        String taskId = command.taskId();
        return ProcessCommandFailures.translate(taskId, () -> commands.executeForOperator(operator,
                command.meta(), "TASK_SKIP", command, Boolean.class,
                () -> TaskCommandScope.run(taskId, () -> advance(command, operator))).receipt());
    }

    private ProcessCommandExecutor.Outcome<Boolean> advance(SkipTaskCommand command, String operator) {
        String taskId = command.taskId();
        var source = tasks.createTaskQuery().taskId(taskId).active().singleResult();
        if (source == null || !mirrors.lockPendingTaskForIntervention(taskId)) {
            throw new ProcessOperationException("TASK_STATE_CHANGED", "任务不存在或已不是活动待办", taskId);
        }
        // 加签也先锁源待办，此处用当前读检查编排。回退重提有独立的提交和状态恢复规则。
        if (addSigns.hasOpenAddSignForUpdate(taskId)
                || Boolean.TRUE.equals(tasks.getVariableLocal(taskId, TaskHistoryReturnService.RESUBMIT_MARKER))) {
            throw new ProcessOperationException("TASK_SKIP_UNSUPPORTED", "加签或回退重提中的待办不能跳过", taskId);
        }

        // 流程继续前先登记跳过，让后续节点及流程结束时读取到正确的办理记录。
        String instanceId = source.getProcessInstanceId();
        mirrors.markTaskSkipped(taskId, command.reason());
        recordSkip(instanceId, taskId, command.reason(), operator);
        engine.skip(source);
        mirrors.syncTasksAfterIntervention(instanceId);
        return new ProcessCommandExecutor.Outcome<>(instanceId, taskId, true, true);
    }

    private void recordSkip(String instanceId, String taskId, String reason, String operator) {
        var log = new ProcessOperationLog();
        log.setProcessInstanceId(instanceId);
        log.setTaskId(taskId);
        log.setOperationType("SKIP");
        log.setOperatorId(operator);
        log.setOperatorName(identities.getDisplayName(operator));
        log.setOperationComment(reason);
        log.setOperationTime(LocalDateTime.now());
        log.setCreatedAt(log.getOperationTime());
        if (logs.insert(log) != 1) throw new IllegalStateException("流程跳过记录写入失败");
    }
}
