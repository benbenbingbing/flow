package com.workflow.openapi.webhook.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.http.HttpTransportRequest;
import com.workflow.http.HttpTransportResult;
import com.workflow.http.PinnedHttpTransport;
import com.workflow.http.WorkflowHttpProperties;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import com.workflow.openapi.webhook.security.WebhookSecretCipher;
import com.workflow.openapi.webhook.security.WebhookSignatureService;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebhookHttpClientTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T08:30:00Z");

    private WebhookSecretCipher cipher;
    private PinnedHttpTransport transport;
    private WorkflowHttpProperties properties;

    @BeforeEach
    void setUp() {
        cipher = new WebhookSecretCipher(
                Base64.getEncoder()
                        .encodeToString(new byte[32]),
                true);
        transport = mock(PinnedHttpTransport.class);
        properties = new WorkflowHttpProperties();
        properties.setAllowedHosts(List.of("hooks.example.com"));
    }

    @Test
    void sendsExactBodyAndVerifiableSignature() throws Exception {
        when(transport.execute(any())).thenReturn(
                new HttpTransportResult(204, "", null, false));

        WebhookHttpResult result = client(Duration.ofSeconds(2))
                .send(delivery("/hook"));

        assertEquals(204, result.statusCode());
        assertNull(result.responseExcerpt());
        ArgumentCaptor<HttpTransportRequest> request =
                ArgumentCaptor.forClass(HttpTransportRequest.class);
        verify(transport).execute(request.capture());
        HttpTransportRequest outbound = request.getValue();
        assertEquals("POST", outbound.method());
        assertEquals(
                "https://hooks.example.com/hook",
                outbound.uri().toString());
        assertEquals(
                "{\"specversion\":\"1.0\"}",
                outbound.body());
        assertEquals(
                "application/cloudevents+json",
                outbound.headers().get("Content-Type"));
        assertEquals(
                "event-01",
                outbound.headers().get("Flow-Webhook-Id"));
        assertEquals(
                "trace-01",
                outbound.headers().get("X-Trace-Id"));
        assertEquals(
                Long.toString(NOW.getEpochSecond()),
                outbound.headers().get("Flow-Webhook-Timestamp"));
        assertEquals(
                new WebhookSignatureService().sign(
                        "event-01",
                        NOW.getEpochSecond(),
                        outbound.body().getBytes(StandardCharsets.UTF_8),
                        "signing-secret"),
                outbound.headers().get("Flow-Webhook-Signature"));
        assertEquals(64 * 1024, outbound.maxResponseBytes());
        assertTrue(outbound.truncateOversizedResponse());
        assertEquals(
                java.util.Set.of("hooks.example.com"),
                outbound.allowedHosts());
    }

    @Test
    void returnsRedirectWithoutFollowingItInWebhookLayer()
            throws Exception {
        when(transport.execute(any())).thenReturn(
                new HttpTransportResult(302, "move", null, false));

        WebhookHttpResult result = client(Duration.ofSeconds(2))
                .send(delivery("/redirect"));

        assertEquals(302, result.statusCode());
        assertEquals("move", result.responseExcerpt());
    }

    @Test
    void boundsLargeErrorResponseAndRetryAfter() throws Exception {
        when(transport.execute(any())).thenReturn(
                new HttpTransportResult(
                        429,
                        "x".repeat(64 * 1024),
                        "999999",
                        true));

        WebhookHttpResult result = client(Duration.ofSeconds(2))
                .send(delivery("/limited"));

        assertEquals(429, result.statusCode());
        assertTrue(result.responseTruncated());
        assertEquals(4096, result.responseExcerpt().length());
        assertEquals(86400L, result.retryAfterSeconds());
    }

    @Test
    void flattensControlCharactersInStoredErrorResponses()
            throws Exception {
        when(transport.execute(any())).thenReturn(
                new HttpTransportResult(
                        400,
                        "line1\r\nline2\u0000",
                        null,
                        false));

        WebhookHttpResult result = client(Duration.ofSeconds(2))
                .send(delivery("/unsafe-error"));

        assertEquals("line1 line2", result.responseExcerpt());
    }

    @Test
    void propagatesTransportTimeout() throws Exception {
        when(transport.execute(any())).thenThrow(
                new SocketTimeoutException("timeout"));

        assertThrows(
                SocketTimeoutException.class,
                () -> client(Duration.ofMillis(100))
                        .send(delivery("/slow")));
    }

    private WebhookHttpClient client(Duration timeout) {
        return new WebhookHttpClient(
                cipher,
                new WebhookSignatureService(),
                transport,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                timeout);
    }

    private WebhookDeliveryWorkRecord delivery(String path) {
        return new WebhookDeliveryWorkRecord(
                "delivery-01",
                "application-01",
                "subscription-01",
                "event-01",
                0,
                "PROCESSING",
                0,
                8,
                "owner-01",
                7,
                LocalDateTime.parse("2026-07-29T08:31:00"),
                cipher.encrypt("signing-secret"),
                1,
                "https://hooks.example.com" + path,
                "ACTIVE",
                "ACTIVE",
                "com.flow.process.started.v1",
                "trace-01",
                "{\"specversion\":\"1.0\"}");
    }
}
