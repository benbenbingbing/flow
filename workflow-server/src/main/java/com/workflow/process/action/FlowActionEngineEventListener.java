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

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowActionEngineEventListener implements FlowableEventListener {

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

    @Override
    public void onEvent(FlowableEvent event) {
        FlowActionTriggerEvent triggerEvent = map(event);
        if (triggerEvent != null) {
            dispatcher.dispatch(triggerEvent);
        }
    }

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

    private FlowActionTriggerEvent base(FlowableEngineEvent event) {
        FlowActionTriggerEvent trigger = new FlowActionTriggerEvent();
        trigger.setProcessDefinitionId(event.getProcessDefinitionId());
        trigger.setProcessInstanceId(event.getProcessInstanceId());
        trigger.setExecutionId(event.getExecutionId());
        trigger.setOperatorId(firstNonBlank(UserContext.getUserId(), UserContext.getUsername()));
        return trigger;
    }

    private void processEvent(
            FlowActionTriggerEvent trigger,
            FlowActionTriggerTiming timing,
            String endReason) {
        trigger.setScopeType(FlowActionScopeType.PROCESS.name());
        trigger.setTriggerTiming(timing.name());
        trigger.setEndReason(endReason);
    }

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

    private void populateVariables(FlowActionTriggerEvent trigger, FlowableEvent event) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (trigger.getProcessInstanceId() != null) {
            try {
                variables.putAll(runtimeService.getVariables(trigger.getProcessInstanceId()));
            } catch (Exception ignored) {
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
        trigger.setApprovalAction(firstNonBlank(
                stringValue(variables.get("action")),
                stringValue(variables.get("approved"))));
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
