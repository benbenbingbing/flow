package com.workflow.openapi.webhook.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.workflow.http.RestEndpointPolicy;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import com.workflow.openapi.webhook.security.WebhookSecretCipher;
import com.workflow.openapi.webhook.security.WebhookSignatureService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookHttpClientTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T08:30:00Z");
    private HttpServer server;
    private WebhookSecretCipher cipher;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        cipher = new WebhookSecretCipher(
                Base64.getEncoder()
                        .encodeToString(new byte[32]),
                true);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsExactBodyAndVerifiableSignature() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        server.createContext("/hook", exchange -> {
            body.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            signature.set(exchange.getRequestHeaders()
                    .getFirst("Flow-Webhook-Signature"));
            timestamp.set(exchange.getRequestHeaders()
                    .getFirst("Flow-Webhook-Timestamp"));
            assertEquals(
                    "application/cloudevents+json",
                    exchange.getRequestHeaders()
                            .getFirst("Content-Type"));
            assertEquals(
                    "event-01",
                    exchange.getRequestHeaders()
                            .getFirst("Flow-Webhook-Id"));
            assertEquals(
                    "trace-01",
                    exchange.getRequestHeaders()
                            .getFirst("X-Trace-Id"));
            respond(exchange, 204, new byte[0]);
        });
        server.start();
        WebhookHttpClient client = client(Duration.ofSeconds(2));

        WebhookHttpResult result = client.send(delivery("/hook"));

        assertEquals(204, result.statusCode());
        assertNull(result.responseExcerpt());
        assertEquals("{\"specversion\":\"1.0\"}", body.get());
        assertEquals(
                Long.toString(NOW.getEpochSecond()),
                timestamp.get());
        assertEquals(
                new WebhookSignatureService().sign(
                        "event-01",
                        NOW.getEpochSecond(),
                        body.get().getBytes(StandardCharsets.UTF_8),
                        "signing-secret"),
                signature.get());
    }

    @Test
    void neverFollowsRedirects() throws Exception {
        AtomicInteger redirectedRequests = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add(
                    "Location",
                    endpoint("/target"));
            respond(exchange, 302, "move".getBytes(
                    StandardCharsets.UTF_8));
        });
        server.createContext("/target", exchange -> {
            redirectedRequests.incrementAndGet();
            respond(exchange, 204, new byte[0]);
        });
        server.start();

        WebhookHttpResult result =
                client(Duration.ofSeconds(2))
                        .send(delivery("/redirect"));

        assertEquals(302, result.statusCode());
        assertEquals(0, redirectedRequests.get());
    }

    @Test
    void boundsLargeErrorResponseAndRetryAfter() throws Exception {
        byte[] response = "x".repeat(70 * 1024)
                .getBytes(StandardCharsets.UTF_8);
        server.createContext("/limited", exchange -> {
            exchange.getResponseHeaders().add(
                    "Retry-After",
                    "999999");
            respond(exchange, 429, response);
        });
        server.start();

        WebhookHttpResult result =
                client(Duration.ofSeconds(2))
                        .send(delivery("/limited"));

        assertEquals(429, result.statusCode());
        assertTrue(result.responseTruncated());
        assertEquals(4096, result.responseExcerpt().length());
        assertEquals(86400L, result.retryAfterSeconds());
    }

    @Test
    void flattensControlCharactersInStoredErrorResponses()
            throws Exception {
        server.createContext("/unsafe-error", exchange -> respond(
                exchange,
                400,
                "line1\r\nline2\u0000".getBytes(
                        StandardCharsets.UTF_8)));
        server.start();

        WebhookHttpResult result =
                client(Duration.ofSeconds(2))
                        .send(delivery("/unsafe-error"));

        assertEquals("line1 line2", result.responseExcerpt());
    }

    @Test
    void enforcesTotalRequestTimeout() throws Exception {
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(300);
                respond(exchange, 204, new byte[0]);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                exchange.close();
            }
        });
        server.start();

        assertThrows(
                HttpTimeoutException.class,
                () -> client(Duration.ofMillis(50))
                        .send(delivery("/slow")));
    }

    private WebhookHttpClient client(Duration timeout) {
        return new WebhookHttpClient(
                mock(RestEndpointPolicy.class),
                cipher,
                new WebhookSignatureService(),
                HttpClient.newBuilder()
                        .followRedirects(
                                HttpClient.Redirect.NEVER)
                        .build(),
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
                endpoint(path),
                "ACTIVE",
                "ACTIVE",
                "com.flow.process.started.v1",
                "trace-01",
                "{\"specversion\":\"1.0\"}");
    }

    private String endpoint(String path) {
        return "http://127.0.0.1:"
                + server.getAddress().getPort()
                + path;
    }

    private void respond(
            HttpExchange exchange,
            int status,
            byte[] body) throws IOException {
        exchange.sendResponseHeaders(
                status,
                body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
