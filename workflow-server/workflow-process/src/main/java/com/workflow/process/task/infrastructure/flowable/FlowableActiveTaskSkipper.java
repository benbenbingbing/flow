package com.workflow.process.task.infrastructure.flowable;

import com.workflow.port.process.error.ProcessOperationException;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ManagementService;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.TaskHelper;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 用 Flowable 的用户任务离开机制处理已经创建的待办。 */
@Component
@RequiredArgsConstructor
public class FlowableActiveTaskSkipper {
    private final ManagementService management;

    /**
     * 在同一次引擎命令中关闭当前待办并触发其执行流，使用 BPMN 原有的连线条件。
     * @param source 已通过调用权限及本地编排校验的待办快照
     * @throws ProcessOperationException 任务执行流已变化，或无法作为普通用户任务跳过
     */
    public void skip(Task source) {
        management.executeCommand(context -> {
            var taskService = CommandContextUtil.getProcessEngineConfiguration(context)
                    .getTaskServiceConfiguration().getTaskService();
            var task = taskService.getTask(source.getId());
            if (task == null || task.isDeleted() || task.isSuspended()
                    || !Objects.equals(task.getExecutionId(), source.getExecutionId())
                    || !Objects.equals(task.getProcessInstanceId(), source.getProcessInstanceId())) {
                throw new ProcessOperationException("TASK_STATE_CHANGED", "任务执行状态已变化", source.getId());
            }
            if (task.getExecutionId() == null || task.getProcessInstanceId() == null
                    || task.getDelegationState() == DelegationState.PENDING || task.getParentTaskId() != null
                    || !taskService.findTasksByParentTaskId(task.getId()).isEmpty()) {
                throw new ProcessOperationException("TASK_SKIP_UNSUPPORTED", "仅支持跳过普通流程待办", source.getId());
            }
            var execution = CommandContextUtil.getExecutionEntityManager(context).findById(task.getExecutionId());
            if (execution == null || !execution.isActive()
                    || !(execution.getCurrentFlowElement() instanceof UserTask node)
                    || !Objects.equals(node.getId(), task.getTaskDefinitionKey())) {
                throw new ProcessOperationException("TASK_STATE_CHANGED", "任务已离开原节点", source.getId());
            }
            if (node.getLoopCharacteristics() != null) {
                throw new ProcessOperationException("TASK_SKIP_UNSUPPORTED", "会签节点不能跳过单个待办", source.getId());
            }

            // 沿用原生 skipExpression 的关闭方式，保留任务历史，不触发审批完成监听器。
            // 已存在的待办需要发出实体删除事件，以便同事务清理候选权限投影。
            TaskHelper.deleteTask(task, null, false, false, true);
            CommandContextUtil.getAgenda(context).planTriggerExecutionOperation(execution);
            return null;
        });
    }
}
