package com.workflow.openapi.connector.config;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.http.HttpConnectorConfiguration;
import com.workflow.http.HttpConnectorConfigurationCodec;
import com.workflow.openapi.api.request.CreateIntegrationConnectorRequest;
import com.workflow.openapi.api.request.UpdateIntegrationConnectorRequest;
import com.workflow.openapi.api.response.IntegrationConnectorView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationConnectorAdministrationService {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationConnectorConfigMapper configMapper;
    private final HttpConnectorConfigurationCodec codec;
    private final ObjectMapper objectMapper;
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final Clock clock;

    @Autowired
    public IntegrationConnectorAdministrationService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationConnectorConfigMapper configMapper,
            HttpConnectorConfigurationCodec codec,
            ObjectMapper objectMapper,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort) {
        this(
                applicationMapper,
                configMapper,
                codec,
                objectMapper,
                actorProvider,
                auditPort,
                Clock.systemUTC());
    }

    IntegrationConnectorAdministrationService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationConnectorConfigMapper configMapper,
            HttpConnectorConfigurationCodec codec,
            ObjectMapper objectMapper,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.configMapper = configMapper;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IntegrationConnectorView> list(String applicationId) {
        if (applicationMapper.selectById(applicationId) == null) {
            throw new IllegalArgumentException("接入应用不存在");
        }
        return configMapper.findByApplication(applicationId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationConnectorView create(
            String applicationId,
            CreateIntegrationConnectorRequest request) {
        CurrentActor actor = requireActor();
        requireConfigurableApplication(applicationId);
        String id = IdWorker.getIdStr();
        String configName = request.configName().trim();
        requireUniqueName(applicationId, configName, null);
        String configuration = write(request.configuration());
        String hosts = write(request.allowedHosts());
        codec.read(id, applicationId, configuration, hosts);
        LocalDateTime now = now();
        IntegrationConnectorConfigRecord record =
                new IntegrationConnectorConfigRecord();
        record.setId(id);
        record.setApplicationId(applicationId);
        record.setConfigName(configName);
        record.setConnectorCode("http-json");
        record.setStatus("ACTIVE");
        record.setConfigurationDocument(configuration);
        record.setAllowedHostsDocument(hosts);
        record.setVersion(0L);
        record.setCreatedBy(actor.userId());
        record.setUpdatedBy(actor.userId());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        configMapper.insert(record);
        audit(AuditAction.CREATE, "创建 HTTP Connector 配置", record, actor);
        return toView(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationConnectorView update(
            String applicationId,
            String configId,
            UpdateIntegrationConnectorRequest request) {
        CurrentActor actor = requireActor();
        requireConfigurableApplication(applicationId);
        IntegrationConnectorConfigRecord record =
                configMapper.lockOwned(applicationId, configId);
        if (record == null) {
            throw new IllegalArgumentException(
                    "HTTP Connector 配置不存在");
        }
        if (!record.getVersion().equals(request.expectedVersion())) {
            throw conflict();
        }
        String configName = request.configName().trim();
        requireUniqueName(applicationId, configName, configId);
        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);
        String configuration = write(request.configuration());
        String hosts = write(request.allowedHosts());
        HttpConnectorConfiguration parsed = codec.read(
                configId,
                applicationId,
                configuration,
                hosts);
        if (parsed.operations().isEmpty()) {
            throw new IllegalArgumentException(
                    "HTTP Connector 必须包含操作");
        }
        LocalDateTime now = now();
        if (configMapper.updateConfiguration(
                applicationId,
                configId,
                request.expectedVersion(),
                configName,
                status,
                configuration,
                hosts,
                actor.userId(),
                now) != 1) {
            throw conflict();
        }
        record.setConfigName(configName);
        record.setStatus(status);
        record.setConfigurationDocument(configuration);
        record.setAllowedHostsDocument(hosts);
        record.setVersion(request.expectedVersion() + 1);
        record.setUpdatedBy(actor.userId());
        record.setUpdateTime(now);
        audit(AuditAction.CONFIGURE, "更新 HTTP Connector 配置", record, actor);
        return toView(record);
    }

    private IntegrationApplicationRecord requireConfigurableApplication(
            String applicationId) {
        IntegrationApplicationRecord application =
                applicationMapper.lockById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("接入应用不存在");
        }
        if (!"ACTIVE".equals(application.getStatus())
                || application.getExpiresAt() != null
                && !application.getExpiresAt().isAfter(now())) {
            throw new BusinessConflictException(
                    "INTEGRATION_APPLICATION_NOT_ACTIVE",
                    "接入应用当前不可配置 Connector");
        }
        return application;
    }

    private void requireUniqueName(
            String applicationId,
            String configName,
            String currentId) {
        String existingId = configMapper.findIdByName(
                applicationId,
                configName);
        if (existingId != null && !existingId.equals(currentId)) {
            throw new BusinessConflictException(
                    "INTEGRATION_CONNECTOR_NAME_CONFLICT",
                    "同名 HTTP Connector 配置已存在");
        }
    }

    private IntegrationConnectorView toView(
            IntegrationConnectorConfigRecord record) {
        try {
            return new IntegrationConnectorView(
                    record.getId(),
                    record.getApplicationId(),
                    record.getConfigName(),
                    record.getConnectorCode(),
                    record.getStatus(),
                    objectMapper.readTree(
                            record.getConfigurationDocument()),
                    List.copyOf(objectMapper.readValue(
                            record.getAllowedHostsDocument(),
                            STRING_LIST)),
                    record.getVersion(),
                    instant(record.getCreateTime()),
                    instant(record.getUpdateTime()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "HTTP Connector 配置损坏",
                    exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "HTTP Connector 配置无法序列化",
                    exception);
        }
    }

    private void audit(
            AuditAction action,
            String operation,
            IntegrationConnectorConfigRecord record,
            CurrentActor actor) {
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(action)
                .operationName(operation)
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(true)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType("INTEGRATION_CONNECTOR_CONFIG")
                .targetId(record.getId())
                .targetName(record.getConfigName())
                .summary(operation)
                .createdAt(now())
                .build());
    }

    private CurrentActor requireActor() {
        CurrentActor actor = actorProvider.current();
        if (actor == null
                || actor.userId() == null
                || actor.userId().isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        return actor;
    }

    private BusinessConflictException conflict() {
        return new BusinessConflictException(
                "INTEGRATION_CONNECTOR_VERSION_CONFLICT",
                "HTTP Connector 配置已被其他管理员修改");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
