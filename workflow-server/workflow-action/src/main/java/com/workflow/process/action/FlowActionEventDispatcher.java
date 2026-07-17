package com.workflow.process.action;

import com.workflow.entity.FlowAction;
import com.workflow.entity.FlowActionExecution;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.service.FlowActionExecutionService;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionEventDispatcher implements FlowActionDispatcher {

    private final FlowActionService flowActionService;
    private final FlowActionExecutor flowActionExecutor;
    private final FlowActionExecutionService executionService;
    private final FlowActionTimingCatalog timingCatalog;
    private final ProcessVersionHistoryMapper versionHistoryMapper;
    private final RepositoryService repositoryService;

    @Override
    public void dispatch(FlowActionTriggerEvent event) {
        validateEvent(event);
        String versionId = resolveVersionId(event);
        if (!StringUtils.hasText(versionId)) {
            log.warn("流程动作无法解析发布版本: processDefinitionId={}, processInstanceId={}",
                    event.getProcessDefinitionId(), event.getProcessInstanceId());
            return;
        }
        event.setVersionId(versionId);
        List<FlowAction> actions = flowActionService.findPublishedActionsByBinding(
                versionId,
                event.getScopeType(),
                event.getElementId(),
                event.getTriggerTiming());
        for (FlowAction action : actions) {
            if (!Boolean.TRUE.equals(action.getEnabled())) {
                continue;
            }
            dispatchAction(action, event);
        }
    }

    private void dispatchAction(FlowAction action, FlowActionTriggerEvent event) {
        String executionMode = normalizeExecutionMode(action);
        String failurePolicy = normalizeFailurePolicy(action, executionMode);
        String idempotencyKey = UUID.randomUUID().toString();
        if (FlowActionExecutionMode.AFTER_COMMIT.name().equals(executionMode)) {
            executionService.create(
                    action,
                    event,
                    idempotencyKey,
                    FlowActionExecution.Status.PENDING);
            return;
        }

        FlowActionExecution execution = executionService.createInTransactionAudit(
                action,
                event,
                idempotencyKey,
                FlowActionExecution.Status.RUNNING);
        try {
            FlowActionContext context = flowActionExecutor.executeAction(
                    action,
                    event,
                    idempotencyKey,
                    execution);
            executionService.markSuccess(execution, context);
        } catch (RuntimeException e) {
            executionService.markFinalFailure(execution, e);
            if (FlowActionFailurePolicy.CONTINUE.name().equals(failurePolicy)) {
                log.error("事务内流程动作失败，按 CONTINUE 策略继续: actionId={}, actionName={}",
                        action.getId(), action.getActionName(), e);
                return;
            }
            throw e;
        }
    }

    private void validateEvent(FlowActionTriggerEvent event) {
        if (event == null || !StringUtils.hasText(event.getTriggerTiming())) {
            throw new RuntimeException("流程动作触发事件不能为空");
        }
        if (timingCatalog.find(event.getTriggerTiming()).isEmpty()) {
            throw new RuntimeException("未注册的流程动作时机: " + event.getTriggerTiming());
        }
        if (!StringUtils.hasText(event.getScopeType())) {
            throw new RuntimeException("流程动作事件作用域不能为空");
        }
        if (!FlowActionScopeType.PROCESS.name().equals(event.getScopeType())
                && !StringUtils.hasText(event.getElementId())) {
            throw new RuntimeException("节点或连线动作事件缺少元素 ID");
        }
    }

    private String resolveVersionId(FlowActionTriggerEvent event) {
        if (StringUtils.hasText(event.getVersionId())) {
            return event.getVersionId();
        }
        if (!StringUtils.hasText(event.getProcessDefinitionId())) {
            return null;
        }
        try {
            ProcessDefinition definition = repositoryService.getProcessDefinition(event.getProcessDefinitionId());
            if (definition != null && StringUtils.hasText(definition.getDeploymentId())) {
                return versionHistoryMapper.findByDeploymentId(definition.getDeploymentId())
                        .map(ProcessVersionHistory::getId)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("解析流程动作发布版本失败: processDefinitionId={}",
                    event.getProcessDefinitionId(), e);
        }
        return null;
    }

    private String normalizeExecutionMode(FlowAction action) {
        if (StringUtils.hasText(action.getExecutionMode())) {
            return action.getExecutionMode().toUpperCase(Locale.ROOT);
        }
        return FlowActionExecutionMode.IN_TRANSACTION.name();
    }

    private String normalizeFailurePolicy(FlowAction action, String executionMode) {
        if (StringUtils.hasText(action.getFailurePolicy())) {
            return action.getFailurePolicy().toUpperCase(Locale.ROOT);
        }
        return FlowActionExecutionMode.AFTER_COMMIT.name().equals(executionMode)
                ? FlowActionFailurePolicy.RETRY.name()
                : FlowActionFailurePolicy.ROLLBACK.name();
    }
}
