package com.workflow.process.open.application;

import com.workflow.contracts.process.open.OpenProcessEvent;
import com.workflow.contracts.process.open.OpenProcessEventPort;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Maps relevant Flowable lifecycle events to the open-integration event port.
 */
@Component
public class OpenIntegrationProcessEventListener
        implements FlowableEventListener {

    private static final Set<FlowableEngineEventType> EVENT_TYPES =
            EnumSet.of(
                    FlowableEngineEventType.PROCESS_STARTED,
                    FlowableEngineEventType.PROCESS_COMPLETED,
                    FlowableEngineEventType
                            .PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT,
                    FlowableEngineEventType
                            .PROCESS_COMPLETED_WITH_ERROR_END_EVENT,
                    FlowableEngineEventType
                            .PROCESS_COMPLETED_WITH_ESCALATION_END_EVENT,
                    FlowableEngineEventType.PROCESS_CANCELLED,
                    FlowableEngineEventType.TASK_CREATED,
                    FlowableEngineEventType.TASK_COMPLETED);

    private final OpenProcessEventPort eventPort;
    private final Clock clock;

    @Autowired
    public OpenIntegrationProcessEventListener(
            OpenProcessEventPort eventPort) {
        this(eventPort, Clock.systemUTC());
    }

    OpenIntegrationProcessEventListener(
            OpenProcessEventPort eventPort,
            Clock clock) {
        this.eventPort = eventPort;
        this.clock = clock;
    }

    @Override
    public void onEvent(FlowableEvent rawEvent) {
        if (!(rawEvent instanceof FlowableEngineEvent event)
                || !(rawEvent.getType()
                instanceof FlowableEngineEventType type)) {
            return;
        }
        String eventType = externalType(type);
        if (eventType == null) {
            return;
        }
        Task task = rawEvent instanceof FlowableEntityEvent entity
                && entity.getEntity() instanceof Task value
                ? value
                : null;
        String taskId = task == null ? null : task.getId();
        String traceId = traceId(rawEvent, task);
        String eventKey = type.name()
                + ":"
                + event.getProcessInstanceId()
                + (taskId == null ? "" : ":" + taskId);
        eventPort.publish(new OpenProcessEvent(
                eventKey,
                eventType,
                event.getProcessInstanceId(),
                taskId,
                task == null ? null : task.getTaskDefinitionKey(),
                task == null ? null : task.getName(),
                traceId,
                clock.instant(),
                attributes(rawEvent, task)));
    }

    private String traceId(FlowableEvent event, Task task) {
        Object value = task == null
                || task.getProcessVariables() == null
                ? null
                : task.getProcessVariables().get(
                        "integrationTraceId");
        if (value == null
                && event instanceof FlowableEntityEvent entity
                && entity.getEntity()
                instanceof ProcessInstance process
                && process.getProcessVariables() != null) {
            value = process.getProcessVariables().get(
                    "integrationTraceId");
        }
        return value instanceof String text && !text.isBlank()
                ? text
                : null;
    }

    private Map<String, Object> attributes(
            FlowableEvent event,
            Task task) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (task != null && task.getProcessVariables() != null) {
            variables.putAll(task.getProcessVariables());
        } else if (event instanceof FlowableEntityEvent entity
                && entity.getEntity() instanceof ProcessInstance process
                && process.getProcessVariables() != null) {
            variables.putAll(process.getProcessVariables());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        copyVariable(variables, result, "outcomeCode");
        copyVariable(variables, result, "outcome");
        copyVariable(variables, result, "approver", "actorId");
        copyVariable(variables, result, "approvalEvidence", "evidence");
        copyVariable(variables, result, "decidedAt");
        copyVariable(variables, result, "opinion");
        copyVariable(variables, result, "reasonCode");
        copyVariable(variables, result, "failureCode");
        return Map.copyOf(result);
    }

    private void copyVariable(Map<String, Object> source,
                              Map<String, Object> target,
                              String sourceKey) {
        copyVariable(source, target, sourceKey, sourceKey);
    }

    private void copyVariable(Map<String, Object> source,
                              Map<String, Object> target,
                              String sourceKey,
                              String targetKey) {
        Object value = source.get(sourceKey);
        if (value instanceof String || value instanceof Number
                || value instanceof Boolean) {
            target.put(targetKey, value);
        }
    }

    private String externalType(FlowableEngineEventType type) {
        return switch (type) {
            case PROCESS_STARTED -> "com.flow.process.started.v1";
            case PROCESS_COMPLETED -> "com.flow.process.completed.v1";
            case PROCESS_CANCELLED,
                 PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT ->
                    "com.flow.process.terminated.v1";
            case PROCESS_COMPLETED_WITH_ERROR_END_EVENT,
                 PROCESS_COMPLETED_WITH_ESCALATION_END_EVENT ->
                    "com.flow.process.failed.v1";
            case TASK_CREATED -> "com.flow.task.created.v1";
            case TASK_COMPLETED -> "com.flow.task.completed.v1";
            default -> null;
        };
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
    public Collection<? extends
            org.flowable.common.engine.api.delegate.event.FlowableEventType>
            getTypes() {
        return EVENT_TYPES;
    }
}
