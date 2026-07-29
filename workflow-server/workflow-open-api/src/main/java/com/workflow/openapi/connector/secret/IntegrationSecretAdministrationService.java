package com.workflow.openapi.connector.secret;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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
import com.workflow.openapi.api.request.CreateIntegrationSecretRequest;
import com.workflow.openapi.api.request.RevokeIntegrationSecretRequest;
import com.workflow.openapi.api.request.RotateIntegrationSecretRequest;
import com.workflow.openapi.api.response.IntegrationSecretView;
import com.workflow.openapi.api.response.IssuedIntegrationSecretView;
import com.workflow.openapi.application.IntegrationSecretGenerator;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationSecretAdministrationService {

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationSecretMapper secretMapper;
    private final IntegrationSecretCipher cipher;
    private final IntegrationSecretGenerator generator;
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final Clock clock;

    @Autowired
    public IntegrationSecretAdministrationService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationSecretMapper secretMapper,
            IntegrationSecretCipher cipher,
            IntegrationSecretGenerator generator,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort) {
        this(
                applicationMapper,
                secretMapper,
                cipher,
                generator,
                actorProvider,
                auditPort,
                Clock.systemUTC());
    }

    IntegrationSecretAdministrationService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationSecretMapper secretMapper,
            IntegrationSecretCipher cipher,
            IntegrationSecretGenerator generator,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.secretMapper = secretMapper;
        this.cipher = cipher;
        this.generator = generator;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IntegrationSecretView> list(String applicationId) {
        requireApplication(applicationId, false);
        return secretMapper.findByApplication(applicationId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedIntegrationSecretView create(
            String applicationId,
            CreateIntegrationSecretRequest request) {
        CurrentActor actor = requireActor();
        requireApplication(applicationId, true);
        String name = request.secretName().trim();
        if (secretMapper.lockActive(applicationId, name) != null) {
            throw conflict("同名集成 Secret 已存在");
        }
        return insert(
                applicationId,
                name,
                1,
                valueOrGenerate(request.secretValue()),
                actor,
                AuditAction.CREATE,
                "创建集成 Secret");
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedIntegrationSecretView rotate(
            String applicationId,
            String secretName,
            RotateIntegrationSecretRequest request) {
        CurrentActor actor = requireActor();
        requireApplication(applicationId, true);
        IntegrationSecretRecord current = requireActive(
                applicationId,
                secretName);
        requireVersion(current, request.expectedSecretVersion());
        LocalDateTime now = now();
        if (secretMapper.revoke(current.getId(), actor.userId(), now) != 1) {
            throw conflict("集成 Secret 已被其他管理员修改");
        }
        return insert(
                applicationId,
                current.getSecretName(),
                current.getSecretVersion() + 1,
                valueOrGenerate(request.secretValue()),
                actor,
                AuditAction.CONFIGURE,
                "轮换集成 Secret");
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationSecretView revoke(
            String applicationId,
            String secretName,
            RevokeIntegrationSecretRequest request) {
        CurrentActor actor = requireActor();
        requireApplication(applicationId, false);
        IntegrationSecretRecord current = requireActive(
                applicationId,
                secretName);
        requireVersion(current, request.expectedSecretVersion());
        LocalDateTime now = now();
        if (secretMapper.revoke(current.getId(), actor.userId(), now) != 1) {
            throw conflict("集成 Secret 已被其他管理员修改");
        }
        current.setStatus("REVOKED");
        current.setRevokedBy(actor.userId());
        current.setRevokedAt(now);
        current.setUpdateTime(now);
        audit(
                AuditAction.DISABLE,
                "吊销集成 Secret",
                current,
                actor);
        return toView(current);
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationSecretView destroy(
            String applicationId,
            String secretId) {
        CurrentActor actor = requireActor();
        requireApplication(applicationId, false);
        IntegrationSecretRecord secret = secretMapper.selectById(secretId);
        if (secret == null
                || !applicationId.equals(secret.getApplicationId())) {
            throw new IllegalArgumentException("集成 Secret 不存在");
        }
        if (!"REVOKED".equals(secret.getStatus())) {
            throw conflict("只能销毁已吊销的集成 Secret");
        }
        LocalDateTime now = now();
        if (secretMapper.destroy(
                applicationId,
                secretId,
                actor.userId(),
                now) != 1) {
            throw conflict("集成 Secret 已被其他管理员修改");
        }
        secret.setStatus("DESTROYED");
        secret.setDestroyedBy(actor.userId());
        secret.setDestroyedAt(now);
        secret.setKeyVersion(null);
        secret.setEncryptedDataKey(null);
        secret.setDataKeyNonce(null);
        secret.setSecretCiphertext(null);
        secret.setSecretNonce(null);
        audit(
                AuditAction.DELETE,
                "销毁集成 Secret 密文",
                secret,
                actor);
        return toView(secret);
    }

    private IssuedIntegrationSecretView insert(
            String applicationId,
            String secretName,
            long secretVersion,
            String plaintext,
            CurrentActor actor,
            AuditAction action,
            String operation) {
        IntegrationSecretEnvelope envelope = cipher.encrypt(
                applicationId,
                secretName,
                secretVersion,
                plaintext);
        LocalDateTime now = now();
        IntegrationSecretRecord secret = new IntegrationSecretRecord();
        secret.setId(IdWorker.getIdStr());
        secret.setApplicationId(applicationId);
        secret.setSecretName(secretName);
        secret.setSecretVersion(secretVersion);
        secret.setStatus("ACTIVE");
        secret.setKeyVersion(envelope.keyVersion());
        secret.setEncryptedDataKey(envelope.encryptedDataKey());
        secret.setDataKeyNonce(envelope.dataKeyNonce());
        secret.setSecretCiphertext(envelope.secretCiphertext());
        secret.setSecretNonce(envelope.secretNonce());
        secret.setSecretHint(hint(plaintext));
        secret.setCreatedBy(actor.userId());
        secret.setCreateTime(now);
        secret.setUpdateTime(now);
        secretMapper.insert(secret);
        audit(action, operation, secret, actor);
        return new IssuedIntegrationSecretView(
                toView(secret),
                plaintext,
                "secret://integration/"
                        + applicationId + "/" + secretName);
    }

    private IntegrationApplicationRecord requireApplication(
            String applicationId,
            boolean configurable) {
        IntegrationApplicationRecord application =
                configurable
                        ? applicationMapper.lockById(applicationId)
                        : applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("接入应用不存在");
        }
        if (configurable
                && (!"ACTIVE".equals(application.getStatus())
                    || application.getExpiresAt() != null
                    && !application.getExpiresAt().isAfter(now()))) {
            throw conflict("接入应用当前不可配置 Secret");
        }
        return application;
    }

    private IntegrationSecretRecord requireActive(
            String applicationId,
            String secretName) {
        DatabaseIntegrationSecretResolver.SecretReference reference =
                DatabaseIntegrationSecretResolver.parse(
                        "secret://integration/"
                                + applicationId + "/" + secretName);
        IntegrationSecretRecord secret = secretMapper.lockActive(
                reference.applicationId(),
                reference.secretName());
        if (secret == null) {
            throw new IllegalArgumentException(
                    "活跃的集成 Secret 不存在");
        }
        return secret;
    }

    private void requireVersion(
            IntegrationSecretRecord secret,
            long expectedVersion) {
        if (secret.getSecretVersion() != expectedVersion) {
            throw conflict("集成 Secret 已被其他管理员修改");
        }
    }

    private String valueOrGenerate(String value) {
        String selected = value == null || value.isBlank()
                ? generator.newClientSecret()
                : value;
        int bytes = selected.getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 65536) {
            throw new IllegalArgumentException(
                    "集成 Secret 必须为 8 到 65536 字节");
        }
        return selected;
    }

    private String hint(String value) {
        int start = Math.max(0, value.length() - 8);
        return value.substring(start);
    }

    private IntegrationSecretView toView(IntegrationSecretRecord secret) {
        return new IntegrationSecretView(
                secret.getId(),
                secret.getApplicationId(),
                secret.getSecretName(),
                secret.getSecretVersion(),
                secret.getStatus(),
                secret.getSecretHint(),
                instant(secret.getCreateTime()),
                instant(secret.getRevokedAt()),
                instant(secret.getDestroyedAt()));
    }

    private void audit(
            AuditAction action,
            String operation,
            IntegrationSecretRecord secret,
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
                .targetType("INTEGRATION_SECRET")
                .targetId(secret.getId())
                .targetName(secret.getSecretName())
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

    private BusinessConflictException conflict(String message) {
        return new BusinessConflictException(
                "INTEGRATION_SECRET_CONFLICT",
                message);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
