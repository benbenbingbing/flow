package com.workflow.openapi.webhook.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.core.error.ForbiddenException;
import com.workflow.openapi.api.response.WebhookValidationView;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.webhook.delivery.WebhookHttpClient;
import com.workflow.openapi.webhook.delivery.WebhookHttpResult;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEndpointMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookEndpointRecord;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class WebhookValidationService {

    static final String EVENT_TYPE =
            "com.flow.webhook.validation.v1";

    private final IntegrationApplicationMapper applicationMapper;
    private final WebhookEndpointMapper endpointMapper;
    private final WebhookHttpClient httpClient;
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WebhookValidationService(
            IntegrationApplicationMapper applicationMapper,
            WebhookEndpointMapper endpointMapper,
            WebhookHttpClient httpClient,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(
                applicationMapper,
                endpointMapper,
                httpClient,
                actorProvider,
                auditPort,
                objectMapper,
                Clock.systemUTC());
    }

    WebhookValidationService(
            IntegrationApplicationMapper applicationMapper,
            WebhookEndpointMapper endpointMapper,
            WebhookHttpClient httpClient,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.endpointMapper = endpointMapper;
        this.httpClient = httpClient;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public WebhookValidationView send(
            String applicationId,
            String endpointId) {
        CurrentActor actor = requireActor();
        requireActiveApplication(applicationId);
        WebhookEndpointRecord endpoint =
                endpointMapper.selectById(endpointId);
        if (endpoint == null
                || !applicationId.equals(
                endpoint.getApplicationId())) {
            throw new IllegalArgumentException(
                    "Webhook 端点不存在");
        }
        if (!"ACTIVE".equals(endpoint.getStatus())) {
            throw new IllegalArgumentException(
                    "Webhook 端点未启用");
        }

        String validationId = IdWorker.getIdStr();
        Instant sentAt = clock.instant();
        WebhookDeliveryWorkRecord delivery = validationDelivery(
                applicationId,
                endpoint,
                validationId,
                sentAt);

        // Required audit is durable before the external side effect.
        audit(endpoint, actor, validationId);
        try {
            WebhookHttpResult result = httpClient.send(delivery);
            String outcome = result.statusCode() >= 200
                    && result.statusCode() < 300
                    ? "SUCCEEDED"
                    : "HTTP_ERROR";
            return new WebhookValidationView(
                    validationId,
                    outcome,
                    result.statusCode(),
                    sentAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new WebhookValidationView(
                    validationId,
                    "TRANSPORT_ERROR",
                    null,
                    sentAt);
        } catch (IOException exception) {
            return new WebhookValidationView(
                    validationId,
                    "TRANSPORT_ERROR",
                    null,
                    sentAt);
        }
    }

    private WebhookDeliveryWorkRecord validationDelivery(
            String applicationId,
            WebhookEndpointRecord endpoint,
            String validationId,
            Instant sentAt) {
        return new WebhookDeliveryWorkRecord(
                validationId,
                applicationId,
                "validation",
                validationId,
                0,
                "PROCESSING",
                0,
                1,
                null,
                0,
                null,
                endpoint.getSecretCiphertext(),
                endpoint.getSecretVersion(),
                endpoint.getEndpointUrl(),
                endpoint.getStatus(),
                "ACTIVE",
                EVENT_TYPE,
                validationId,
                payload(validationId, sentAt));
    }

    private String payload(
            String validationId,
            Instant sentAt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("specversion", "1.0");
        root.put("id", validationId);
        root.put("source", "/flow/integration");
        root.put("type", EVENT_TYPE);
        root.put("time", sentAt.toString());
        root.put("datacontenttype", "application/json");
        ObjectNode data = root.putObject("data");
        data.put("validationId", validationId);
        data.put("sentAt", sentAt.toString());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Webhook 验证事件无法序列化",
                    exception);
        }
    }

    private void requireActiveApplication(
            String applicationId) {
        IntegrationApplicationRecord application =
                applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException(
                    "集成应用不存在");
        }
        if (!"ACTIVE".equals(application.getStatus())) {
            throw new IllegalArgumentException(
                    "集成应用未启用");
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

    private void audit(
            WebhookEndpointRecord endpoint,
            CurrentActor actor,
            String validationId) {
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.INTEGRATION)
                .action(AuditAction.OTHER)
                .operationName("发送 Webhook 验证事件")
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(true)
                .operatorId(actor.userId())
                .operatorName(actor.username())
                .targetType("WEBHOOK_ENDPOINT")
                .targetId(endpoint.getId())
                .targetName(endpoint.getEndpointName())
                .summary("发送 Webhook 验证事件 "
                        + validationId)
                .createdAt(LocalDateTime.ofInstant(
                        clock.instant(),
                        ZoneOffset.UTC))
                .build());
    }
}
