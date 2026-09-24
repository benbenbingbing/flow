package com.workflow.process.task.infrastructure.flowable;

import com.workflow.process.task.application.ProcessTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Flowable 任务创建监听器
 * 当流程任务创建时，自动同步到本地待办表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCreateListener implements TaskListener {

    /** 流程任务服务，同步任务到本地待办 */
    private final ProcessTaskService processTaskService;
    /** Flowable 运行时服务，读取流程变量 */
    private final RuntimeService runtimeService;

    /**
     * 任务创建回调：将 Flowable 任务连同流程变量同步到本地待办表。
     * <p>
     * 本地任务是收件箱查询的基础，失败必须回滚，不能提交一个没有本地任务的引擎任务。
     *
     * @param delegateTask Flowable 委托任务
     */
    @Override
    public void notify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String taskName = delegateTask.getName();
        String processInstanceId = delegateTask.getProcessInstanceId();
        
        log.info("Flowable任务创建: taskName={}, taskId={}, processInstanceId={}", 
                taskName, taskId, processInstanceId);
        
        try {
            // 获取流程变量
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            
            // 同步到本地待办
            processTaskService.createTask(delegateTask, variables);
            
            log.info("任务同步到本地待办成功: taskId={}", taskId);
        } catch (Exception e) {
            log.error("任务同步到本地待办失败: taskId={}", taskId, e);
            throw new IllegalStateException("本地任务同步失败，回滚流程事务", e);
        }
    }
}
