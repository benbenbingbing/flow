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
    private java.util.concurrent.ExecutorService serverWorkers;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0);
        serverWorkers = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(serverWorkers);
        WorkflowHttpProperties properties =
                new WorkflowHttpProperties();
        properties.setAllowHttp(true);
        properties.setMaxResponseBytes(128 * 1024);
        // 单连接下连续成功才足以证明前一次取消归还了真实连接容量。
        properties.setMaxConcurrentPerTarget(1);
        transport = new PinnedHttpTransport(
                new RestEndpointPolicy(properties),
                properties);
    }

    @AfterEach
    void tearDown() {
        transport.close();
        server.stop(0);
        serverWorkers.shutdownNow();
    }

    @Test
    void sequentialRequestsReuseConnectionButStillValidateEveryAddress() throws Exception {
        var ports = new java.util.HashSet<Integer>();
        AtomicInteger resolutions = new AtomicInteger();
        server.createContext("/reuse", exchange -> {
            ports.add(exchange.getRemoteAddress().getPort());
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "ok");
        });
        server.start();
        var properties = new WorkflowHttpProperties();
        properties.setAllowHttp(true);
        transport.close();
        transport = new PinnedHttpTransport(new RestEndpointPolicy(properties, host -> {
            resolutions.incrementAndGet();
            return new java.net.InetAddress[]{java.net.InetAddress.getByName("127.0.0.1")};
        }), properties);
        for (int i = 0; i < 3; i++) assertEquals("ok", transport.executeLegacy(request("/reuse", 1024, false), true).body());
        assertEquals(1, ports.size());
        assertEquals(3, resolutions.get());
        assertThrows(IllegalArgumentException.class, () -> transport.execute(request("/reuse", 1024, false)));
        assertEquals(4, resolutions.get());
    }

    @Test
    void parentDeadlineStopsSlowTrickleAndConnectionCapacityRecovers() throws Exception {
        server.createContext("/trickle", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                for (int i = 0; i < 100; i++) {
                    exchange.getResponseBody().write('x');
                    exchange.getResponseBody().flush();
                    Thread.sleep(30);
                }
            } catch (IOException ignored) {
                // 客户端取消必须关闭流，服务端随后观察到写入失败。
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.createContext("/fast", exchange -> respond(exchange, 200, "ready"));
        server.start();
        long start = System.nanoTime();
        try (var deadline = com.workflow.core.concurrent.ExecutionDeadline.afterMillis(250); var scope = deadline.attach()) {
            assertThrows(IOException.class, () -> transport.executeLegacy(request("/trickle", 1024, false), true));
        }
        assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500);
        assertEquals("ready", transport.executeLegacy(request("/fast", 1024, false), true).body());
    }

    @Test
    void dnsWaitUsesRemainingBudgetAndCancelledResolverReleasesCapacity() throws Exception {
        var stopped = new java.util.concurrent.CountDownLatch(1);
        var calls = new AtomicInteger();
        var properties = new WorkflowHttpProperties();
        properties.setAllowHttp(true);
        transport.close();
        transport = new PinnedHttpTransport(new RestEndpointPolicy(properties, host -> {
            if (calls.incrementAndGet() == 1) {
                try { new java.util.concurrent.CountDownLatch(1).await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                finally { stopped.countDown(); }
            }
            return new java.net.InetAddress[]{java.net.InetAddress.getByName("127.0.0.1")};
        }), properties);
        server.createContext("/fast", exchange -> respond(exchange, 200, "ready"));
        server.start();
        try (var deadline = com.workflow.core.concurrent.ExecutionDeadline.afterMillis(150); var scope = deadline.attach()) {
            assertThrows(IOException.class, () -> transport.executeLegacy(request("/fast", 1024, false), true));
        }
        assertTrue(stopped.await(1, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("ready", transport.executeLegacy(request("/fast", 1024, false), true).body());
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
    void preservesExplicitContentType() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/target", exchange -> {
            contentType.set(exchange.getRequestHeaders()
                    .getFirst("Content-Type"));
            respond(exchange, 204, "");
        });
        server.start();
        HttpTransportRequest request = new HttpTransportRequest(
                "POST",
                endpoint("/target"),
                Map.of(
                        "Content-Type",
                        "application/vnd.example+json"),
                "{\"specversion\":\"1.0\"}",
                2000,
                Set.of("127.0.0.1"),
                1024,
                false);

        transport.executeLegacy(request, true);

        assertEquals(
                "application/vnd.example+json",
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
