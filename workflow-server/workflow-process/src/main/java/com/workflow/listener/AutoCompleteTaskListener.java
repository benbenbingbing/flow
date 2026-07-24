package com.workflow.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * 自动完成任务监听器
 * 
 * 当流程节点设置为"跳过"时，此监听器在任务创建时自动完成任务，
 * 使流程直接流转到下一节点，无需人工处理。
 * 
 * 注意：此监听器需要在 BPMN 中配置为 taskListener，event="create"
 */
@Slf4j
public class AutoCompleteTaskListener implements TaskListener {

    /**
     * 任务创建回调：标记当前节点为自动跳过，并设置 approved 变量为 approve。
     * <p>
     * 受事务限制不在此直接 complete，实际完成交由应用层或 skipExpression 处理。
     *
     * @param delegateTask Flowable 委托任务
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String taskName = delegateTask.getName();
        String processInstanceId = delegateTask.getProcessInstanceId();
        
        log.info("自动跳过节点: taskName={}, taskId={}, processInstanceId={}", 
                taskName, taskId, processInstanceId);
        
        // 设置流程变量，标记此节点已自动跳过
        delegateTask.setVariable("skipReason_" + delegateTask.getTaskDefinitionKey(), "系统自动跳过此节点");
        delegateTask.setVariable("approved", "approve");
        
        // 注意：由于事务限制，这里不直接调用 complete
        // 而是在应用层查询任务时检测到 skipNode 标记后自动完成
        // 或者使用 Flowable 的 skipExpression 功能
        
        log.info("节点标记为自动跳过，将在查询时处理: taskName={}", taskName);
    }
}
