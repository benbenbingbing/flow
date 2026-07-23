package com.workflow.process.action;

import com.workflow.entity.FlowAction;
import com.workflow.entity.FlowActionExecution;
import com.workflow.mapper.FlowActionMapper;
import com.workflow.service.FlowActionExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程动作发件箱执行处理器。
 *
 * <p>在工作线程抢占到执行记录后，以独立新事务调用动作执行器，
     * 并根据失败策略决定是直接标记死信（IGNORE）还是安排重试（RETRY）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowActionOutboxProcessor {

    private final FlowActionExecutionService executionService;
    private final FlowActionMapper flowActionMapper;
    private final FlowActionExecutor flowActionExecutor;

    /**
     * 在新事务中处理单条执行记录：读取触发事件、调用处理器并按结果更新状态。
     *
     * @param executionId 执行记录 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String executionId) {
        FlowActionExecution execution = executionService.get(executionId);
        // 仅处理抢占后仍处于 RUNNING 状态的记录，避免并发重复执行
        if (execution == null || !FlowActionExecution.Status.RUNNING.name().equals(execution.getStatus())) {
            return;
        }
        FlowAction action = flowActionMapper.selectById(execution.getActionId());
        if (action == null) {
            executionService.markFinalFailure(execution, new RuntimeException("流程动作配置不存在"));
            return;
        }
        try {
            FlowActionTriggerEvent event = executionService.readEvent(execution);
            FlowActionContext context = flowActionExecutor.executeAction(
                    action,
                    event,
                    execution.getIdempotencyKey(),
                    execution);
            executionService.markSuccess(execution, context);
        } catch (Exception e) {
            // IGNORE 策略：直接进入死信，不再重试
            if (FlowActionFailurePolicy.IGNORE.name().equalsIgnoreCase(action.getFailurePolicy())) {
                executionService.markFinalFailure(execution, e);
                log.warn("提交后流程动作失败，按 IGNORE 策略结束: executionId={}, actionId={}",
                        executionId, action.getId(), e);
            } else {
                // 默认 RETRY 策略：安排下一次重试
                executionService.markRetryFailure(execution, e);
                log.warn("提交后流程动作失败，已安排重试: executionId={}, actionId={}, retryCount={}",
                        executionId, action.getId(), execution.getRetryCount(), e);
            }
        }
    }
}
