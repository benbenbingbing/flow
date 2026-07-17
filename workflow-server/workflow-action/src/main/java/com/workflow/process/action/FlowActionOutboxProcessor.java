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

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowActionOutboxProcessor {

    private final FlowActionExecutionService executionService;
    private final FlowActionMapper flowActionMapper;
    private final FlowActionExecutor flowActionExecutor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String executionId) {
        FlowActionExecution execution = executionService.get(executionId);
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
            if (FlowActionFailurePolicy.IGNORE.name().equalsIgnoreCase(action.getFailurePolicy())) {
                executionService.markFinalFailure(execution, e);
                log.warn("提交后流程动作失败，按 IGNORE 策略结束: executionId={}, actionId={}",
                        executionId, action.getId(), e);
            } else {
                executionService.markRetryFailure(execution, e);
                log.warn("提交后流程动作失败，已安排重试: executionId={}, actionId={}, retryCount={}",
                        executionId, action.getId(), execution.getRetryCount(), e);
            }
        }
    }
}
