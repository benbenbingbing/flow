package com.workflow.openapi.webhook.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.http.RestEndpointPolicy;
import com.workflow.openapi.api.request.CreateWebhookEndpointRequest;
import com.workflow.openapi.api.request.RotateWebhookSecretRequest;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEndpointMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookSubscriptionMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookEndpointRecord;
import com.workflow.openapi.webhook.security.WebhookSecretCipher;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebhookAdministrationServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.parse("2026-07-29T08:30:00");

    private IntegrationApplicationMapper applicationMapper;
    private WebhookEndpointMapper endpointMapper;
    private WebhookSubscriptionMapper subscriptionMapper;
    private WebhookSecretCipher secretCipher;
    private RestEndpointPolicy endpointPolicy;
    private SystemAuditPort auditPort;
    private WebhookAdministrationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(IntegrationApplicationMapper.class);
        endpointMapper = mock(WebhookEndpointMapper.class);
        subscriptionMapper = mock(WebhookSubscriptionMapper.class);
        secretCipher = mock(WebhookSecretCipher.class);
        endpointPolicy = mock(RestEndpointPolicy.class);
        auditPort = mock(SystemAuditPort.class);
        CurrentActorProvider actorProvider =
                () -> new CurrentActor("admin-01", "Admin");
        service = new WebhookAdministrationService(
                applicationMapper,
                endpointMapper,
                subscriptionMapper,
                secretCipher,
                endpointPolicy,
                actorProvider,
                auditPort,
                Clock.fixed(
                        Instant.parse("2026-07-29T08:30:00Z"),
                        ZoneOffset.UTC));
        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId("application-01");
        application.setStatus("ACTIVE");
        when(applicationMapper.lockById("application-01"))
                .thenReturn(application);
    }

    @Test
    void createReturnsSecretOnceAndPersistsOnlyCiphertext() {
        when(secretCipher.generateSecret())
                .thenReturn("plain-secret-with-at-least-32-bytes");
        when(secretCipher.encrypt(
                "plain-secret-with-at-least-32-bytes"))
                .thenReturn("v1.nonce.ciphertext");
        when(subscriptionMapper.findByEndpoint(
                eq("application-01"),
                any())).thenReturn(List.of());

        var issued = service.create(
                "application-01",
                new CreateWebhookEndpointRequest(
                        "ERP callback",
                        "https://hooks.example.com:443/flow",
                        Set.of(
                                "com.flow.process.started.v1",
                                "com.flow.process.completed.v1")));

        ArgumentCaptor<WebhookEndpointRecord> endpoint =
                ArgumentCaptor.forClass(WebhookEndpointRecord.class);
        verify(endpointMapper).insert(endpoint.capture());
        assertEquals(
                "plain-secret-with-at-least-32-bytes",
                issued.signingSecret());
        assertEquals(
                "v1.nonce.ciphertext",
                endpoint.getValue().getSecretCiphertext());
        assertNotEquals(
                issued.signingSecret(),
                endpoint.getValue().getSecretCiphertext());
        assertEquals(
                "https://hooks.example.com/flow",
                endpoint.getValue().getEndpointUrl());
        assertEquals(
                "32-bytes",
                endpoint.getValue().getSecretHint());
        assertNotNull(endpoint.getValue().getEndpointHash());
        verify(endpointPolicy).validate(any(URI.class));
        verify(subscriptionMapper, times(2)).insert(
                any(),
                eq("application-01"),
                eq(endpoint.getValue().getId()),
                any(),
                eq("ACTIVE"),
                eq("admin-01"),
                eq(NOW));
        verify(auditPort).record(any());
    }

    @Test
    void rotationPreservesPreviousSecretForExactlyFortyEightHours() {
        WebhookEndpointRecord endpoint = new WebhookEndpointRecord();
        endpoint.setId("endpoint-01");
        endpoint.setApplicationId("application-01");
        endpoint.setEndpointName("ERP callback");
        endpoint.setEndpointUrl("https://hooks.example.com/flow");
        endpoint.setStatus("ACTIVE");
        endpoint.setSecretCiphertext("old-ciphertext");
        endpoint.setSecretVersion(4L);
        endpoint.setSecretHint("old-hint");
        endpoint.setVersion(6L);
        endpoint.setCreateTime(NOW.minusDays(1));
        endpoint.setUpdateTime(NOW.minusDays(1));
        when(endpointMapper.lockOwned(
                "application-01",
                "endpoint-01")).thenReturn(endpoint);
        when(secretCipher.generateSecret())
                .thenReturn("replacement-secret-with-32-bytes");
        when(secretCipher.encrypt(
                "replacement-secret-with-32-bytes"))
                .thenReturn("new-ciphertext");
        when(endpointMapper.rotateSecret(
                "application-01",
                "endpoint-01",
                6,
                "new-ciphertext",
                "32-bytes",
                NOW.plusHours(48),
                "admin-01",
                NOW)).thenReturn(1);
        when(subscriptionMapper.findByEndpoint(
                "application-01",
                "endpoint-01")).thenReturn(List.of());

        var issued = service.rotateSecret(
                "application-01",
                "endpoint-01",
                new RotateWebhookSecretRequest(6L));

        assertEquals(
                "replacement-secret-with-32-bytes",
                issued.signingSecret());
        assertEquals(
                "old-ciphertext",
                endpoint.getPreviousSecretCiphertext());
        assertEquals(4L, endpoint.getPreviousSecretVersion());
        assertEquals(
                NOW.plusHours(48),
                endpoint.getPreviousSecretValidUntil());
        assertEquals("new-ciphertext", endpoint.getSecretCiphertext());
        assertEquals(5L, endpoint.getSecretVersion());
        assertEquals(7L, endpoint.getVersion());
        verify(auditPort).record(any());
    }
}
