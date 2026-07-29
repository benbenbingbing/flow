package com.workflow.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PinnedHttpTransportTest {

    private HttpServer server;
    private PinnedHttpTransport transport;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        WorkflowHttpProperties properties =
                new WorkflowHttpProperties();
        properties.setAllowHttp(true);
        properties.setMaxResponseBytes(128 * 1024);
        transport = new PinnedHttpTransport(
                new RestEndpointPolicy(properties),
                properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void productionEntryRejectsPrivateDestinations() {
        server.start();

        assertThrows(
                IllegalArgumentException.class,
                () -> transport.execute(request(
                        "/private",
                        64 * 1024,
                        false)));
    }

    @Test
    void pinsApprovedConnectionAndNeverFollowsRedirects()
            throws Exception {
        AtomicInteger targetCalls = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add(
                    "Location",
                    endpoint("/target").toString());
            respond(exchange, 302, "move");
        });
        server.createContext("/target", exchange -> {
            targetCalls.incrementAndGet();
            respond(exchange, 204, "");
        });
        server.start();

        HttpTransportResult result = transport.executeLegacy(
                request("/redirect", 64 * 1024, false),
                true);

        assertEquals(302, result.statusCode());
        assertEquals("move", result.body());
        assertEquals(0, targetCalls.get());
    }

    @Test
    void truncatesOnlyWhenCallerExplicitlyRequestsIt()
            throws Exception {
        server.createContext("/large", exchange -> respond(
                exchange,
                500,
                "x".repeat(2048)));
        server.start();

        HttpTransportResult truncated = transport.executeLegacy(
                request("/large", 1024, true),
                true);

        assertEquals(1024, truncated.body().length());
        assertTrue(truncated.responseTruncated());
        assertThrows(
                IOException.class,
                () -> transport.executeLegacy(
                        request("/large", 1024, false),
                        true));
    }

    @Test
    void rejectsUnsupportedMethodsOversizedUrisAndHeaders() {
        server.start();
        HttpTransportRequest valid = request(
                "/bounded",
                1024,
                false);
        assertThrows(
                IllegalArgumentException.class,
                () -> transport.execute(new HttpTransportRequest(
                        "TRACE",
                        valid.uri(),
                        valid.headers(),
                        valid.body(),
                        valid.timeoutMillis(),
                        valid.allowedHosts(),
                        valid.maxResponseBytes(),
                        false)));
        assertThrows(
                IllegalArgumentException.class,
                () -> transport.execute(new HttpTransportRequest(
                        "GET",
                        endpoint("/" + "x".repeat(9000)),
                        valid.headers(),
                        null,
                        valid.timeoutMillis(),
                        valid.allowedHosts(),
                        valid.maxResponseBytes(),
                        false)));
        assertThrows(
                IllegalArgumentException.class,
                () -> transport.execute(new HttpTransportRequest(
                        "GET",
                        valid.uri(),
                        Map.of("X-Large", "x".repeat(9000)),
                        null,
                        valid.timeoutMillis(),
                        valid.allowedHosts(),
                        valid.maxResponseBytes(),
                        false)));
    }

    @Test
    void preservesExplicitCloudEventsContentType() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/webhook", exchange -> {
            contentType.set(exchange.getRequestHeaders()
                    .getFirst("Content-Type"));
            respond(exchange, 204, "");
        });
        server.start();
        HttpTransportRequest request = new HttpTransportRequest(
                "POST",
                endpoint("/webhook"),
                Map.of(
                        "Content-Type",
                        "application/cloudevents+json"),
                "{\"specversion\":\"1.0\"}",
                2000,
                Set.of("127.0.0.1"),
                1024,
                false);

        transport.executeLegacy(request, true);

        assertEquals(
                "application/cloudevents+json",
                contentType.get());
    }

    private HttpTransportRequest request(
            String path,
            int maxResponseBytes,
            boolean truncate) {
        return new HttpTransportRequest(
                "POST",
                endpoint(path),
                Map.of("Content-Type", "application/json"),
                "{\"ok\":true}",
                2000,
                Set.of("127.0.0.1"),
                maxResponseBytes,
                truncate);
    }

    private URI endpoint(String path) {
        return URI.create(
                "http://127.0.0.1:"
                        + server.getAddress().getPort()
                        + path);
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(
                status,
                bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
