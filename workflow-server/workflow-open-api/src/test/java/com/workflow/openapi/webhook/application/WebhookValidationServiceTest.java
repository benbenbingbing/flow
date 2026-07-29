package com.workflow.openapi.webhook.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.webhook.delivery.WebhookHttpClient;
import com.workflow.openapi.webhook.delivery.WebhookHttpResult;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEndpointMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookEndpointRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class WebhookValidationServiceTest {

    private IntegrationApplicationMapper applicationMapper;
    private WebhookEndpointMapper endpointMapper;
    private WebhookHttpClient httpClient;
    private SystemAuditPort auditPort;
    private WebhookValidationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(IntegrationApplicationMapper.class);
        endpointMapper = mock(WebhookEndpointMapper.class);
        httpClient = mock(WebhookHttpClient.class);
        auditPort = mock(SystemAuditPort.class);
        CurrentActorProvider actorProvider =
                () -> new CurrentActor("admin-01", "Admin");
        service = new WebhookValidationService(
                applicationMapper,
                endpointMapper,
                httpClient,
                actorProvider,
                auditPort,
                new ObjectMapper(),
                Clock.fixed(
                        Instant.parse("2026-07-29T10:00:00Z"),
                        ZoneOffset.UTC));

        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId("application-01");
        application.setStatus("ACTIVE");
        when(applicationMapper.selectById("application-01"))
                .thenReturn(application);

        WebhookEndpointRecord endpoint =
                new WebhookEndpointRecord();
        endpoint.setId("endpoint-01");
        endpoint.setApplicationId("application-01");
        endpoint.setEndpointName("ERP callback");
        endpoint.setEndpointUrl("https://hooks.example.com/flow");
        endpoint.setStatus("ACTIVE");
        endpoint.setSecretCiphertext("encrypted-secret");
        endpoint.setSecretVersion(2L);
        when(endpointMapper.selectById("endpoint-01"))
                .thenReturn(endpoint);
    }

    @Test
    void sendsMinimalSignedValidationEventAfterRequiredAudit()
            throws Exception {
        when(httpClient.send(any()))
                .thenReturn(new WebhookHttpResult(
                        204,
                        null,
                        false,
                        null));

        var result = service.send(
                "application-01",
                "endpoint-01");

        assertEquals("SUCCEEDED", result.result());
        assertEquals(204, result.responseStatus());
        ArgumentCaptor<WebhookDeliveryWorkRecord> delivery =
                ArgumentCaptor.forClass(
                        WebhookDeliveryWorkRecord.class);
        verify(httpClient).send(delivery.capture());
        assertEquals(
                WebhookValidationService.EVENT_TYPE,
                delivery.getValue().eventType());
        assertEquals(
                "encrypted-secret",
                delivery.getValue().signingSecretCiphertext());
        assertFalse(delivery.getValue()
                .payloadDocument()
                .contains("applicationName"));
        assertFalse(delivery.getValue()
                .payloadDocument()
                .contains("process"));
        InOrder order = inOrder(auditPort, httpClient);
        order.verify(auditPort).record(any());
        order.verify(httpClient).send(any());
    }

    @Test
    void returnsBoundedTransportErrorWithoutRemoteDetails()
            throws Exception {
        when(httpClient.send(any()))
                .thenThrow(new java.io.IOException(
                        "token=must-not-leak"));

        var result = service.send(
                "application-01",
                "endpoint-01");

        assertEquals("TRANSPORT_ERROR", result.result());
        assertEquals(null, result.responseStatus());
    }
}
