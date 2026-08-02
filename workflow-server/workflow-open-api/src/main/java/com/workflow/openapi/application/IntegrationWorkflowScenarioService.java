package com.workflow.openapi.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.openapi.api.request.CreateIntegrationWorkflowScenarioRequest;
import com.workflow.openapi.api.request.UpdateIntegrationWorkflowScenarioRequest;
import com.workflow.openapi.api.response.IntegrationWorkflowScenarioView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessGrantMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationWorkflowScenarioMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationWorkflowScenarioRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理外部系统可复用的流程场景契约，场景配置与业务系统解耦。 */
@Service
public class IntegrationWorkflowScenarioService {

    private static final Pattern NAME = Pattern.compile(
            "^[A-Za-z][A-Za-z0-9._-]{0,99}$");
    private static final Pattern MAPPING_VALUE = Pattern.compile(
            "^[A-Za-z][A-Za-z0-9_.-]{0,127}$");
    private static final Set<String> EVENT_TYPES = Set.of(
            "com.flow.process.started.v1",
            "com.flow.task.created.v1",
            "com.flow.task.completed.v1",
            "com.flow.process.completed.v1",
            "com.flow.process.terminated.v1",
            "com.flow.process.failed.v1");
    private static final Set<String> OUTCOME_KEYS = Set.of(
            "status", "outcome", "actorId", "evidence");
    private static final Set<String> IDENTITY_KEYS = Set.of(
            "initiator", "subject", "tenant");
    private static final TypeReference<Set<String>> STRING_SET =
            new TypeReference<>() {
            };

    private final IntegrationWorkflowScenarioMapper mapper;
    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationProcessGrantMapper grantMapper;
    private final IntegrationVariableSchemaService schemaService;
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public IntegrationWorkflowScenarioService(
            IntegrationWorkflowScenarioMapper mapper,
            IntegrationApplicationMapper applicationMapper,
            IntegrationProcessGrantMapper grantMapper,
            IntegrationVariableSchemaService schemaService,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(mapper, applicationMapper, grantMapper, schemaService,
                actorProvider, auditPort, objectMapper, Clock.systemUTC());
    }

    IntegrationWorkflowScenarioService(
            IntegrationWorkflowScenarioMapper mapper,
            IntegrationApplicationMapper applicationMapper,
            IntegrationProcessGrantMapper grantMapper,
            IntegrationVariableSchemaService schemaService,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.mapper = mapper;
        this.applicationMapper = applicationMapper;
        this.grantMapper = grantMapper;
        this.schemaService = schemaService;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IntegrationWorkflowScenarioView> list(String applicationId) {
        requireActiveApplication(applicationId);
        return mapper.findByApplicationId(applicationId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationWorkflowScenarioView create(
            String applicationId,
            CreateIntegrationWorkflowScenarioRequest request) {
        CurrentActor actor = requireActor();
        requireActiveApplication(applicationId);
        Normalized normalized = normalize(applicationId, request);
        IntegrationWorkflowScenarioRecord record = normalized.record();
        record.setId(IdWorker.getIdStr());
        record.setApplicationId(applicationId);
        record.setRevision(1L);
        record.setStatus("ACTIVE");
        try {
            mapper.insert(record, actor.userId(), now());
        } catch (DuplicateKeyException exception) {
            throw new BusinessConflictException(
                    "INTEGRATION_SCENARIO_ALREADY_EXISTS",
                    "场景标识已存在");
        }
        recordAudit(AuditAction.CREATE, "创建外部流程场景", record, actor);
        return toView(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationWorkflowScenarioView update(
            String applicationId,
            String scenarioKey,
            UpdateIntegrationWorkflowScenarioRequest request) {
        CurrentActor actor = requireActor();
        requireActiveApplication(applicationId);
        if (request.expectedRevision() == null
                || request.expectedRevision() < 1) {
            throw new IllegalArgumentException("场景版本无效");
        }
        CreateIntegrationWorkflowScenarioRequest configuration =
                request.configuration();
        if (!scenarioKey.equals(configuration.scenarioKey())) {
            throw new IllegalArgumentException("更新时不能修改场景标识");
        }
        Normalized normalized = normalize(applicationId, configuration);
        IntegrationWorkflowScenarioRecord record = normalized.record();
        record.setApplicationId(applicationId);
        int updated;
        try {
            updated = mapper.update(record, actor.userId(),
                    request.expectedRevision(), now());
        } catch (DuplicateKeyException exception) {
            throw new BusinessConflictException(
                    "INTEGRATION_SCENARIO_ALREADY_EXISTS",
                    "场景标识已存在");
        }
        if (updated != 1) {
            throw new BusinessConflictException(
                    "INTEGRATION_SCENARIO_VERSION_CONFLICT",
                    "场景已被其他管理员修改或已停用");
        }
        IntegrationWorkflowScenarioRecord current =
                mapper.findByApplicationAndKey(applicationId, scenarioKey);
        recordAudit(AuditAction.CONFIGURE, "更新外部流程场景", current, actor);
        return toView(current);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(String applicationId, String scenarioKey,
                        long expectedRevision) {
        CurrentActor actor = requireActor();
        requireActiveApplication(applicationId);
        if (mapper.disable(applicationId, scenarioKey, expectedRevision,
                actor.userId(), now()) != 1) {
            throw new BusinessConflictException(
                    "INTEGRATION_SCENARIO_VERSION_CONFLICT",
                "场景已被其他管理员修改或已停用");
        }
        IntegrationWorkflowScenarioRecord current =
                mapper.findByApplicationAndKey(applicationId, scenarioKey);
        recordAudit(AuditAction.DISABLE, "停用外部流程场景", current, actor);
    }

    @Transactional(readOnly = true)
    public IntegrationWorkflowScenarioRecord requireActive(
            String applicationId, String scenarioKey) {
        IntegrationWorkflowScenarioRecord record =
                mapper.findByApplicationAndKey(applicationId, scenarioKey);
        if (record == null || !"ACTIVE".equals(record.getStatus())) {
            throw new IllegalArgumentException("外部流程场景不存在或已停用");
        }
        return record;
    }

    private Normalized normalize(String applicationId,
                                 CreateIntegrationWorkflowScenarioRequest request) {
        if (request == null || !NAME.matcher(trim(request.scenarioKey())).matches()
                || !NAME.matcher(trim(request.processKey())).matches()
                || trim(request.displayName()).isEmpty()
                || trim(request.displayName()).length() > 128
                || request.eventTypes() == null) {
            throw new IllegalArgumentException("场景标识或流程标识格式不正确");
        }
        if (grantMapper.findContract(applicationId, request.processKey().trim()) == null) {
            throw new IllegalArgumentException("流程未授权给该接入应用");
        }
        String schema = schemaService.validateConfiguration(request.inputSchema());
        JsonNode outcome = requireMapping(request.outcomeMapping(), OUTCOME_KEYS,
                "结果映射");
        JsonNode identity = requireMapping(request.identityMapping(), IDENTITY_KEYS,
                "身份映射");
        Set<String> events = request.eventTypes().stream()
                .map(this::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (events.isEmpty() || events.size() != request.eventTypes().size()
                || !EVENT_TYPES.containsAll(events)) {
            throw new IllegalArgumentException("事件白名单包含不支持的事件类型");
        }
        IntegrationWorkflowScenarioRecord record = new IntegrationWorkflowScenarioRecord();
        record.setScenarioKey(request.scenarioKey().trim());
        record.setDisplayName(request.displayName().trim());
        record.setProcessKey(request.processKey().trim());
        record.setProcessDefinitionVersion(request.processDefinitionVersion());
        record.setInputSchemaJson(schema);
        record.setOutcomeMappingJson(write(outcome));
        record.setIdentityMappingJson(write(identity));
        record.setEventTypesJson(write(events));
        Map<String, Object> configuration = new TreeMap<>();
        configuration.put("scenarioKey", record.getScenarioKey());
        configuration.put("displayName", record.getDisplayName());
        configuration.put("processKey", record.getProcessKey());
        configuration.put("processDefinitionVersion",
                record.getProcessDefinitionVersion());
        configuration.put("inputSchema", schema);
        configuration.put("outcomeMapping", record.getOutcomeMappingJson());
        configuration.put("identityMapping", record.getIdentityMappingJson());
        configuration.put("eventTypes", record.getEventTypesJson());
        record.setConfigHash(hash(configuration));
        return new Normalized(record);
    }

    private JsonNode requireMapping(JsonNode value, Set<String> keys,
                                    String label) {
        if (value == null || !value.isObject() || value.size() > keys.size()) {
            throw new IllegalArgumentException(label + "必须是对象");
        }
        value.fieldNames().forEachRemaining(key -> {
            if (!keys.contains(key)
                    || !value.get(key).isTextual()
                    || !MAPPING_VALUE.matcher(value.get(key).asText()).matches()) {
                throw new IllegalArgumentException(label + "包含不安全的字段映射");
            }
        });
        return value;
    }

    private IntegrationWorkflowScenarioView toView(
            IntegrationWorkflowScenarioRecord record) {
        try {
            return new IntegrationWorkflowScenarioView(
                    record.getId(), record.getScenarioKey(),
                    record.getDisplayName(), record.getProcessKey(),
                    record.getProcessDefinitionVersion(), record.getStatus(),
                    objectMapper.readTree(record.getInputSchemaJson()),
                    objectMapper.readTree(record.getOutcomeMappingJson()),
                    objectMapper.readTree(record.getIdentityMappingJson()),
                    Set.copyOf(objectMapper.readValue(record.getEventTypesJson(), STRING_SET)),
                    record.getRevision(), record.getConfigHash(),
                    toInstant(record.getCreateTime()), toInstant(record.getUpdateTime()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("场景配置损坏", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("场景配置无法保存", exception);
        }
    }

    private String hash(Map<String, Object> value) {
        try {
            String json = objectMapper.writeValueAsString(new TreeMap<>(value));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("场景配置无法计算摘要", exception);
        }
    }

    private CurrentActor requireActor() {
        CurrentActor actor = actorProvider.current();
        if (actor == null || actor.userId() == null || actor.userId().isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        return actor;
    }

    private void requireActiveApplication(String applicationId) {
        var application = applicationMapper.selectById(applicationId);
        if (application == null || !"ACTIVE".equals(application.getStatus())) {
            throw new IllegalArgumentException("接入应用不存在或已停用");
        }
    }

    private void recordAudit(
            AuditAction action,
            String operation,
            IntegrationWorkflowScenarioRecord scenario,
            CurrentActor actor) {
        if (auditPort == null || scenario == null) {
            return;
        }
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(action)
                .operationName(operation)
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(true)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType("INTEGRATION_WORKFLOW_SCENARIO")
                .targetId(scenario.getId())
                .targetName(scenario.getScenarioKey())
                .summary(operation)
                .createdAt(now())
                .build());
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record Normalized(IntegrationWorkflowScenarioRecord record) {
    }
}
