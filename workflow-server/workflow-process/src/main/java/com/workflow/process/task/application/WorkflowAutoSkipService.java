package com.workflow.process.task.application;

import com.workflow.process.definition.application.DeployedSkipExpressionSafety;
import com.workflow.process.instance.application.WorkflowReservedVariables;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Flowable 原生用户任务跳过表达式的存量实例兼容监听器。
 *
 * <p>{@code ACTIVITY_STARTED} 在 {@code UserTaskActivityBehavior.execute}
 * 的 skipExpression 判断之前触发。新实例在启动入口已经注入可信开关；本监听器只为
 * 升级前启动、尚未到达用户任务的在途实例补齐该开关。任务是否跳过完全由部署版本中的
 * {@code flowable:skipExpression} 决定，本服务不得查询或自动完成任务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowAutoSkipService implements FlowableEventListener {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    /**
     * 在用户任务行为执行前补齐原生 skipExpression 的可信启用变量。
     *
     * <p>Flowable 会优先读取旧 Activiti 变量，因此必须先删除该兼容键；否则存量
     * {@code false} 会遮蔽平台写入的 Flowable 开关。安全模型的兼容变量写入异常
     * 不阻断流程，节点会按普通用户任务停留；不安全模型若无法清除旧开关则中止命令。</p>
     *
     * @param event Flowable 运行时事件
     */
    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableActivityEvent activityEvent)
                || event.getType()
                != FlowableEngineEventType.ACTIVITY_STARTED
                || !StringUtils.hasText(
                        activityEvent.getProcessInstanceId())) {
            return;
        }

        String processInstanceId = activityEvent.getProcessInstanceId();
        String unsafeSkipElement =
                DeployedSkipExpressionSafety.firstUnsafeElementId(
                        repositoryService.getBpmnModel(
                                activityEvent.getProcessDefinitionId()));
        if (unsafeSkipElement != null) {
            disableUnsafeSkipExpressions(activityEvent);
            log.warn(
                    "历史部署包含不安全 skipExpression，已禁用原生跳过并保留正常流转: processDefinitionId={}, element={}",
                    activityEvent.getProcessDefinitionId(),
                    unsafeSkipElement);
            return;
        }
        if (!"userTask".equals(activityEvent.getActivityType())) {
            return;
        }
        try {
            Object legacyOverride = runtimeService.getVariable(
                    processInstanceId,
                    WorkflowReservedVariables
                            .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE);
            if (legacyOverride != null) {
                runtimeService.removeVariable(
                        processInstanceId,
                        WorkflowReservedVariables
                                .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE);
            }
            Object legacySkipEnabled = runtimeService.getVariable(
                    processInstanceId,
                    WorkflowReservedVariables
                            .LEGACY_SKIP_NODE_ENABLED_VARIABLE);
            if (!Boolean.TRUE.equals(legacySkipEnabled)) {
                // 升级前的部署版本仍可能使用 ${skipNodeEnabled}。
                runtimeService.setVariable(
                        processInstanceId,
                        WorkflowReservedVariables
                                .LEGACY_SKIP_NODE_ENABLED_VARIABLE,
                        true);
            }
            Object enabled = runtimeService.getVariable(
                    processInstanceId,
                    WorkflowReservedVariables
                            .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE);
            if (!Boolean.TRUE.equals(enabled)) {
                runtimeService.setVariable(
                        processInstanceId,
                        WorkflowReservedVariables
                                .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE,
                        true);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "补齐原生跳过表达式开关失败，用户任务将保留人工办理兜底: processInstanceId={}, activityId={}",
                    processInstanceId,
                    activityEvent.getActivityId(),
                    exception);
        }
    }

    /**
     * 禁用当前执行上的不安全跳过表达式。
     *
     * <p>多实例 {@code elementVariable} 会先于子活动启动写入 execution-local
     * 变量，因此仅清理流程实例根变量无法阻止局部 {@code true}。在当前
     * execution 写入优先级更高的 Activiti {@code false}，同时清理根与当前
     * execution 的 Flowable 开关，使存量恶意模型只能按普通节点执行。</p>
     *
     * @param activityEvent 即将执行 activity behavior 的启动事件
     */
    private void disableUnsafeSkipExpressions(
            FlowableActivityEvent activityEvent) {
        String processInstanceId = activityEvent.getProcessInstanceId();
        String executionId = activityEvent.getExecutionId();
        if (!StringUtils.hasText(executionId)) {
            throw new IllegalStateException(
                    "无法禁用不安全 skipExpression: executionId 为空");
        }

        runtimeService.removeVariable(
                processInstanceId,
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE);
        runtimeService.removeVariable(
                processInstanceId,
                WorkflowReservedVariables
                        .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE);
        if (!processInstanceId.equals(executionId)) {
            runtimeService.removeVariableLocal(
                    executionId,
                    WorkflowReservedVariables
                            .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE);
        }
        // Activiti 键的读取优先级高于 Flowable 键，local=false 可遮蔽任意父作用域。
        runtimeService.setVariableLocal(
                executionId,
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE,
                false);
    }

    @Override
    public boolean isFailOnException() {
        // 安全模型的兼容变量写入异常已在 onEvent 内降级为人工待办；
        // 不安全模型若无法清除继承开关则必须中止当前引擎命令。
        return true;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }
}
