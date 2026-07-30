package com.workflow.process.action.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionFailurePolicy;
import com.workflow.process.action.domain.FlowActionTriggerEvent;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * 流程动作执行队列处理器。
 *
 * <p>在工作线程抢占到执行记录后，以独立新事务调用动作执行器，
     * 并根据失败策略决定是直接标记死信（IGNORE）还是安排重试（RETRY）。</p>
 */
@Slf4j
@Service
public class FlowActionExecutionProcessor {

    private final FlowActionExecutionService executionService;
    private final FlowActionMapper flowActionMapper;
    private final FlowActionExecutor flowActionExecutor;
    private final TaskScheduler heartbeatScheduler;

    public FlowActionExecutionProcessor(
            FlowActionExecutionService executionService,
            FlowActionMapper flowActionMapper,
            FlowActionExecutor flowActionExecutor,
            @Qualifier("flowActionHeartbeatScheduler") TaskScheduler heartbeatScheduler) {
        this.executionService = executionService;
        this.flowActionMapper = flowActionMapper;
        this.flowActionExecutor = flowActionExecutor;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    /**
     * 处理单条已认领记录：读取触发事件、调用处理器并以独立事务更新状态。
     *
     * @param executionId 执行记录 ID
     * @param ownerId 当前租约所有者
     * @param leaseToken fencing token
     * @param leaseSeconds 心跳续租时长
     */
    public void process(
            String executionId,
            String ownerId,
            long leaseToken,
            int leaseSeconds) {
        FlowActionExecution execution =
                executionService.getClaimed(executionId, ownerId);
        // 仅处理抢占后仍处于 RUNNING 状态的记录，避免并发重复执行
        if (execution == null
                || execution.getLeaseToken() == null
                || execution.getLeaseToken() != leaseToken) {
            return;
        }
        Duration heartbeatPeriod = Duration.ofSeconds(
                Math.max(1, leaseSeconds / 3));
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> heartbeat(
                        executionId, ownerId, leaseToken, leaseSeconds),
                Instant.now().plus(heartbeatPeriod),
                heartbeatPeriod);
        try {
            FlowAction action = flowActionMapper.selectById(execution.getActionId());
            if (action == null) {
                executionService.markFinalFailure(
                        execution,
                        new RuntimeException("流程动作配置不存在"));
                return;
            }
            String previousUserId = UserContext.getUserId();
            String previousUsername = UserContext.getUsername();
            boolean retryable = false;
            try {
                retryable = flowActionExecutor.retryable(action);
                FlowActionTriggerEvent event = executionService.readEvent(execution);
                restoreOperatorContext(event);
                FlowActionContext context = flowActionExecutor.executeAction(
                        action,
                        event,
                        execution.getIdempotencyKey(),
                        execution);
                executionService.markSuccess(execution, context);
            } catch (Exception e) {
                boolean retryPolicy =
                        FlowActionFailurePolicy.RETRY.name()
                                .equalsIgnoreCase(action.getFailurePolicy());
                if (retryPolicy && retryable) {
                    executionService.markRetryFailure(execution, e);
                    log.warn("提交后流程动作失败，已安排幂等重试: executionId={}, actionId={}, retryCount={}",
                            executionId, action.getId(), execution.getRetryCount(), e);
                } else {
                    executionService.markFinalFailure(execution, e);
                    log.warn("提交后流程动作失败，处理器未声明可安全重试: executionId={}, actionId={}",
                            executionId, action.getId(), e);
                }
            } finally {
                restorePreviousContext(previousUserId, previousUsername);
            }
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void heartbeat(
            String executionId,
            String ownerId,
            long leaseToken,
            int leaseSeconds) {
        try {
            if (!executionService.heartbeat(
                    executionId, ownerId, leaseToken, leaseSeconds)) {
                log.warn("流程动作心跳被 fencing 拒绝: id={}, owner={}, token={}",
                        executionId, ownerId, leaseToken);
            }
        } catch (RuntimeException exception) {
            log.error("流程动作心跳失败，将在下一周期重试: id={}, owner={}",
                    executionId, ownerId, exception);
        }
    }

    private void restoreOperatorContext(FlowActionTriggerEvent event) {
        UserContext.clear();
        if (event == null || !StringUtils.hasText(event.getOperatorId())) {
            return;
        }
        String username = StringUtils.hasText(event.getOperatorName())
                ? event.getOperatorName()
                : event.getOperatorId();
        UserContext.setCurrentUser(event.getOperatorId(), username);
    }

    private void restorePreviousContext(String userId, String username) {
        UserContext.clear();
        if (StringUtils.hasText(userId)) {
            UserContext.setCurrentUser(
                    userId,
                    StringUtils.hasText(username) ? username : userId);
        }
    }
}
