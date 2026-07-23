package com.workflow.process.action;

import com.workflow.common.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.delegate.event.FlowableCancelledEvent;
import org.flowable.engine.delegate.event.FlowableEntityWithVariablesEvent;
import org.flowable.engine.delegate.event.FlowableSequenceFlowTakenEvent;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flowable 引擎事件监听器。
 *
 * <p>统一监听流程、活动、任务、顺序流等关键事件，将其映射为流程动作触发事件
 * 并交由 {@link FlowActionDispatcher} 分发到对应已发布动作。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionEngineEventListener implements FlowableEventListener {

    /** 本监听器关注的事件类型集合 */
    private static final Collection<FlowableEngineEventType> EVENT_TYPES = EnumSet.of(
            FlowableEngineEventType.PROCESS_STARTED,
            FlowableEngineEventType.PROCESS_COMPLETED,
            FlowableEngineEventType.PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT,
            FlowableEngineEventType.PROCESS_COMPLETED_WITH_ERROR_END_EVENT,
            FlowableEngineEventType.PROCESS_COMPLETED_WITH_ESCALATION_END_EVENT,
            FlowableEngineEventType.PROCESS_CANCELLED,
            FlowableEngineEventType.ACTIVITY_STARTED,
            FlowableEngineEventType.ACTIVITY_COMPLETED,
            FlowableEngineEventType.TASK_CREATED,
            FlowableEngineEventType.TASK_ASSIGNED,
            FlowableEngineEventType.TASK_COMPLETED,
            FlowableEngineEventType.SEQUENCEFLOW_TAKEN);

    private final FlowActionDispatcher dispatcher;
    private final RuntimeService runtimeService;
    private final FlowActionHelper flowActionHelper;

    /**
     * 接收引擎事件并转换为流程动作触发事件进行分发。
     *
     * @param event Flowable 引擎事件
     */
    @Override
    public void onEvent(FlowableEvent event) {
        FlowActionTriggerEvent triggerEvent = map(event);
        if (triggerEvent != null) {
            dispatcher.dispatch(triggerEvent);
        }
    }

    /**
     * 将 Flowable 引擎事件映射为流程动作触发事件，并按事件类型填充作用域与元素信息。
     *
     * @param event Flowable 引擎事件
     * @return 流程动作触发事件；非关注事件返回 null
     * @throws RuntimeException 事件缺少必要的活动/任务上下文时抛出
     */
    private FlowActionTriggerEvent map(FlowableEvent event) {
        if (!(event instanceof FlowableEngineEvent engineEvent)) {
            return null;
        }
        FlowActionTriggerEvent trigger = base(engineEvent);
        FlowableEngineEventType type = (FlowableEngineEventType) event.getType();
        switch (type) {
            case PROCESS_STARTED -> processEvent(trigger, FlowActionTriggerTiming.PROCESS_STARTED, null);
            case PROCESS_COMPLETED -> processEvent(trigger, FlowActionTriggerTiming.PROCESS_COMPLETED, null);
            case PROCESS_CANCELLED -> {
                // 撤回与终止通过取消原因区分：包含"撤回"字样视为撤回
                String reason = event instanceof FlowableCancelledEvent cancelled
                        ? stringValue(cancelled.getCause())
                        : null;
                FlowActionTriggerTiming timing = reason != null && reason.contains("撤回")
                        ? FlowActionTriggerTiming.PROCESS_WITHDRAWN
                        : FlowActionTriggerTiming.PROCESS_TERMINATED;
                processEvent(trigger, timing, reason);
            }
            case PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT,
                 PROCESS_COMPLETED_WITH_ERROR_END_EVENT,
                 PROCESS_COMPLETED_WITH_ESCALATION_END_EVENT ->
                    processEvent(trigger, FlowActionTriggerTiming.PROCESS_TERMINATED, type.name());
            case ACTIVITY_STARTED -> activityEvent(trigger, event, FlowActionTriggerTiming.NODE_ENTERED);
            case ACTIVITY_COMPLETED -> activityEvent(trigger, event, FlowActionTriggerTiming.NODE_COMPLETED);
            case TASK_CREATED -> taskEvent(trigger, event, FlowActionTriggerTiming.TASK_CREATED);
            case TASK_ASSIGNED -> taskEvent(trigger, event, FlowActionTriggerTiming.TASK_ASSIGNED);
            case TASK_COMPLETED -> taskEvent(trigger, event, FlowActionTriggerTiming.TASK_COMPLETING);
            case SEQUENCEFLOW_TAKEN -> sequenceFlowEvent(trigger, event);
            default -> {
                return null;
            }
        }
        populateVariables(trigger, event);
        return trigger;
    }

    /**
     * 填充触发事件的基础标识：流程定义 ID、流程实例 ID、执行 ID 与操作人。
     *
     * @param event Flowable 引擎事件
     * @return 已填充基础字段的触发事件
     */
    private FlowActionTriggerEvent base(FlowableEngineEvent event) {
        FlowActionTriggerEvent trigger = new FlowActionTriggerEvent();
        trigger.setProcessDefinitionId(event.getProcessDefinitionId());
        trigger.setProcessInstanceId(event.getProcessInstanceId());
        trigger.setExecutionId(event.getExecutionId());
        trigger.setOperatorId(firstNonBlank(UserContext.getUserId(), UserContext.getUsername()));
        return trigger;
    }

    /**
     * 设置流程级事件的作用域、触发时机与结束原因。
     *
     * @param trigger   触发事件
     * @param timing    触发时机
     * @param endReason 结束原因；无则传 null
     */
    private void processEvent(
            FlowActionTriggerEvent trigger,
            FlowActionTriggerTiming timing,
            String endReason) {
        trigger.setScopeType(FlowActionScopeType.PROCESS.name());
        trigger.setTriggerTiming(timing.name());
        trigger.setEndReason(endReason);
    }

    /**
     * 设置活动级事件的作用域、元素 ID/名称/类型与触发时机。
     *
     * @param trigger 触发事件
     * @param event   Flowable 事件
     * @param timing  触发时机
     * @throws RuntimeException 事件非活动事件时抛出
     */
    private void activityEvent(
            FlowActionTriggerEvent trigger,
            FlowableEvent event,
            FlowActionTriggerTiming timing) {
        if (!(event instanceof FlowableActivityEvent activity)) {
            throw new RuntimeException("活动事件缺少活动上下文");
        }
        trigger.setScopeType(FlowActionScopeType.NODE.name());
        trigger.setElementId(activity.getActivityId());
        trigger.setElementName(activity.getActivityName());
        trigger.setElementType(activity.getActivityType());
        trigger.setTriggerTiming(timing.name());
    }

    /**
     * 设置任务级事件的作用域、任务信息与触发时机。
     *
     * @param trigger 触发事件
     * @param event   Flowable 事件
     * @param timing  触发时机
     * @throws RuntimeException 事件缺少任务上下文时抛出
     */
    private void taskEvent(
            FlowActionTriggerEvent trigger,
            FlowableEvent event,
            FlowActionTriggerTiming timing) {
        if (!(event instanceof FlowableEntityEvent entityEvent)
                || !(entityEvent.getEntity() instanceof Task task)) {
            throw new RuntimeException("任务事件缺少任务上下文");
        }
        trigger.setScopeType(FlowActionScopeType.NODE.name());
        trigger.setElementId(task.getTaskDefinitionKey());
        trigger.setElementName(task.getName());
        trigger.setElementType("userTask");
        trigger.setTaskId(task.getId());
        trigger.setTaskName(task.getName());
        trigger.setTaskAssignee(task.getAssignee());
        trigger.setTriggerTiming(timing.name());
    }

    /**
     * 设置顺序流事件的作用域、源/目标节点信息与触发时机。
     *
     * @param trigger 触发事件
     * @param event   Flowable 事件
     * @throws RuntimeException 事件非顺序流事件时抛出
     */
    private void sequenceFlowEvent(FlowActionTriggerEvent trigger, FlowableEvent event) {
        if (!(event instanceof FlowableSequenceFlowTakenEvent sequenceFlow)) {
            throw new RuntimeException("顺序流事件缺少连线上下文");
        }
        trigger.setScopeType(FlowActionScopeType.SEQUENCE_FLOW.name());
        trigger.setElementId(sequenceFlow.getId());
        trigger.setElementType("sequenceFlow");
        trigger.setSourceNodeId(sequenceFlow.getSourceActivityId());
        trigger.setSourceNodeName(sequenceFlow.getSourceActivityName());
        trigger.setTargetNodeId(sequenceFlow.getTargetActivityId());
        trigger.setTargetNodeName(sequenceFlow.getTargetActivityName());
        trigger.setTriggerTiming(FlowActionTriggerTiming.TRANSITION_TAKEN.name());
    }

    /**
     * 合并运行时变量与事件携带变量，并解析实体编码、审批动作、操作人等业务字段。
     *
     * @param trigger 触发事件
     * @param event   Flowable 事件
     */
    private void populateVariables(FlowActionTriggerEvent trigger, FlowableEvent event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (trigger.getProcessInstanceId() != null) {
            try {
                variables.putAll(runtimeService.getVariables(trigger.getProcessInstanceId()));
            } catch (Exception ignored) {
                // 运行实例已结束则回退到历史变量查询
                variables.putAll(flowActionHelper.getVariables(trigger.getProcessInstanceId()));
            }
        }
        if (event instanceof FlowableEntityWithVariablesEvent variablesEvent
                && variablesEvent.getVariables() != null) {
            variables.putAll(variablesEvent.getVariables());
        }
        trigger.setVariables(variables);
        trigger.setEntityCode(stringValue(variables.get("entityCode")));
        trigger.setEntityDataId(stringValue(variables.get("entityDataId")));
        // 审批动作兼容 action 与 approved 两种变量名
        trigger.setApprovalAction(firstNonBlank(
                stringValue(variables.get("action")),
                stringValue(variables.get("approved"))));
        // 操作人优先使用事件携带值，其次回退到 approver 或 initiator
        trigger.setOperatorId(firstNonBlank(
                trigger.getOperatorId(),
                stringValue(variables.get("approver")),
                stringValue(variables.get("initiator"))));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public boolean isFailOnException() {
        return true;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    @Override
    public Collection<? extends org.flowable.common.engine.api.delegate.event.FlowableEventType> getTypes() {
        return EVENT_TYPES;
    }
}
