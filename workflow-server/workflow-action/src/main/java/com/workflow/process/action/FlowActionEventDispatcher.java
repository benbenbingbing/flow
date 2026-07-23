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

/**
 * 流程动作事件分发器。
 *
 * <p>将 Flowable 引擎事件转换后的 {@link FlowActionTriggerEvent} 解析到对应的已发布版本，
 * 查出该版本下匹配的已启用动作，并按其执行方式（事务内 / 提交后）与失败策略进行分发执行。</p>
 */
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

    /**
     * 分发触发事件：解析版本、查询匹配的已发布动作并逐一执行。
     *
     * @param event 流程动作触发事件
     */
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
            // 仅处理启用状态的动作，未启用的直接跳过
            if (!Boolean.TRUE.equals(action.getEnabled())) {
                continue;
            }
            dispatchAction(action, event);
        }
    }

    /**
     * 分发单个动作：根据执行方式决定是入队提交后执行还是事务内立即执行。
     *
     * @param action 已发布动作配置
     * @param event  触发事件
     */
    private void dispatchAction(FlowAction action, FlowActionTriggerEvent event) {
        String executionMode = normalizeExecutionMode(action);
        String failurePolicy = normalizeFailurePolicy(action, executionMode);
        String idempotencyKey = UUID.randomUUID().toString();
        // 提交后执行：仅写入 PENDING 执行记录，由发件箱工作线程异步处理
        if (FlowActionExecutionMode.AFTER_COMMIT.name().equals(executionMode)) {
            executionService.create(
                    action,
                    event,
                    idempotencyKey,
                    FlowActionExecution.Status.PENDING);
            return;
        }

        // 事务内执行：先创建 RUNNING 记录，再调用处理器，按失败策略决定是否抛出回滚
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
            // CONTINUE 策略：仅记录失败，不影响主流程事务
            if (FlowActionFailurePolicy.CONTINUE.name().equals(failurePolicy)) {
                log.error("事务内流程动作失败，按 CONTINUE 策略继续: actionId={}, actionName={}",
                        action.getId(), action.getActionName(), e);
                return;
            }
            // ROLLBACK 策略：抛出异常使主流程事务回滚
            throw e;
        }
    }

    /**
     * 校验触发事件必填字段及触发时机是否已注册。
     *
     * @param event 触发事件
     * @throws RuntimeException 事件为空、时机未注册或作用域信息缺失时抛出
     */
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
        // 非流程级动作必须携带具体的 BPMN 元素 ID
        if (!FlowActionScopeType.PROCESS.name().equals(event.getScopeType())
                && !StringUtils.hasText(event.getElementId())) {
            throw new RuntimeException("节点或连线动作事件缺少元素 ID");
        }
    }

    /**
     * 解析触发事件对应的流程发布版本 ID。
     *
     * <p>优先使用事件自带的版本 ID；否则通过 Flowable 流程定义 ID 反查部署 ID，
     * 再由部署 ID 关联到流程版本历史记录。</p>
     *
     * @param event 触发事件
     * @return 版本 ID；解析失败返回 null
     */
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

    /**
     * 归一化执行方式，缺省按事务内执行处理。
     *
     * @param action 动作配置
     * @return 执行方式枚举名称（大写）
     */
    private String normalizeExecutionMode(FlowAction action) {
        if (StringUtils.hasText(action.getExecutionMode())) {
            return action.getExecutionMode().toUpperCase(Locale.ROOT);
        }
        return FlowActionExecutionMode.IN_TRANSACTION.name();
    }

    /**
     * 归一化失败策略：已配置则直接使用，否则按执行方式给出默认值。
     *
     * @param action        动作配置
     * @param executionMode 已归一化的执行方式
     * @return 失败策略枚举名称（大写）
     */
    private String normalizeFailurePolicy(FlowAction action, String executionMode) {
        if (StringUtils.hasText(action.getFailurePolicy())) {
            return action.getFailurePolicy().toUpperCase(Locale.ROOT);
        }
        return FlowActionExecutionMode.AFTER_COMMIT.name().equals(executionMode)
                ? FlowActionFailurePolicy.RETRY.name()
                : FlowActionFailurePolicy.ROLLBACK.name();
    }
}
