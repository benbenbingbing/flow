package com.workflow.process.open.application;

import com.workflow.contracts.process.open.OpenApplicationActor;
import com.workflow.contracts.process.open.OpenMessageCorrelationCommand;
import com.workflow.contracts.process.open.OpenMessageCorrelationResult;
import com.workflow.contracts.process.open.OpenProcessCatalogPort;
import com.workflow.contracts.process.open.OpenProcessCancelCommand;
import com.workflow.contracts.process.open.OpenProcessDefinition;
import com.workflow.contracts.process.open.OpenProcessNotFoundException;
import com.workflow.contracts.process.open.OpenProcessRuntimePort;
import com.workflow.contracts.process.open.OpenProcessStartCommand;
import com.workflow.contracts.process.open.OpenProcessStateConflictException;
import com.workflow.contracts.process.open.OpenProcessView;
import com.workflow.contracts.process.open.OpenTaskView;
import com.workflow.contracts.process.open.OpenProcessIdentityNotResolvedException;
import com.workflow.contracts.identity.external.ExternalIdentityResolutionRequest;
import com.workflow.contracts.identity.external.ExternalIdentityResolver;
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
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.task.api.Task;
import org.flowable.identitylink.api.IdentityLink;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
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
            "integrationBusinessVersion",
            "integrationExternalInitiatorId",
            "integrationExternalInitiatorNamespace",
            "integrationOutcomeMapping",
            "integrationEventsDeferred");

    private final ProcessDefinitionConfigMapper processDefinitionMapper;
    private final ProcessVersionHistoryMapper processVersionMapper;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final org.flowable.engine.TaskService taskService;
    private final MultiInstanceCollectionListener multiInstanceListener;
    private final WorkflowAutoSkipService autoSkipService;
    private final ProcessTaskService processTaskService;
    private final RepositoryService repositoryService;
    private final List<ExternalIdentityResolver> externalIdentityResolvers;

    @Autowired
    public OpenProcessAdapter(
            ProcessDefinitionConfigMapper processDefinitionMapper,
            ProcessVersionHistoryMapper processVersionMapper,
            RuntimeService runtimeService,
            HistoryService historyService,
            org.flowable.engine.TaskService taskService,
            MultiInstanceCollectionListener multiInstanceListener,
            WorkflowAutoSkipService autoSkipService,
            ProcessTaskService processTaskService,
            RepositoryService repositoryService,
            List<ExternalIdentityResolver> externalIdentityResolvers) {
        this.processDefinitionMapper = processDefinitionMapper;
        this.processVersionMapper = processVersionMapper;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.taskService = taskService;
        this.multiInstanceListener = multiInstanceListener;
        this.autoSkipService = autoSkipService;
        this.processTaskService = processTaskService;
        this.repositoryService = repositoryService;
        this.externalIdentityResolvers = externalIdentityResolvers == null
                ? List.of()
                : List.copyOf(externalIdentityResolvers);
    }

    public OpenProcessAdapter(
            ProcessDefinitionConfigMapper processDefinitionMapper,
            ProcessVersionHistoryMapper processVersionMapper,
            RuntimeService runtimeService,
            HistoryService historyService,
            org.flowable.engine.TaskService taskService,
            MultiInstanceCollectionListener multiInstanceListener,
            WorkflowAutoSkipService autoSkipService,
            ProcessTaskService processTaskService) {
        this(processDefinitionMapper, processVersionMapper, runtimeService,
                historyService, taskService, multiInstanceListener,
                autoSkipService, processTaskService, null);
    }

    public OpenProcessAdapter(
            ProcessDefinitionConfigMapper processDefinitionMapper,
            ProcessVersionHistoryMapper processVersionMapper,
            RuntimeService runtimeService,
            HistoryService historyService,
            org.flowable.engine.TaskService taskService,
            MultiInstanceCollectionListener multiInstanceListener,
            WorkflowAutoSkipService autoSkipService,
            ProcessTaskService processTaskService,
            RepositoryService repositoryService) {
        this(processDefinitionMapper, processVersionMapper, runtimeService,
                historyService, taskService, multiInstanceListener,
                autoSkipService, processTaskService, repositoryService,
                List.of());
    }

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
        if (StringUtils.hasText(command.businessReference().version())) {
            variables.put(
                    "integrationBusinessVersion",
                    command.businessReference().version());
        }
        if (StringUtils.hasText(command.externalInitiatorId())) {
            variables.put(
                    "integrationExternalInitiatorId",
                    command.externalInitiatorId());
        }
        if (StringUtils.hasText(command.externalInitiatorNamespace())) {
            variables.put(
                    "integrationExternalInitiatorNamespace",
                    command.externalInitiatorNamespace());
        }
        if (StringUtils.hasText(command.outcomeMappingJson())) {
            variables.put(
                    "integrationOutcomeMapping",
                    command.outcomeMappingJson());
        }
        variables.put("integrationEventsDeferred", true);
        String resolvedInitiator = resolveExternalInitiator(command);
        if (resolvedInitiator != null) {
            variables.put("startUserId", resolvedInitiator);
            variables.put("initiator", resolvedInitiator);
        }
        multiInstanceListener.prepareVariables(
                definition.getId(),
                variables);

        ProcessInstance instance;
        if (command.processDefinitionVersion() == null) {
            instance = runtimeService.startProcessInstanceByKey(
                    definition.getProcessKey(),
                    command.businessKey(),
                    variables);
        } else {
            if (repositoryService == null) {
                throw new OpenProcessStateConflictException(
                        "Pinned process versions are unavailable");
            }
            ProcessDefinition deployed = repositoryService
                    .createProcessDefinitionQuery()
                    .processDefinitionKey(definition.getProcessKey())
                    .processDefinitionVersion(command.processDefinitionVersion())
                    .singleResult();
            if (deployed == null) {
                throw new OpenProcessStateConflictException(
                        "Pinned process definition version is not published");
            }
            instance = runtimeService.startProcessInstanceById(
                    deployed.getId(), command.businessKey(), variables);
        }
        autoSkipService.autoSkipNodes(
                instance.getId(),
                definition.getId());
        processTaskService.syncTasksFromFlowable(instance.getId());
        return get(instance.getId(), command.actor());
    }

    @Override
    @Transactional
    public void releaseIntegrationEvents(
            String processInstanceId,
            OpenApplicationActor actor) {
        if (!StringUtils.hasText(processInstanceId)) {
            return;
        }
        try {
            runtimeService.setVariable(
                    processInstanceId,
                    "integrationEventsDeferred",
                    false);
        } catch (RuntimeException exception) {
            // A process completed during start is covered by the explicit
            // terminal event emitted after its binding is stored. An active
            // process must fail instead of silently losing future events.
            if (runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult() != null) {
                throw exception;
            }
        }
    }

    private String resolveExternalInitiator(OpenProcessStartCommand command) {
        if (!StringUtils.hasText(command.externalInitiatorId())
                || !StringUtils.hasText(command.externalInitiatorNamespace())) {
            return null;
        }
        ExternalIdentityResolutionRequest request =
                new ExternalIdentityResolutionRequest(
                        command.externalInitiatorNamespace(),
                        command.externalInitiatorId(),
                        command.businessReference().system(),
                        command.processKey(),
                        command.businessKey(),
                        command.actor(),
                        command.variables());
        List<ExternalIdentityResolver> candidates =
                externalIdentityResolvers.stream()
                        .filter(resolver -> resolver.supports(
                                request.namespace()))
                        .toList();
        if (candidates.size() != 1) {
            throw new OpenProcessIdentityNotResolvedException(
                    "没有为身份命名空间注册唯一解析器: "
                            + request.namespace());
        }
        Optional<String> resolved = candidates.get(0).resolve(request);
        if (resolved.isEmpty() || !StringUtils.hasText(resolved.get())) {
            throw new OpenProcessIdentityNotResolvedException(
                    "外部发起人无法解析为 Flow 用户: "
                            + request.namespace());
        }
        return resolved.get().trim();
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
                    null,
                    immutableVariables(runtimeService.getVariables(active.getId())));
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
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list()
                .forEach(variable -> variables.put(
                        variable.getVariableName(), variable.getValue()));
        return new OpenProcessView(
                historic.getId(),
                historic.getProcessDefinitionKey(),
                status,
                toInstant(historic.getStartTime()),
                toInstant(historic.getEndTime()),
                immutableVariables(variables));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenProcessView cancel(OpenProcessCancelCommand command) {
        OpenProcessView current = get(
                command.processInstanceId(), command.actor());
        if (!"RUNNING".equals(current.status())) {
            throw new OpenProcessStateConflictException(
                    "Process is not running");
        }
        runtimeService.deleteProcessInstance(
                command.processInstanceId(), command.reason());
        return get(command.processInstanceId(), command.actor());
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
            List<IdentityLink> identityLinks = taskService
                    .getIdentityLinksForTask(task.getId());
            result.add(new OpenTaskView(
                    task.getId(),
                    task.getTaskDefinitionKey(),
                    task.getName(),
                    task.isSuspended() ? "SUSPENDED" : "ACTIVE",
                    toInstant(task.getCreateTime()),
                    task.getAssignee(),
                    identityLinks.stream()
                            .filter(link -> "candidate".equals(link.getType())
                                    && link.getUserId() != null)
                            .map(IdentityLink::getUserId)
                            .toList(),
                    identityLinks.stream()
                            .filter(link -> "candidate".equals(link.getType())
                                    && link.getGroupId() != null)
                            .map(IdentityLink::getGroupId)
                            .toList()));
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

    private Map<String, Object> immutableVariables(
            Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        variables.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
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
