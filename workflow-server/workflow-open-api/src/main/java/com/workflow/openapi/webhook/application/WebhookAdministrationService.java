package com.workflow.openapi.webhook.application;

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
import com.workflow.http.RestEndpointPolicy;
import com.workflow.openapi.api.request.CreateWebhookEndpointRequest;
import com.workflow.openapi.api.request.RotateWebhookSecretRequest;
import com.workflow.openapi.api.request.UpdateWebhookEndpointRequest;
import com.workflow.openapi.api.response.IssuedWebhookSecretView;
import com.workflow.openapi.api.response.WebhookEndpointView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEndpointMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookSubscriptionMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookEndpointRecord;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookSubscriptionRecord;
import com.workflow.openapi.webhook.security.WebhookSecretCipher;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookAdministrationService {

    private static final Set<String> EVENT_TYPES = Set.of(
            "com.flow.process.started.v1",
            "com.flow.task.created.v1",
            "com.flow.task.completed.v1",
            "com.flow.process.completed.v1",
            "com.flow.process.terminated.v1",
            "com.flow.process.failed.v1");
    private static final Set<String> STATUSES =
            Set.of("ACTIVE", "DISABLED");

    private final IntegrationApplicationMapper applicationMapper;
    private final WebhookEndpointMapper endpointMapper;
    private final WebhookSubscriptionMapper subscriptionMapper;
    private final WebhookSecretCipher secretCipher;
    private final RestEndpointPolicy endpointPolicy;
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final Clock clock;

    @Autowired
    public WebhookAdministrationService(
            IntegrationApplicationMapper applicationMapper,
            WebhookEndpointMapper endpointMapper,
            WebhookSubscriptionMapper subscriptionMapper,
            WebhookSecretCipher secretCipher,
            RestEndpointPolicy endpointPolicy,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort) {
        this(
                applicationMapper,
                endpointMapper,
                subscriptionMapper,
                secretCipher,
                endpointPolicy,
                actorProvider,
                auditPort,
                Clock.systemUTC());
    }

    WebhookAdministrationService(
            IntegrationApplicationMapper applicationMapper,
            WebhookEndpointMapper endpointMapper,
            WebhookSubscriptionMapper subscriptionMapper,
            WebhookSecretCipher secretCipher,
            RestEndpointPolicy endpointPolicy,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.endpointMapper = endpointMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.secretCipher = secretCipher;
        this.endpointPolicy = endpointPolicy;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointView> list(String applicationId) {
        requireApplication(applicationId);
        Map<String, Set<String>> eventTypes =
                subscriptionMapper.findByApplication(applicationId)
                        .stream()
                        .filter(value -> "ACTIVE".equals(
                                value.status()))
                        .collect(Collectors.groupingBy(
                                WebhookSubscriptionRecord::endpointId,
                                Collectors.mapping(
                                        WebhookSubscriptionRecord::eventType,
                                        Collectors.toCollection(
                                                LinkedHashSet::new))));
        return endpointMapper.findByApplicationId(applicationId)
                .stream()
                .map(endpoint -> toView(
                        endpoint,
                        eventTypes.getOrDefault(
                                endpoint.getId(),
                                Set.of())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedWebhookSecretView create(
            String applicationId,
            CreateWebhookEndpointRequest request) {
        CurrentActor actor = requireActor();
        requireConfigurableApplication(applicationId);
        String url = normalizeUrl(request.endpointUrl());
        Set<String> eventTypes = validateEventTypes(
                request.eventTypes());
        String secret = secretCipher.generateSecret();
        LocalDateTime now = now();
        WebhookEndpointRecord endpoint =
                new WebhookEndpointRecord();
        endpoint.setId(IdWorker.getIdStr());
        endpoint.setApplicationId(applicationId);
        endpoint.setEndpointName(request.endpointName().trim());
        endpoint.setEndpointUrl(url);
        endpoint.setEndpointHash(hash(url));
        endpoint.setStatus("ACTIVE");
        endpoint.setSecretCiphertext(
                secretCipher.encrypt(secret));
        endpoint.setSecretVersion(1L);
        endpoint.setSecretHint(hint(secret));
        endpoint.setVersion(0L);
        endpoint.setCreatedBy(actor.userId());
        endpoint.setUpdatedBy(actor.userId());
        endpoint.setCreateTime(now);
        endpoint.setUpdateTime(now);
        endpointMapper.insert(endpoint);
        synchronizeSubscriptions(
                endpoint,
                eventTypes,
                actor.userId(),
                now);
        audit(
                AuditAction.CREATE,
                "创建 Webhook 端点",
                endpoint,
                actor);
        return new IssuedWebhookSecretView(
                toView(endpoint, eventTypes),
                secret);
    }

    @Transactional(rollbackFor = Exception.class)
    public WebhookEndpointView update(
            String applicationId,
            String endpointId,
            UpdateWebhookEndpointRequest request) {
        CurrentActor actor = requireActor();
        requireConfigurableApplication(applicationId);
        WebhookEndpointRecord endpoint =
                requireLockedEndpoint(
                        applicationId,
                        endpointId,
                        request.expectedVersion());
        String status = request.status()
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "Webhook 端点状态无效");
        }
        String url = normalizeUrl(request.endpointUrl());
        Set<String> eventTypes = validateEventTypes(
                request.eventTypes());
        LocalDateTime now = now();
        if (endpointMapper.updateConfiguration(
                applicationId,
                endpointId,
                request.expectedVersion(),
                request.endpointName().trim(),
                url,
                hash(url),
                status,
                actor.userId(),
                now) != 1) {
            throw versionConflict();
        }
        endpoint.setEndpointName(request.endpointName().trim());
        endpoint.setEndpointUrl(url);
        endpoint.setEndpointHash(hash(url));
        endpoint.setStatus(status);
        endpoint.setVersion(request.expectedVersion() + 1);
        endpoint.setUpdatedBy(actor.userId());
        endpoint.setUpdateTime(now);
        synchronizeSubscriptions(
                endpoint,
                eventTypes,
                actor.userId(),
                now);
        audit(
                AuditAction.CONFIGURE,
                "更新 Webhook 端点",
                endpoint,
                actor);
        return toView(endpoint, eventTypes);
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedWebhookSecretView rotateSecret(
            String applicationId,
            String endpointId,
            RotateWebhookSecretRequest request) {
        CurrentActor actor = requireActor();
        requireConfigurableApplication(applicationId);
        WebhookEndpointRecord endpoint =
                requireLockedEndpoint(
                        applicationId,
                        endpointId,
                        request.expectedVersion());
        String secret = secretCipher.generateSecret();
        String ciphertext = secretCipher.encrypt(secret);
        LocalDateTime now = now();
        if (endpointMapper.rotateSecret(
                applicationId,
                endpointId,
                request.expectedVersion(),
                ciphertext,
                hint(secret),
                now.plusHours(48),
                actor.userId(),
                now) != 1) {
            throw versionConflict();
        }
        endpoint.setPreviousSecretCiphertext(
                endpoint.getSecretCiphertext());
        endpoint.setPreviousSecretVersion(
                endpoint.getSecretVersion());
        endpoint.setPreviousSecretValidUntil(
                now.plusHours(48));
        endpoint.setSecretCiphertext(ciphertext);
        endpoint.setSecretVersion(
                endpoint.getSecretVersion() + 1);
        endpoint.setSecretHint(hint(secret));
        endpoint.setVersion(request.expectedVersion() + 1);
        endpoint.setUpdatedBy(actor.userId());
        endpoint.setUpdateTime(now);
        audit(
                AuditAction.CONFIGURE,
                "轮换 Webhook 签名密钥",
                endpoint,
                actor);
        Set<String> eventTypes =
                activeEventTypes(applicationId, endpointId);
        return new IssuedWebhookSecretView(
                toView(endpoint, eventTypes),
                secret);
    }

    private void synchronizeSubscriptions(
            WebhookEndpointRecord endpoint,
            Set<String> requested,
            String actorId,
            LocalDateTime now) {
        Map<String, WebhookSubscriptionRecord> existing =
                subscriptionMapper.findByEndpoint(
                                endpoint.getApplicationId(),
                                endpoint.getId())
                        .stream()
                        .collect(Collectors.toMap(
                                WebhookSubscriptionRecord::eventType,
                                value -> value));
        for (String eventType : EVENT_TYPES) {
            String status = requested.contains(eventType)
                    ? "ACTIVE"
                    : "DISABLED";
            WebhookSubscriptionRecord subscription =
                    existing.get(eventType);
            if (subscription == null) {
                if ("ACTIVE".equals(status)) {
                    subscriptionMapper.insert(
                            IdWorker.getIdStr(),
                            endpoint.getApplicationId(),
                            endpoint.getId(),
                            eventType,
                            status,
                            actorId,
                            now);
                }
            } else if (!status.equals(subscription.status())) {
                subscriptionMapper.updateStatus(
                        subscription.id(),
                        endpoint.getApplicationId(),
                        status,
                        actorId,
                        now);
            }
        }
    }

    private Set<String> activeEventTypes(
            String applicationId,
            String endpointId) {
        return subscriptionMapper.findByEndpoint(
                        applicationId,
                        endpointId)
                .stream()
                .filter(value -> "ACTIVE".equals(
                        value.status()))
                .map(WebhookSubscriptionRecord::eventType)
                .collect(Collectors.toUnmodifiableSet());
    }

    private WebhookEndpointRecord requireLockedEndpoint(
            String applicationId,
            String endpointId,
            long expectedVersion) {
        WebhookEndpointRecord endpoint =
                endpointMapper.lockOwned(
                        applicationId,
                        endpointId);
        if (endpoint == null) {
            throw new IllegalArgumentException(
                    "Webhook 端点不存在");
        }
        if (endpoint.getVersion() == null
                || endpoint.getVersion() != expectedVersion) {
            throw versionConflict();
        }
        return endpoint;
    }

    private IntegrationApplicationRecord requireApplication(
            String applicationId) {
        IntegrationApplicationRecord application =
                applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException(
                    "接入应用不存在");
        }
        return application;
    }

    private void requireConfigurableApplication(
            String applicationId) {
        IntegrationApplicationRecord application =
                applicationMapper.lockById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException(
                    "接入应用不存在");
        }
        if ("REVOKED".equals(application.getStatus())) {
            throw new BusinessConflictException(
                    "INTEGRATION_APPLICATION_REVOKED",
                    "接入应用已吊销");
        }
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

    private Set<String> validateEventTypes(Set<String> values) {
        Set<String> normalized = values.stream()
                .map(String::trim)
                .collect(Collectors.toCollection(
                        LinkedHashSet::new));
        if (normalized.isEmpty()
                || !EVENT_TYPES.containsAll(normalized)) {
            throw new IllegalArgumentException(
                    "Webhook 事件类型无效");
        }
        return Set.copyOf(normalized);
    }

    private String normalizeUrl(String rawUrl) {
        try {
            URI input = new URI(rawUrl.trim());
            if (input.getRawQuery() != null
                    || input.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "Webhook URL 不允许查询参数或片段");
            }
            endpointPolicy.validate(input);
            String scheme = input.getScheme()
                    .toLowerCase(Locale.ROOT);
            String host = input.getHost()
                    .toLowerCase(Locale.ROOT);
            int port = input.getPort();
            if (("https".equals(scheme) && port == 443)
                    || ("http".equals(scheme)
                    && port == 80)) {
                port = -1;
            }
            return new URI(
                    scheme,
                    null,
                    host,
                    port,
                    input.getRawPath() == null
                            || input.getRawPath().isBlank()
                            ? "/"
                            : input.getRawPath(),
                    null,
                    null).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "Webhook URL 格式无效",
                    exception);
        }
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "运行环境不支持 SHA-256",
                    exception);
        }
    }

    private String hint(String secret) {
        return secret.substring(secret.length() - 8);
    }

    private BusinessConflictException versionConflict() {
        return new BusinessConflictException(
                "WEBHOOK_ENDPOINT_VERSION_CONFLICT",
                "Webhook 端点已被其他管理员修改");
    }

    private WebhookEndpointView toView(
            WebhookEndpointRecord endpoint,
            Set<String> eventTypes) {
        return new WebhookEndpointView(
                endpoint.getId(),
                endpoint.getApplicationId(),
                endpoint.getEndpointName(),
                endpoint.getEndpointUrl(),
                endpoint.getStatus(),
                Set.copyOf(eventTypes),
                endpoint.getSecretVersion(),
                endpoint.getSecretHint(),
                endpoint.getVersion(),
                instant(endpoint.getCreateTime()),
                instant(endpoint.getUpdateTime()));
    }

    private void audit(
            AuditAction action,
            String operation,
            WebhookEndpointRecord endpoint,
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
                .targetType("WEBHOOK_ENDPOINT")
                .targetId(endpoint.getId())
                .targetName(endpoint.getEndpointName())
                .summary(operation)
                .createdAt(now())
                .build());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
    }

    private Instant instant(LocalDateTime value) {
        return value == null
                ? null
                : value.toInstant(ZoneOffset.UTC);
    }
}
