package com.workflow.openapi.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.open.OpenApplicationActor;
import com.workflow.contracts.process.open.OpenBusinessReference;
import com.workflow.contracts.process.open.OpenProcessCancelCommand;
import com.workflow.contracts.process.open.OpenMessageCorrelationCommand;
import com.workflow.contracts.process.open.OpenProcessCatalogPort;
import com.workflow.contracts.process.open.OpenProcessDefinition;
import com.workflow.contracts.process.open.OpenProcessEvent;
import com.workflow.contracts.process.open.OpenProcessEventPort;
import com.workflow.contracts.process.open.OpenProcessNotFoundException;
import com.workflow.contracts.process.open.OpenProcessRuntimePort;
import com.workflow.contracts.process.open.OpenProcessStartCommand;
import com.workflow.contracts.process.open.OpenProcessStateConflictException;
import com.workflow.contracts.process.open.OpenProcessView;
import com.workflow.openapi.api.error.OpenApiException;
import com.workflow.openapi.api.request.OpenCorrelateMessageRequest;
import com.workflow.openapi.api.request.OpenCancelProcessRequest;
import com.workflow.openapi.api.request.OpenStartProcessRequest;
import com.workflow.openapi.api.response.OpenBusinessReferenceView;
import com.workflow.openapi.api.response.OpenMessageCorrelationView;
import com.workflow.openapi.api.response.OpenPage;
import com.workflow.openapi.api.response.OpenPageMetadata;
import com.workflow.openapi.api.response.OpenProcessDefinitionView;
import com.workflow.openapi.api.response.OpenProcessInstanceView;
import com.workflow.openapi.api.response.OpenTaskSummaryView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessBindingMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessGrantMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationWorkflowScenarioMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessBindingRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessGrantRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationWorkflowScenarioRecord;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OpenProcessService {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final Pattern PUBLIC_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9_-]{7,127}");
    private static final Pattern MESSAGE_KEY = Pattern.compile(
            "[A-Za-z][A-Za-z0-9._-]{0,127}");

    private final IntegrationProcessGrantMapper grantMapper;
    private final IntegrationWorkflowScenarioMapper scenarioMapper;
    private final IntegrationProcessBindingMapper bindingMapper;
    private final OpenProcessCatalogPort catalogPort;
    private final OpenProcessRuntimePort runtimePort;
    private final IntegrationVariableSchemaService variableSchemaService;
    private final OpenIdempotencyService idempotencyService;
    private final OpenCursorCodec cursorCodec;
    private final OpenProcessEventPort eventPort;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public OpenProcessService(
            IntegrationProcessGrantMapper grantMapper,
            IntegrationWorkflowScenarioMapper scenarioMapper,
            IntegrationProcessBindingMapper bindingMapper,
            OpenProcessCatalogPort catalogPort,
            OpenProcessRuntimePort runtimePort,
            IntegrationVariableSchemaService variableSchemaService,
            OpenIdempotencyService idempotencyService,
            OpenCursorCodec cursorCodec,
            OpenProcessEventPort eventPort,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this(
                grantMapper,
                scenarioMapper,
                bindingMapper,
                catalogPort,
                runtimePort,
                variableSchemaService,
                idempotencyService,
                cursorCodec,
                eventPort,
                objectMapper,
                transactionManager,
                Clock.systemUTC());
    }

    OpenProcessService(
            IntegrationProcessGrantMapper grantMapper,
            IntegrationProcessBindingMapper bindingMapper,
            OpenProcessCatalogPort catalogPort,
            OpenProcessRuntimePort runtimePort,
            IntegrationVariableSchemaService variableSchemaService,
            OpenIdempotencyService idempotencyService,
            OpenCursorCodec cursorCodec,
            OpenProcessEventPort eventPort,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(grantMapper, null, bindingMapper, catalogPort, runtimePort,
                variableSchemaService, idempotencyService, cursorCodec,
                eventPort, objectMapper, transactionManager, clock);
    }

    OpenProcessService(
            IntegrationProcessGrantMapper grantMapper,
            IntegrationWorkflowScenarioMapper scenarioMapper,
            IntegrationProcessBindingMapper bindingMapper,
            OpenProcessCatalogPort catalogPort,
            OpenProcessRuntimePort runtimePort,
            IntegrationVariableSchemaService variableSchemaService,
            OpenIdempotencyService idempotencyService,
            OpenCursorCodec cursorCodec,
            OpenProcessEventPort eventPort,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.grantMapper = grantMapper;
        this.scenarioMapper = scenarioMapper;
        this.bindingMapper = bindingMapper;
        this.catalogPort = catalogPort;
        this.runtimePort = runtimePort;
        this.variableSchemaService = variableSchemaService;
        this.idempotencyService = idempotencyService;
        this.cursorCodec = cursorCodec;
        this.eventPort = eventPort;
        this.objectMapper = objectMapper;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public OpenPage<OpenProcessDefinitionView> listDefinitions(
            OpenApplicationActor actor,
            String cursor,
            Integer requestedLimit) {
        int limit = validateLimit(requestedLimit);
        int offset = cursorCodec.decode(cursor);
        List<IntegrationProcessGrantRecord> contracts =
                grantMapper.findContractsByApplicationId(
                        actor.applicationId());
        Map<String, IntegrationProcessGrantRecord> contractsByKey =
                contracts.stream().collect(Collectors.toMap(
                        IntegrationProcessGrantRecord::processKey,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<OpenProcessDefinition> definitions =
                new ArrayList<>(catalogPort.listPublished(
                        contractsByKey.keySet(),
                        actor));
        definitions.sort(Comparator.comparing(
                OpenProcessDefinition::processKey));
        if (offset > definitions.size()) {
            throw new OpenApiException(
                    400,
                    "INVALID_REQUEST",
                    "Cursor is no longer valid");
        }
        int end = Math.min(definitions.size(), offset + limit);
        List<OpenProcessDefinitionView> items = definitions
                .subList(offset, end)
                .stream()
                .map(definition -> toDefinitionView(
                        definition,
                        contractsByKey.get(definition.processKey())))
                .toList();
        boolean hasMore = end < definitions.size();
        return new OpenPage<>(
                items,
                new OpenPageMetadata(
                        hasMore ? cursorCodec.encode(end) : null,
                        hasMore));
    }

    public OperationResult<OpenProcessInstanceView> start(
            OpenApplicationActor actor,
            String idempotencyKey,
            OpenStartProcessRequest request) {
        if ((request.scenarioKey() == null || request.scenarioKey().isBlank())
                && (request.processKey() == null || request.processKey().isBlank())) {
            throw new OpenApiException(
                    400, "INVALID_REQUEST",
                    "processKey is required when scenarioKey is omitted");
        }
        IntegrationWorkflowScenarioRecord scenario = resolveScenario(
                actor.applicationId(), request.scenarioKey());
        String processKey = scenario == null
                ? request.processKey()
                : scenario.getProcessKey();
        IntegrationProcessGrantRecord contract = requireGrant(
                actor.applicationId(),
                processKey,
                true);
        validateVariables(scenario == null
                ? contract.inputSchemaJson()
                : scenario.getInputSchemaJson(), request.variables());
        String externalInitiatorId = resolveExternalInitiator(
                scenario, request);
        OpenIdempotencyService.Claim claim =
                idempotencyService.claim(
                        actor.applicationId(),
                        "PROCESS_START",
                        idempotencyKey,
                        request);
        if (claim.replay()) {
            return new OperationResult<>(
                    200,
                    true,
                    idempotencyService.readReplay(
                            claim,
                            OpenProcessInstanceView.class));
        }
        requireAcquired(claim);
        try {
            OpenProcessInstanceView created =
                    transactionTemplate.execute(status -> {
                        String bindingId = IdWorker.getIdStr();
                        OpenProcessView process = runtimePort.start(
                                new OpenProcessStartCommand(
                                        processKey,
                                        bindingId,
                                        toBusinessReference(
                                                request),
                                        externalInitiatorId,
                                        request.variables(),
                                        actor,
                                        scenario == null
                                                ? null
                                                : scenario.getProcessDefinitionVersion()));
                        if (scenario == null) {
                            bindingMapper.insert(
                                    bindingId,
                                    actor.applicationId(),
                                    request.businessReference().system(),
                                    request.businessReference().type(),
                                    request.businessReference().id(),
                                    process.processInstanceId(),
                                    process.processKey(),
                                    now());
                        } else {
                            String snapshot = writeSnapshot(request.variables());
                            bindingMapper.insertScenario(
                                    bindingId,
                                    actor.applicationId(),
                                    scenario.getId(),
                                    scenario.getScenarioKey(),
                                    scenario.getRevision(),
                                    scenario.getConfigHash(),
                                    request.businessReference().system(),
                                    request.businessReference().type(),
                                    request.businessReference().id(),
                                    process.processInstanceId(),
                                    process.processKey(),
                                    snapshot,
                                    sha256(snapshot),
                                    scenario.getOutcomeMappingJson(),
                                    scenario.getEventTypesJson(),
                                    externalInitiatorId,
                                    now());
                        }
                        publishInitialEvents(actor, process, externalInitiatorId);
                        OpenProcessInstanceView view = toInstanceView(
                                process,
                                request.businessReference().system(),
                                request.businessReference().type(),
                                request.businessReference().id(),
                                scenario == null ? null : scenario.getScenarioKey(),
                                scenario == null ? null : scenario.getRevision(),
                                projectResult(process,
                                        scenario == null
                                                ? null
                                                : scenario.getOutcomeMappingJson()));
                        idempotencyService
                                .completeInBusinessTransaction(
                                        claim,
                                        "PROCESS_INSTANCE",
                                        process.processInstanceId(),
                                        201,
                                        view);
                        return view;
                    });
            if (created == null) {
                throw new IllegalStateException(
                        "流程事务没有返回结果");
            }
            return new OperationResult<>(201, false, created);
        } catch (DataIntegrityViolationException exception) {
            idempotencyService.failRetryable(claim);
            throw new OpenApiException(
                    409,
                    "PROCESS_STATE_CONFLICT",
                    "Business reference is already bound");
        } catch (OpenProcessStateConflictException exception) {
            idempotencyService.failRetryable(claim);
            throw new OpenApiException(
                    409,
                    "PROCESS_STATE_CONFLICT",
                    exception.getMessage());
        } catch (OpenApiException exception) {
            idempotencyService.failRetryable(claim);
            throw exception;
        } catch (RuntimeException exception) {
            idempotencyService.failRetryable(claim);
            throw unavailable(exception);
        }
    }

    private void publishInitialEvents(
            OpenApplicationActor actor,
            OpenProcessView process,
            String externalInitiatorId) {
        Map<String, Object> identity = externalInitiatorId == null
                || externalInitiatorId.isBlank()
                ? Map.of()
                : Map.of("actorId", externalInitiatorId);
        eventPort.publish(new OpenProcessEvent(
                "OPEN_PROCESS_STARTED:"
                        + process.processInstanceId(),
                "com.flow.process.started.v1",
                process.processInstanceId(),
                null,
                null,
                null,
                actor.traceId(),
                process.createdAt(),
                identity));
        runtimePort.listActiveTasks(
                        process.processInstanceId(),
                        0,
                        1000,
                        actor)
                .forEach(task -> eventPort.publish(
                        new OpenProcessEvent(
                                "OPEN_TASK_CREATED:"
                                        + process.processInstanceId()
                                        + ":"
                                        + task.taskId(),
                                "com.flow.task.created.v1",
                                process.processInstanceId(),
                                task.taskId(),
                                task.taskDefinitionKey(),
                                task.name(),
                                actor.traceId(),
                                task.createdAt())));
    }

    public OpenProcessInstanceView get(
            OpenApplicationActor actor,
            String processInstanceId) {
        validatePublicId(processInstanceId);
        IntegrationProcessBindingRecord binding =
                requireBinding(actor, processInstanceId);
        OpenProcessView process;
        try {
            process = runtimePort.get(processInstanceId, actor);
        } catch (OpenProcessNotFoundException exception) {
            throw notFound();
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        return toInstanceView(process, binding);
    }

    public OperationResult<OpenProcessInstanceView> cancel(
            OpenApplicationActor actor,
            String processInstanceId,
            String idempotencyKey,
            OpenCancelProcessRequest request) {
        validatePublicId(processInstanceId);
        IntegrationProcessBindingRecord binding = requireBinding(
                actor, processInstanceId);
        OpenIdempotencyService.Claim claim = idempotencyService.claim(
                actor.applicationId(),
                "PROCESS_CANCEL",
                idempotencyKey,
                Map.of("processInstanceId", processInstanceId,
                        "reason", request.reason()));
        if (claim.replay()) {
            return new OperationResult<>(200, true,
                    idempotencyService.readReplay(
                            claim, OpenProcessInstanceView.class));
        }
        requireAcquired(claim);
        try {
            OpenProcessInstanceView cancelled = transactionTemplate.execute(
                    status -> {
                        OpenProcessView process = runtimePort.cancel(
                                new OpenProcessCancelCommand(
                                        processInstanceId,
                                        request.reason().trim(),
                                        actor));
                        OpenProcessInstanceView view = toInstanceView(
                                process, binding);
                        idempotencyService.completeInBusinessTransaction(
                                claim, "PROCESS_INSTANCE", processInstanceId,
                                200, view);
                        return view;
                    });
            if (cancelled == null) {
                throw new IllegalStateException("取消流程事务没有返回结果");
            }
            return new OperationResult<>(200, false, cancelled);
        } catch (OpenProcessStateConflictException exception) {
            idempotencyService.failRetryable(claim);
            throw new OpenApiException(409, "PROCESS_STATE_CONFLICT",
                    exception.getMessage());
        } catch (OpenProcessNotFoundException exception) {
            idempotencyService.failRetryable(claim);
            throw notFound();
        } catch (OpenApiException exception) {
            idempotencyService.failRetryable(claim);
            throw exception;
        } catch (RuntimeException exception) {
            idempotencyService.failRetryable(claim);
            throw unavailable(exception);
        }
    }

    public OpenPage<OpenTaskSummaryView> listTasks(
            OpenApplicationActor actor,
            String processInstanceId,
            String cursor,
            Integer requestedLimit) {
        validatePublicId(processInstanceId);
        requireBinding(actor, processInstanceId);
        int limit = validateLimit(requestedLimit);
        int offset = cursorCodec.decode(cursor);
        try {
            var tasks = runtimePort.listActiveTasks(
                    processInstanceId,
                    offset,
                    limit + 1,
                    actor);
            boolean hasMore = tasks.size() > limit;
            List<OpenTaskSummaryView> items = tasks.stream()
                    .limit(limit)
                    .map(task -> new OpenTaskSummaryView(
                            task.taskId(),
                            task.taskDefinitionKey(),
                            task.name(),
                            task.status(),
                            task.createdAt(),
                            task.assignee(),
                            task.candidateUserIds(),
                            task.candidateGroupIds()))
                    .toList();
            return new OpenPage<>(
                    items,
                    new OpenPageMetadata(
                            hasMore
                                    ? cursorCodec.encode(
                                    offset + limit)
                                    : null,
                            hasMore));
        } catch (OpenProcessNotFoundException exception) {
            throw notFound();
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    public OperationResult<OpenMessageCorrelationView> correlate(
            OpenApplicationActor actor,
            String processInstanceId,
            String messageKey,
            String idempotencyKey,
            OpenCorrelateMessageRequest request) {
        validatePublicId(processInstanceId);
        if (messageKey == null
                || !MESSAGE_KEY.matcher(messageKey).matches()) {
            throw new OpenApiException(
                    400,
                    "INVALID_REQUEST",
                    "Message key is invalid");
        }
        IntegrationProcessBindingRecord binding =
                requireBinding(actor, processInstanceId);
        IntegrationProcessGrantRecord contract = requireGrant(
                actor.applicationId(),
                binding.getProcessDefinitionKey(),
                false);
        if (!readMessageKeys(contract).contains(messageKey)) {
            throw new OpenApiException(
                    403,
                    "PROCESS_NOT_GRANTED",
                    "Message is not granted for this process");
        }
        validateVariables(contract, request.variables());
        CorrelationFingerprint fingerprint =
                new CorrelationFingerprint(
                        processInstanceId,
                        messageKey,
                        request.variables());
        OpenIdempotencyService.Claim claim =
                idempotencyService.claim(
                        actor.applicationId(),
                        "MESSAGE_CORRELATE",
                        idempotencyKey,
                        fingerprint);
        if (claim.replay()) {
            return new OperationResult<>(
                    200,
                    true,
                    idempotencyService.readReplay(
                            claim,
                            OpenMessageCorrelationView.class));
        }
        requireAcquired(claim);
        try {
            OpenMessageCorrelationView accepted =
                    transactionTemplate.execute(status -> {
                        var result = runtimePort.correlate(
                                new OpenMessageCorrelationCommand(
                                        processInstanceId,
                                        messageKey,
                                        request.variables(),
                                        actor));
                        OpenMessageCorrelationView view =
                                new OpenMessageCorrelationView(
                                        result.processInstanceId(),
                                        result.messageKey(),
                                        "ACCEPTED",
                                        result.acceptedAt());
                        idempotencyService
                                .completeInBusinessTransaction(
                                        claim,
                                        "MESSAGE_CORRELATION",
                                        processInstanceId,
                                        202,
                                        view);
                        return view;
                    });
            if (accepted == null) {
                throw new IllegalStateException(
                        "消息关联事务没有返回结果");
            }
            return new OperationResult<>(202, false, accepted);
        } catch (OpenProcessNotFoundException exception) {
            idempotencyService.failRetryable(claim);
            throw notFound();
        } catch (OpenProcessStateConflictException exception) {
            idempotencyService.failRetryable(claim);
            throw new OpenApiException(
                    409,
                    "PROCESS_STATE_CONFLICT",
                    exception.getMessage());
        } catch (OpenApiException exception) {
            idempotencyService.failRetryable(claim);
            throw exception;
        } catch (RuntimeException exception) {
            idempotencyService.failRetryable(claim);
            throw unavailable(exception);
        }
    }

    private IntegrationProcessBindingRecord requireBinding(
            OpenApplicationActor actor,
            String processInstanceId) {
        IntegrationProcessBindingRecord binding =
                bindingMapper.findByProcessInstance(
                        actor.applicationId(),
                        processInstanceId);
        if (binding == null
                || grantMapper.findContract(
                        actor.applicationId(),
                        binding.getProcessDefinitionKey()) == null) {
            throw notFound();
        }
        return binding;
    }

    private IntegrationProcessGrantRecord requireGrant(
            String applicationId,
            String processKey,
            boolean revealGrantFailure) {
        IntegrationProcessGrantRecord contract =
                grantMapper.findContract(applicationId, processKey);
        if (contract == null) {
            if (revealGrantFailure) {
                throw new OpenApiException(
                        403,
                        "PROCESS_NOT_GRANTED",
                        "Process is not granted to this application");
            }
            throw notFound();
        }
        return contract;
    }

    private void validateVariables(
            String schemaJson,
            Map<String, Object> variables) {
        List<IntegrationVariableSchemaService.Violation> violations =
                variableSchemaService.validateVariables(
                        schemaJson,
                        variables);
        if (!violations.isEmpty()) {
            throw new OpenApiException(
                    422,
                    "VARIABLE_VALIDATION_FAILED",
                    "Process variables are invalid",
                    Map.of("violations", violations),
                    null);
        }
    }

    private void validateVariables(
            IntegrationProcessGrantRecord contract,
            Map<String, Object> variables) {
        validateVariables(contract.inputSchemaJson(), variables);
    }

    private IntegrationWorkflowScenarioRecord resolveScenario(
            String applicationId,
            String scenarioKey) {
        if (scenarioKey == null || scenarioKey.isBlank()) {
            return null;
        }
        if (scenarioMapper == null) {
            throw new OpenApiException(
                    503,
                    "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                    "Scenario configuration is unavailable");
        }
        IntegrationWorkflowScenarioRecord scenario =
                scenarioMapper.findByApplicationAndKey(
                        applicationId,
                        scenarioKey);
        if (scenario == null || !"ACTIVE".equals(scenario.getStatus())) {
            throw new OpenApiException(
                    404,
                    "RESOURCE_NOT_FOUND",
                    "Workflow scenario was not found");
        }
        return scenario;
    }

    private String resolveExternalInitiator(
            IntegrationWorkflowScenarioRecord scenario,
            OpenStartProcessRequest request) {
        if (request.initiator() != null
                && request.initiator().externalUserId() != null
                && !request.initiator().externalUserId().isBlank()) {
            return request.initiator().externalUserId();
        }
        if (scenario == null || scenario.getIdentityMappingJson() == null) {
            return null;
        }
        try {
            JsonNode mapping = objectMapper.readTree(
                    scenario.getIdentityMappingJson());
            String source = mapping.path("initiator").asText(null);
            if (source == null || !source.startsWith("variables.")) {
                return null;
            }
            Object value = request.variables().get(
                    source.substring("variables.".length()));
            return value instanceof String string && !string.isBlank()
                    ? string
                    : null;
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private Set<String> readMessageKeys(
            IntegrationProcessGrantRecord contract) {
        try {
            return new LinkedHashSet<>(objectMapper.readValue(
                    contract.allowedMessageKeys(),
                    STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private OpenProcessDefinitionView toDefinitionView(
            OpenProcessDefinition definition,
            IntegrationProcessGrantRecord contract) {
        if (contract == null) {
            throw new IllegalStateException(
                    "流程目录返回了未授权流程");
        }
        try {
            JsonNode schema = objectMapper.readTree(
                    contract.inputSchemaJson());
            return new OpenProcessDefinitionView(
                    definition.processKey(),
                    definition.name(),
                    definition.version(),
                    definition.description(),
                    schema,
                    definition.publishedAt());
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private OpenBusinessReference toBusinessReference(
            OpenStartProcessRequest request) {
        return new OpenBusinessReference(
                request.businessReference().system(),
                request.businessReference().type(),
                request.businessReference().id());
    }

    private OpenProcessInstanceView toInstanceView(
            OpenProcessView process,
            IntegrationProcessBindingRecord binding) {
        IntegrationWorkflowScenarioRecord scenario = binding.getScenarioId() == null
                || scenarioMapper == null
                ? null
                : scenarioMapper.findById(binding.getScenarioId());
        return toInstanceView(
                process,
                binding.getExternalSystem(),
                binding.getBusinessType(),
                binding.getBusinessId(),
                binding.getScenarioKey(),
                binding.getScenarioRevision(),
                projectResult(process,
                        binding.getOutcomeMappingSnapshotJson() == null
                                && scenario != null
                                ? scenario.getOutcomeMappingJson()
                                : binding.getOutcomeMappingSnapshotJson()));
    }

    private OpenProcessInstanceView toInstanceView(
            OpenProcessView process,
            String externalSystem,
            String businessType,
            String businessId) {
        return toInstanceView(
                process,
                externalSystem,
                businessType,
                businessId,
                null,
                null,
                Map.of());
    }

    private OpenProcessInstanceView toInstanceView(
            OpenProcessView process,
            String externalSystem,
            String businessType,
            String businessId,
            String scenarioKey,
            Long scenarioRevision,
            Map<String, Object> result) {
        return new OpenProcessInstanceView(
                process.processInstanceId(),
                process.processKey(),
                process.status(),
                new OpenBusinessReferenceView(
                        externalSystem,
                        businessType,
                        businessId),
                process.createdAt(),
                process.completedAt(),
                scenarioKey,
                scenarioRevision,
                result);
    }

    private Map<String, Object> projectResult(
            OpenProcessView process,
            String outcomeMappingJson) {
        if (outcomeMappingJson == null || process.variables() == null
                || process.variables().isEmpty()) {
            return Map.of();
        }
        try {
            JsonNode mapping = objectMapper.readTree(
                    outcomeMappingJson);
            Map<String, Object> result = new LinkedHashMap<>();
            mapping.fields().forEachRemaining(entry -> {
                String source = entry.getValue().asText();
                if (source.startsWith("variables.")) {
                    source = source.substring("variables.".length());
                }
                if (process.variables().containsKey(source)) {
                    Object value = process.variables().get(source);
                    if (value != null) {
                        result.put(entry.getKey(), value);
                    }
                }
            });
            return Map.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private String writeSnapshot(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(
                    variables == null
                            ? Map.of()
                            : new LinkedHashMap<>(variables));
        } catch (JsonProcessingException exception) {
            throw new OpenApiException(
                    422,
                    "INVALID_REQUEST",
                    "Input snapshot cannot be serialized");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to hash input snapshot", exception);
        }
    }

    private void requireAcquired(
            OpenIdempotencyService.Claim claim) {
        if (!claim.acquired()) {
            throw new OpenApiException(
                    409,
                    "REQUEST_IN_PROGRESS",
                    "Request with this idempotency key is in progress",
                    null,
                    3L);
        }
    }

    private int validateLimit(Integer value) {
        int limit = value == null ? DEFAULT_LIMIT : value;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new OpenApiException(
                    400,
                    "INVALID_REQUEST",
                    "Limit must be between 1 and 200");
        }
        return limit;
    }

    private void validatePublicId(String value) {
        if (value == null
                || !PUBLIC_ID.matcher(value).matches()) {
            throw new OpenApiException(
                    400,
                    "INVALID_REQUEST",
                    "Process instance ID is invalid");
        }
    }

    private OpenApiException notFound() {
        return new OpenApiException(
                404,
                "RESOURCE_NOT_FOUND",
                "Resource was not found");
    }

    private OpenApiException unavailable(Throwable cause) {
        return new OpenApiException(
                503,
                "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                "Integration capability is temporarily unavailable");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
    }

    public record OperationResult<T>(
            int status,
            boolean replay,
            T data) {
    }

    private record CorrelationFingerprint(
            String processInstanceId,
            String messageKey,
            Map<String, Object> variables) {
    }
}
