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
import com.workflow.openapi.api.request.ReplayWebhookDeliveryRequest;
import com.workflow.openapi.api.response.WebhookDeliveryView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryAdminRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookDeliveryAdministrationService {

    private final IntegrationApplicationMapper applicationMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final Clock clock;

    public WebhookDeliveryAdministrationService(
            IntegrationApplicationMapper applicationMapper,
            WebhookDeliveryMapper deliveryMapper,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort) {
        this(
                applicationMapper,
                deliveryMapper,
                actorProvider,
                auditPort,
                Clock.systemUTC());
    }

    WebhookDeliveryAdministrationService(
            IntegrationApplicationMapper applicationMapper,
            WebhookDeliveryMapper deliveryMapper,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.deliveryMapper = deliveryMapper;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryView> list(
            String applicationId) {
        if (applicationMapper.selectById(applicationId) == null) {
            throw new IllegalArgumentException(
                    "接入应用不存在");
        }
        return deliveryMapper.findRecentByApplication(
                        applicationId,
                        200)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public WebhookDeliveryView replay(
            String applicationId,
            String deliveryId,
            ReplayWebhookDeliveryRequest request) {
        CurrentActor actor = requireActor();
        WebhookDeliveryAdminRecord source =
                deliveryMapper.findOwnedForReplay(
                        applicationId,
                        deliveryId);
        if (source == null) {
            throw new IllegalArgumentException(
                    "Webhook 投递不存在");
        }
        if (!"DEAD".equals(source.status())) {
            throw new BusinessConflictException(
                    "WEBHOOK_DELIVERY_NOT_REPLAYABLE",
                    "只有死信投递可以人工重放");
        }
        if (!"ACTIVE".equals(source.endpointStatus())
                || !"ACTIVE".equals(
                source.subscriptionStatus())) {
            throw new BusinessConflictException(
                    "WEBHOOK_SUBSCRIPTION_DISABLED",
                    "Webhook 端点或订阅已禁用");
        }
        LocalDateTime now = now();
        if (source.eventExpiresAt() == null
                || !source.eventExpiresAt().isAfter(now)) {
            throw new BusinessConflictException(
                    "WEBHOOK_EVENT_EXPIRED",
                    "Webhook 事件已超过保留期");
        }
        if (deliveryMapper.lockReplaySequence(
                source.subscriptionId(),
                source.eventId()) == null) {
            throw new IllegalStateException(
                    "Webhook 重放序列锁定失败");
        }
        int sequence = deliveryMapper.findMaxReplaySequence(
                source.subscriptionId(),
                source.eventId()) + 1;
        String replayId = IdWorker.getIdStr();
        if (deliveryMapper.insert(
                replayId,
                applicationId,
                source.subscriptionId(),
                source.eventId(),
                sequence,
                8,
                source.currentSecretCiphertext(),
                source.currentSecretVersion(),
                actor.userId(),
                now) != 1) {
            throw new IllegalStateException(
                    "Webhook 重放记录创建失败");
        }
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(AuditAction.RETRY)
                .operationName("重放 Webhook 死信")
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(true)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType("WEBHOOK_DELIVERY")
                .targetId(replayId)
                .targetName(source.eventId())
                .summary(request.reason().trim())
                .createdAt(now)
                .build());
        return new WebhookDeliveryView(
                replayId,
                applicationId,
                source.endpointId(),
                source.endpointName(),
                source.eventId(),
                source.eventType(),
                sequence,
                "PENDING",
                0,
                8,
                instant(now),
                null,
                null,
                null,
                null,
                null,
                instant(now));
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

    private WebhookDeliveryView toView(
            WebhookDeliveryAdminRecord value) {
        return new WebhookDeliveryView(
                value.id(),
                value.applicationId(),
                value.endpointId(),
                value.endpointName(),
                value.eventId(),
                value.eventType(),
                value.replaySequence(),
                value.status(),
                value.attemptCount(),
                value.maxAttempts(),
                instant(value.nextAttemptAt()),
                value.responseStatus(),
                value.errorCode(),
                value.errorMessage(),
                instant(value.lastAttemptAt()),
                instant(value.deliveredAt()),
                instant(value.createTime()));
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
