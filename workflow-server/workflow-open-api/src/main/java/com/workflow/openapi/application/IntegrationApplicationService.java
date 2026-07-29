package com.workflow.openapi.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import com.workflow.openapi.api.request.CreateIntegrationApplicationRequest;
import com.workflow.openapi.api.request.RevokeIntegrationCredentialRequest;
import com.workflow.openapi.api.request.RotateIntegrationCredentialRequest;
import com.workflow.openapi.api.request.UpdateIntegrationAccessRequest;
import com.workflow.openapi.api.request.UpdateIntegrationStatusRequest;
import com.workflow.openapi.api.response.IntegrationApplicationView;
import com.workflow.openapi.api.response.IssuedIntegrationCredentialView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessGrantMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationScopeMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationGrantValueRecord;
import com.workflow.openapi.network.IpNetwork;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationApplicationService {

    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    private static final int DEFAULT_MAX_CONCURRENCY = 10;
    private static final Pattern PROCESS_KEY = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9_.:-]{0,99}");
    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationCredentialMapper credentialMapper;
    private final IntegrationScopeMapper scopeMapper;
    private final IntegrationProcessGrantMapper processGrantMapper;
    private final IntegrationSecretGenerator secretGenerator;
    private final IntegrationSecretHasher secretHasher;
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public IntegrationApplicationService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationCredentialMapper credentialMapper,
            IntegrationScopeMapper scopeMapper,
            IntegrationProcessGrantMapper processGrantMapper,
            IntegrationSecretGenerator secretGenerator,
            IntegrationSecretHasher secretHasher,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(
                applicationMapper,
                credentialMapper,
                scopeMapper,
                processGrantMapper,
                secretGenerator,
                secretHasher,
                actorProvider,
                auditPort,
                objectMapper,
                Clock.systemUTC());
    }

    IntegrationApplicationService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationCredentialMapper credentialMapper,
            IntegrationScopeMapper scopeMapper,
            IntegrationProcessGrantMapper processGrantMapper,
            IntegrationSecretGenerator secretGenerator,
            IntegrationSecretHasher secretHasher,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.credentialMapper = credentialMapper;
        this.scopeMapper = scopeMapper;
        this.processGrantMapper = processGrantMapper;
        this.secretGenerator = secretGenerator;
        this.secretHasher = secretHasher;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IntegrationApplicationView> list() {
        List<IntegrationApplicationRecord> applications =
                applicationMapper.findRecent();
        if (applications.isEmpty()) {
            return List.of();
        }
        List<String> applicationIds = applications.stream()
                .map(IntegrationApplicationRecord::getId)
                .toList();
        Map<String, IntegrationApplicationCredentialRecord> credentials =
                credentialMapper.findActiveByApplicationIds(
                                applicationIds)
                        .stream()
                        .collect(Collectors.toMap(
                                IntegrationApplicationCredentialRecord
                                        ::getApplicationId,
                                Function.identity()));
        Map<String, Set<String>> scopes = groupGrants(
                scopeMapper.findByApplicationIds(applicationIds));
        Map<String, Set<String>> processGrants = groupGrants(
                processGrantMapper.findByApplicationIds(applicationIds));
        return applications.stream()
                .map(application -> toView(
                        application,
                        credentials.get(application.getId()),
                        scopes.getOrDefault(
                                application.getId(),
                                Set.of()),
                        processGrants.getOrDefault(
                                application.getId(),
                                Set.of())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedIntegrationCredentialView create(
            CreateIntegrationApplicationRequest request) {
        CurrentActor actor = requireActor();
        Set<String> scopes = IntegrationScope.validate(request.scopes());
        Set<String> processKeys = validateProcessKeys(request.processKeys());
        List<String> cidrs = validateCidrs(request.allowedSourceCidrs());
        LocalDateTime now = now();

        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId(IdWorker.getIdStr());
        application.setClientId(secretGenerator.newClientId());
        application.setApplicationName(request.applicationName().trim());
        application.setDescription(trimToNull(request.description()));
        application.setOwnerOrganizationId(
                trimToNull(request.ownerOrganizationId()));
        application.setStatus(ApplicationStatus.ACTIVE.name());
        application.setRateLimitPerMinute(request.rateLimitPerMinute() == null
                ? DEFAULT_RATE_LIMIT_PER_MINUTE
                : request.rateLimitPerMinute());
        application.setMaxConcurrency(request.maxConcurrency() == null
                ? DEFAULT_MAX_CONCURRENCY
                : request.maxConcurrency());
        application.setAllowedSourceCidrs(writeCidrs(cidrs));
        application.setExpiresAt(toLocalDateTime(request.expiresAt()));
        application.setVersion(0L);
        application.setCreatedBy(actor.userId());
        application.setUpdatedBy(actor.userId());
        application.setCreateTime(now);
        application.setUpdateTime(now);
        applicationMapper.insert(application);

        storeGrants(application.getId(), scopes, processKeys, actor.userId(), now);
        IssuedSecret issued = createCredential(
                application.getId(),
                1L,
                request.expiresAt(),
                actor.userId(),
                now);
        recordAudit(
                AuditAction.CREATE,
                "创建接入应用",
                application,
                actor,
                true);

        return new IssuedIntegrationCredentialView(
                toView(application),
                issued.secret(),
                issued.expiresAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationApplicationView updateAccess(
            String applicationId,
            UpdateIntegrationAccessRequest request) {
        CurrentActor actor = requireActor();
        Set<String> scopes = IntegrationScope.validate(request.scopes());
        Set<String> processKeys = validateProcessKeys(request.processKeys());
        LocalDateTime now = now();
        IntegrationApplicationRecord application =
                requireLockedApplication(applicationId);
        requireNotRevoked(application);
        requireExpectedVersion(application, request.expectedVersion());
        int updated = applicationMapper.advanceVersion(
                applicationId,
                request.expectedVersion(),
                actor.userId(),
                now);
        if (updated != 1) {
            throw versionConflict();
        }
        scopeMapper.deleteByApplicationId(applicationId);
        processGrantMapper.deleteByApplicationId(applicationId);
        storeGrants(applicationId, scopes, processKeys, actor.userId(), now);
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(actor.userId());
        application.setUpdateTime(now);
        recordAudit(
                AuditAction.ASSIGN_PERMISSION,
                "更新接入应用授权",
                application,
                actor,
                true);
        return toView(application);
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationApplicationView updateStatus(
            String applicationId,
            UpdateIntegrationStatusRequest request) {
        CurrentActor actor = requireActor();
        ApplicationStatus target = ApplicationStatus.parse(request.status());
        LocalDateTime now = now();
        IntegrationApplicationRecord application =
                requireLockedApplication(applicationId);
        requireExpectedVersion(application, request.expectedVersion());
        ApplicationStatus current = ApplicationStatus.valueOf(
                application.getStatus());
        if (current == ApplicationStatus.REVOKED
                && target != ApplicationStatus.REVOKED) {
            throw new BusinessConflictException(
                    "INTEGRATION_APPLICATION_REVOKED",
                    "已吊销的接入应用不能重新启用");
        }
        if (target == ApplicationStatus.REVOKED) {
            credentialMapper.revokeActive(applicationId, actor.userId(), now);
        }
        int updated = applicationMapper.updateStatus(
                applicationId,
                target.name(),
                request.expectedVersion(),
                actor.userId(),
                now);
        if (updated != 1) {
            throw versionConflict();
        }
        application.setStatus(target.name());
        application.setVersion(application.getVersion() + 1);
        application.setUpdatedBy(actor.userId());
        application.setUpdateTime(now);
        recordAudit(
                target == ApplicationStatus.ACTIVE
                        ? AuditAction.ENABLE
                        : AuditAction.DISABLE,
                target == ApplicationStatus.REVOKED
                        ? "吊销接入应用"
                        : "变更接入应用状态",
                application,
                actor,
                true);
        return toView(application);
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedIntegrationCredentialView rotateCredential(
            String applicationId,
            RotateIntegrationCredentialRequest request) {
        CurrentActor actor = requireActor();
        LocalDateTime now = now();
        IntegrationApplicationRecord application =
                requireLockedApplication(applicationId);
        requireNotRevoked(application);
        requireExpectedVersion(application, request.expectedVersion());
        credentialMapper.revokeActive(applicationId, actor.userId(), now);
        long version = credentialMapper.findLatestVersion(applicationId) + 1;
        IssuedSecret issued = createCredential(
                applicationId,
                version,
                request.expiresAt(),
                actor.userId(),
                now);
        advanceApplicationVersion(
                application,
                request.expectedVersion(),
                actor.userId(),
                now);
        recordAudit(
                AuditAction.CONFIGURE,
                "轮换接入应用凭据",
                application,
                actor,
                true);
        return new IssuedIntegrationCredentialView(
                toView(application),
                issued.secret(),
                issued.expiresAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationApplicationView revokeCredential(
            String applicationId,
            RevokeIntegrationCredentialRequest request) {
        CurrentActor actor = requireActor();
        LocalDateTime now = now();
        IntegrationApplicationRecord application =
                requireLockedApplication(applicationId);
        requireNotRevoked(application);
        requireExpectedVersion(application, request.expectedVersion());
        IntegrationApplicationCredentialRecord credential =
                credentialMapper.findActive(applicationId);
        if (credential == null
                || credentialMapper.revokeActive(
                        applicationId,
                        actor.userId(),
                        now) != 1) {
            throw new BusinessConflictException(
                    "INTEGRATION_CREDENTIAL_NOT_ACTIVE",
                    "接入应用没有可吊销的活动凭据");
        }
        advanceApplicationVersion(
                application,
                request.expectedVersion(),
                actor.userId(),
                now);
        recordCredentialAudit(
                "吊销接入应用凭据",
                application,
                credential,
                actor);
        return toView(application);
    }

    private IssuedSecret createCredential(
            String applicationId,
            long version,
            Instant expiresAt,
            String operatorId,
            LocalDateTime now) {
        String secret = secretGenerator.newClientSecret();
        IntegrationApplicationCredentialRecord credential =
                new IntegrationApplicationCredentialRecord();
        credential.setId(IdWorker.getIdStr());
        credential.setApplicationId(applicationId);
        credential.setSecretHash(secretHasher.hash(secret));
        credential.setCredentialHint(
                secret.substring(Math.max(0, secret.length() - 8)));
        credential.setStatus("ACTIVE");
        credential.setCredentialVersion(version);
        credential.setExpiresAt(toLocalDateTime(expiresAt));
        credential.setCreatedBy(operatorId);
        credential.setCreateTime(now);
        credentialMapper.insert(credential);
        return new IssuedSecret(secret, expiresAt);
    }

    private void storeGrants(
            String applicationId,
            Set<String> scopes,
            Set<String> processKeys,
            String operatorId,
            LocalDateTime now) {
        scopes.forEach(scope -> scopeMapper.insertGrant(
                applicationId, scope, operatorId, now));
        processKeys.forEach(processKey -> processGrantMapper.insertGrant(
                applicationId, processKey, operatorId, now));
    }

    private IntegrationApplicationView toView(
            IntegrationApplicationRecord application) {
        return toView(
                application,
                credentialMapper.findActive(application.getId()),
                immutableSet(scopeMapper.findByApplicationId(
                        application.getId())),
                immutableSet(processGrantMapper.findByApplicationId(
                        application.getId())));
    }

    private IntegrationApplicationView toView(
            IntegrationApplicationRecord application,
            IntegrationApplicationCredentialRecord credential,
            Set<String> scopes,
            Set<String> processGrants) {
        return new IntegrationApplicationView(
                application.getId(),
                application.getClientId(),
                application.getApplicationName(),
                application.getDescription(),
                application.getOwnerOrganizationId(),
                application.getStatus(),
                scopes,
                processGrants,
                application.getRateLimitPerMinute(),
                application.getMaxConcurrency(),
                readCidrs(application.getAllowedSourceCidrs()),
                toInstant(application.getExpiresAt()),
                application.getVersion(),
                credential == null ? null : credential.getCredentialHint(),
                credential == null ? null : toInstant(
                        credential.getExpiresAt()),
                credential == null ? null : toInstant(
                        credential.getLastUsedAt()),
                toInstant(application.getCreateTime()),
                toInstant(application.getUpdateTime()));
    }

    private Map<String, Set<String>> groupGrants(
            List<IntegrationGrantValueRecord> grants) {
        return grants.stream().collect(Collectors.groupingBy(
                IntegrationGrantValueRecord::getApplicationId,
                Collectors.mapping(
                        IntegrationGrantValueRecord::getGrantValue,
                        Collectors.toCollection(LinkedHashSet::new))));
    }

    private Set<String> immutableSet(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private Set<String> validateProcessKeys(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = value == null ? "" : value.trim();
            if (!PROCESS_KEY.matcher(normalized).matches()) {
                throw new IllegalArgumentException("流程标识格式不正确");
            }
            result.add(normalized);
        }
        return Set.copyOf(result);
    }

    private List<String> validateCidrs(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = value == null ? "" : value.trim();
            try {
                IpNetwork.parse(normalized);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "来源 CIDR 格式不正确",
                        exception);
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private String writeCidrs(List<String> cidrs) {
        try {
            return objectMapper.writeValueAsString(cidrs);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化来源 CIDR", exception);
        }
    }

    private List<String> readCidrs(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(value, STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("来源 CIDR 配置损坏", exception);
        }
    }

    private IntegrationApplicationRecord requireLockedApplication(String id) {
        IntegrationApplicationRecord application =
                applicationMapper.lockById(id);
        if (application == null) {
            throw new IllegalArgumentException("接入应用不存在");
        }
        return application;
    }

    private void requireNotRevoked(
            IntegrationApplicationRecord application) {
        if (ApplicationStatus.REVOKED.name().equals(application.getStatus())) {
            throw new BusinessConflictException(
                    "INTEGRATION_APPLICATION_REVOKED",
                    "接入应用已吊销");
        }
    }

    private void requireExpectedVersion(
            IntegrationApplicationRecord application,
            Long expectedVersion) {
        if (expectedVersion == null
                || application.getVersion() == null
                || application.getVersion().longValue()
                != expectedVersion.longValue()) {
            throw versionConflict();
        }
    }

    private void advanceApplicationVersion(
            IntegrationApplicationRecord application,
            long expectedVersion,
            String operatorId,
            LocalDateTime now) {
        if (applicationMapper.advanceVersion(
                application.getId(),
                expectedVersion,
                operatorId,
                now) != 1) {
            throw versionConflict();
        }
        application.setVersion(expectedVersion + 1);
        application.setUpdatedBy(operatorId);
        application.setUpdateTime(now);
    }

    private BusinessConflictException versionConflict() {
        return new BusinessConflictException(
                "INTEGRATION_APPLICATION_VERSION_CONFLICT",
                "接入应用已被其他管理员修改");
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

    private void recordAudit(
            AuditAction action,
            String operation,
            IntegrationApplicationRecord application,
            CurrentActor actor,
            boolean required) {
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(action)
                .operationName(operation)
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(required)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType("INTEGRATION_APPLICATION")
                .targetId(application.getId())
                .targetName(application.getApplicationName())
                .summary(operation)
                .createdAt(now())
                .build());
    }

    private void recordCredentialAudit(
            String operation,
            IntegrationApplicationRecord application,
            IntegrationApplicationCredentialRecord credential,
            CurrentActor actor) {
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(AuditAction.DELETE)
                .operationName(operation)
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(true)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType("INTEGRATION_CREDENTIAL")
                .targetId(credential.getId())
                .targetName(application.getApplicationName())
                .summary(operation)
                .createdAt(now())
                .build());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null
                ? null
                : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private enum ApplicationStatus {
        ACTIVE,
        DISABLED,
        REVOKED;

        private static ApplicationStatus parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("接入应用状态不正确");
            }
        }
    }

    private record IssuedSecret(String secret, Instant expiresAt) {
    }
}
