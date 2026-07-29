package com.workflow.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.contracts.integration.IntegrationRuntimeContext;
import com.workflow.contracts.integration.IntegrationSecretResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HttpIntegrationConnectorTest {

    private HttpConnectorConfigurationProvider provider;
    private IntegrationSecretResolver secretResolver;
    private PinnedHttpTransport transport;
    private SystemAuditPort auditPort;
    private HttpIntegrationConnector connector;

    @BeforeEach
    void setUp() {
        provider = mock(HttpConnectorConfigurationProvider.class);
        secretResolver = mock(IntegrationSecretResolver.class);
        transport = mock(PinnedHttpTransport.class);
        auditPort = mock(SystemAuditPort.class);
        connector = new HttpIntegrationConnector(
                provider,
                secretResolver,
                transport,
                new ObjectMapper(),
                auditPort,
                new SimpleMeterRegistry());
        when(provider.findActive("config-1"))
                .thenReturn(configuration(1));
        when(secretResolver.resolve(
                "secret://integration/app-1/api-token"))
                .thenReturn("secret-token-value");
    }

    @Test
    void sendsMappedRequestAndReturnsOnlyDeclaredResponseFields()
            throws Exception {
        when(transport.execute(any())).thenReturn(
                new HttpTransportResult(
                        200,
                        """
                        {
                          "data": {"id": "remote-42"},
                          "internalToken": "must-not-escape"
                        }
                        """,
                        null,
                        false));

        IntegrationResult result = connector.execute(request());

        assertEquals(true, result.isSuccess());
        assertEquals(200, result.getData().get("httpStatus"));
        assertEquals("remote-42", result.getData().get("remoteId"));
        assertFalse(result.getData().containsKey("internalToken"));
        ArgumentCaptor<HttpTransportRequest> outbound =
                ArgumentCaptor.forClass(HttpTransportRequest.class);
        verify(transport).execute(outbound.capture());
        assertEquals(
                "Bearer secret-token-value",
                outbound.getValue().headers().get("Authorization"));
        assertEquals(
                "idem-1",
                outbound.getValue().headers().get("Idempotency-Key"));
        assertEquals(
                "tenant-1",
                outbound.getValue().headers().get("X-Tenant"));
        assertEquals(
                "{\"orderId\":\"order-1\"}",
                outbound.getValue().body());
        assertEquals(
                "https://erp.example.com/api/orders?dryRun=true",
                outbound.getValue().uri().toString());
        verify(auditPort, times(2)).record(any());
    }

    @Test
    void retriesOnlyExplicitTransientStatusesWithinConfiguredBound()
            throws Exception {
        when(provider.findActive("config-1"))
                .thenReturn(configuration(2));
        when(transport.execute(any()))
                .thenReturn(new HttpTransportResult(503, "", null, false))
                .thenReturn(new HttpTransportResult(
                        200,
                        "{\"data\":{\"id\":\"remote-42\"}}",
                        null,
                        false));

        IntegrationResult result = connector.execute(request());

        assertEquals(true, result.isSuccess());
        verify(transport, times(2)).execute(any());
    }

    @Test
    void doesNotRetryClientErrorsOrRedirects() throws Exception {
        when(provider.findActive("config-1"))
                .thenReturn(configuration(4));
        when(transport.execute(any()))
                .thenReturn(new HttpTransportResult(
                        302,
                        "https://metadata.invalid",
                        null,
                        false));

        IntegrationResult result = connector.execute(request());

        assertFalse(result.isSuccess());
        assertEquals("CONNECTOR_REMOTE_REJECTED", result.getCode());
        verify(transport).execute(any());
    }

    @Test
    void neverReturnsSecretOrUnderlyingExceptionMessages() {
        when(secretResolver.resolve(any()))
                .thenThrow(new IllegalStateException(
                        "secret-token-value at https://internal.example"));

        IntegrationResult result = connector.execute(request());

        assertFalse(result.isSuccess());
        assertFalse(result.getMessage().contains("secret-token-value"));
        assertFalse(result.getMessage().contains("internal.example"));
        assertEquals(Map.of(), result.getData());
    }

    @Test
    void requiredAttemptAuditFailurePreventsRemoteSideEffect()
            throws Exception {
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditPort).record(any());

        IntegrationResult result = connector.execute(request());

        assertFalse(result.isSuccess());
        assertEquals("CONNECTOR_AUDIT_FAILED", result.getCode());
        verify(transport, never()).execute(any());
    }

    @Test
    void outcomeAuditFailureDoesNotRewriteSuccessfulRemoteResult()
            throws Exception {
        when(transport.execute(any())).thenReturn(
                new HttpTransportResult(
                        200,
                        "{\"data\":{\"id\":\"remote-42\"}}",
                        null,
                        false));
        doNothing()
                .doThrow(new IllegalStateException("audit unavailable"))
                .when(auditPort).record(any());

        IntegrationResult result = connector.execute(request());

        assertEquals(true, result.isSuccess());
        assertEquals("remote-42", result.getData().get("remoteId"));
        verify(transport).execute(any());
    }

    @Test
    void rejectsMissingIdempotencyBeforeResolvingSecrets() {
        IntegrationRequest invalid = IntegrationRequest.builder()
                .connectorConfigId("config-1")
                .operation("sync-order")
                .idempotencyKey("")
                .parameters(Map.of())
                .build();

        IntegrationResult result = connector.execute(invalid);

        assertFalse(result.isSuccess());
        verify(secretResolver, never()).resolve(any());
        assertNull(result.getData().get("httpStatus"));
    }

    private HttpConnectorConfiguration configuration(int attempts) {
        var authentication =
                new HttpConnectorConfiguration.Authentication(
                        HttpConnectorConfiguration.Authentication.Type.BEARER,
                        null,
                        null,
                        "secret://integration/app-1/api-token");
        var operation = new HttpConnectorConfiguration.Operation(
                "POST",
                "/orders",
                Map.of("dryRun", "$input.dryRun"),
                Map.of("X-Tenant", "$context.tenantId"),
                Map.of("/orderId", "$input.order.id"),
                Map.of("remoteId", "/data/id"),
                Set.of(200),
                authentication,
                3000,
                attempts);
        return new HttpConnectorConfiguration(
                "config-1",
                "app-1",
                URI.create("https://erp.example.com/api"),
                Set.of("erp.example.com"),
                Map.of("sync-order", operation));
    }

    private IntegrationRequest request() {
        return IntegrationRequest.builder()
                .connectorConfigId("config-1")
                .operation("sync-order")
                .idempotencyKey("idem-1")
                .parameters(Map.of(
                        "dryRun", true,
                        "order", Map.of("id", "order-1")))
                .runtimeContext(new IntegrationRuntimeContext(
                        "source-1",
                        "FORM_INIT",
                        "FORM",
                        "form-1",
                        "release-1",
                        1,
                        "entity-1",
                        "order",
                        null,
                        "user-1",
                        "User",
                        "tenant-1",
                        "org-1",
                        "dept-1"))
                .build();
    }
}
