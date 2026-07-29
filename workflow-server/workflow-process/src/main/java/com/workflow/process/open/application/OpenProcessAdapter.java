package com.workflow.process.open.application;

import com.workflow.contracts.process.open.OpenApplicationActor;
import com.workflow.contracts.process.open.OpenMessageCorrelationCommand;
import com.workflow.contracts.process.open.OpenMessageCorrelationResult;
import com.workflow.contracts.process.open.OpenProcessCatalogPort;
import com.workflow.contracts.process.open.OpenProcessDefinition;
import com.workflow.contracts.process.open.OpenProcessNotFoundException;
import com.workflow.contracts.process.open.OpenProcessRuntimePort;
import com.workflow.contracts.process.open.OpenProcessStartCommand;
import com.workflow.contracts.process.open.OpenProcessStateConflictException;
import com.workflow.contracts.process.open.OpenProcessView;
import com.workflow.contracts.process.open.OpenTaskView;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.WorkflowAutoSkipService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class OpenProcessAdapter
        implements OpenProcessCatalogPort, OpenProcessRuntimePort {

    private static final String MESSAGE_EVENT_TYPE = "message";
    private static final Set<String> RESERVED_VARIABLES = Set.of(
            "initiator",
            "submitterId",
            "submitterName",
            "entityCode",
            "entityDataId",
            "dataNo",
            "skipNodeEnabled",
            "integrationApplicationId",
            "integrationTraceId",
            "integrationBusinessSystem",
            "integrationBusinessType",
            "integrationBusinessId",
            "integrationExternalInitiatorId");

    private final ProcessDefinitionConfigMapper processDefinitionMapper;
    private final ProcessVersionHistoryMapper processVersionMapper;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final org.flowable.engine.TaskService taskService;
    private final MultiInstanceCollectionListener multiInstanceListener;
    private final WorkflowAutoSkipService autoSkipService;
    private final ProcessTaskService processTaskService;

    @Override
    @Transactional(readOnly = true)
    public List<OpenProcessDefinition> listPublished(
            Collection<String> processKeys,
            OpenApplicationActor actor) {
        if (processKeys == null || processKeys.isEmpty()) {
            return List.of();
        }
        return processDefinitionMapper.findPublishedByKeys(processKeys)
                .stream()
                .map(this::toDefinition)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenProcessView start(OpenProcessStartCommand command) {
        ProcessDefinitionConfig definition = processDefinitionMapper
                .findByProcessKey(command.processKey())
                .filter(value -> value.getStatus()
                        == ProcessDefinitionConfig.ProcessStatus.PUBLISHED)
                .orElseThrow(() -> new OpenProcessStateConflictException(
                        "Process definition is not published"));

        Map<String, Object> variables = new HashMap<>(
                command.variables());
        RESERVED_VARIABLES.forEach(variables::remove);
        variables.put(
                "integrationApplicationId",
                command.actor().applicationId());
        variables.put("integrationTraceId", command.actor().traceId());
        variables.put(
                "integrationBusinessSystem",
                command.businessReference().system());
        variables.put(
                "integrationBusinessType",
                command.businessReference().type());
        variables.put(
                "integrationBusinessId",
                command.businessReference().id());
        if (StringUtils.hasText(command.externalInitiatorId())) {
            variables.put(
                    "integrationExternalInitiatorId",
                    command.externalInitiatorId());
        }
        multiInstanceListener.prepareVariables(
                definition.getId(),
                variables);

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                definition.getProcessKey(),
                command.businessKey(),
                variables);
        autoSkipService.autoSkipNodes(
                instance.getId(),
                definition.getId());
        processTaskService.syncTasksFromFlowable(instance.getId());
        return get(instance.getId(), command.actor());
    }

    @Override
    @Transactional(readOnly = true)
    public OpenProcessView get(
            String processInstanceId,
            OpenApplicationActor actor) {
        ProcessInstance active = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (active != null) {
            return new OpenProcessView(
                    active.getId(),
                    active.getProcessDefinitionKey(),
                    "RUNNING",
                    toInstant(active.getStartTime()),
                    null);
        }
        HistoricProcessInstance historic = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historic == null) {
            throw new OpenProcessNotFoundException();
        }
        String status = historic.getEndTime() == null
                ? "FAILED"
                : (StringUtils.hasText(historic.getDeleteReason())
                    ? "TERMINATED"
                    : "COMPLETED");
        return new OpenProcessView(
                historic.getId(),
                historic.getProcessDefinitionKey(),
                status,
                toInstant(historic.getStartTime()),
                toInstant(historic.getEndTime()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenTaskView> listActiveTasks(
            String processInstanceId,
            int offset,
            int limit,
            OpenApplicationActor actor) {
        get(processInstanceId, actor);
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .listPage(offset, limit);
        List<OpenTaskView> result = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            result.add(new OpenTaskView(
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    task.getName(),
                    task.isSuspended() ? "SUSPENDED" : "ACTIVE",
                    toInstant(task.getCreateTime())));
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenMessageCorrelationResult correlate(
            OpenMessageCorrelationCommand command) {
        get(command.processInstanceId(), command.actor());
        List<EventSubscription> subscriptions = runtimeService
                .createEventSubscriptionQuery()
                .processInstanceId(command.processInstanceId())
                .eventType(MESSAGE_EVENT_TYPE)
                .eventName(command.messageKey())
                .listPage(0, 2);
        if (subscriptions.size() != 1) {
            throw new OpenProcessStateConflictException(
                    "Process is not waiting for this message");
        }
        runtimeService.messageEventReceived(
                command.messageKey(),
                subscriptions.get(0).getExecutionId(),
                command.variables());
        return new OpenMessageCorrelationResult(
                command.processInstanceId(),
                command.messageKey(),
                Instant.now());
    }

    private OpenProcessDefinition toDefinition(
            ProcessDefinitionConfig definition) {
        ProcessVersionHistory version = processVersionMapper
                .findLatestByProcessKey(definition.getProcessKey());
        int versionNumber = version == null || version.getVersion() == null
                ? defaultVersion(definition.getVersion())
                : version.getVersion();
        Instant publishedAt = version == null
                ? toInstant(definition.getUpdatedAt())
                : toInstant(version.getPublishedAt());
        return new OpenProcessDefinition(
                definition.getProcessKey(),
                definition.getProcessName(),
                versionNumber,
                definition.getDescription(),
                publishedAt);
    }

    private int defaultVersion(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        return value == null
                ? null
                : value.toInstant(ZoneOffset.UTC);
    }
}
