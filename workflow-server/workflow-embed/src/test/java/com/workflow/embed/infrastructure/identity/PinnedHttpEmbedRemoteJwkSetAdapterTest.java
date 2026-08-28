package com.workflow.embed.infrastructure.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.http.HttpTransportRequest;
import com.workflow.http.HttpTransportResult;
import com.workflow.http.PinnedHttpTransport;
import com.workflow.http.RestEndpointPolicy;
import com.workflow.http.WorkflowHttpProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PinnedHttpEmbedRemoteJwkSetAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-08-27T01:00:00Z"));
    private PinnedHttpTransport transport;
    private PinnedHttpEmbedRemoteJwkSetAdapter adapter;

    @BeforeEach
    void setUp() {
        transport = mock(PinnedHttpTransport.class);
        adapter = adapter(transport, 4);
    }

    @Test
    void cachesByProviderSnapshotAndPerformsControlledForceRefresh() throws Exception {
        when(transport.execute(any(HttpTransportRequest.class)))
                .thenReturn(ok(jwks("key-1")), ok(jwks("key-2")), ok(jwks("key-3")),
                        ok(jwks("new-version")));
        EmbedIdentityProviderSnapshot provider = provider(
                "provider-1", 4, 7, "https://keys.partner.example/jwks");

        String first = adapter.load(provider, false);
        String cached = adapter.load(provider, false);
        String refreshed = adapter.load(provider, true);
        String throttledRefresh = adapter.load(provider, true);

        assertEquals(first, cached);
        assertTrue(first.contains("key-1"));
        assertTrue(refreshed.contains("key-2"));
        assertEquals(refreshed, throttledRefresh);
        verify(transport, times(2)).execute(any(HttpTransportRequest.class));

        clock.advance(Duration.ofSeconds(11));
        String refreshedAgain = adapter.load(provider, true);
        assertTrue(refreshedAgain.contains("key-3"));
        String versionIsolated = adapter.load(provider(
                "provider-1", 5, 8, "https://keys.partner.example/jwks"), false);
        assertTrue(versionIsolated.contains("new-version"));

        ArgumentCaptor<HttpTransportRequest> request =
                ArgumentCaptor.forClass(HttpTransportRequest.class);
        verify(transport, times(4)).execute(request.capture());
        HttpTransportRequest outbound = request.getAllValues().get(0);
        assertEquals("GET", outbound.method());
        assertEquals("https://keys.partner.example/jwks", outbound.uri().toString());
        assertEquals(3_000, outbound.timeoutMillis());
        assertEquals(256 * 1024, outbound.maxResponseBytes());
        assertEquals(java.util.Set.of("keys.partner.example"), outbound.allowedHosts());
        assertFalse(outbound.truncateOversizedResponse());
        assertNull(outbound.body());
        assertFalse(outbound.headers().keySet().stream()
                .anyMatch(name -> name.equalsIgnoreCase("Cookie")
                        || name.equalsIgnoreCase("Authorization")));
        assertEquals("no-cache", request.getAllValues().get(1)
                .headers().get("Cache-Control"));
    }

    @Test
    void ttlAndLruBoundPreventUnboundedStaleCache() throws Exception {
        adapter = adapter(transport, 2);
        when(transport.execute(any(HttpTransportRequest.class)))
                .thenReturn(ok(jwks("a")), ok(jwks("b")), ok(jwks("c")),
                        ok(jwks("a-new")), ok(jwks("c-new")));

        adapter.load(provider("p-a", 1, 1, "https://a.example/jwks"), false);
        adapter.load(provider("p-b", 1, 1, "https://b.example/jwks"), false);
        adapter.load(provider("p-c", 1, 1, "https://c.example/jwks"), false);
        String evicted = adapter.load(
                provider("p-a", 1, 1, "https://a.example/jwks"), false);
        assertTrue(evicted.contains("a-new"));

        clock.advance(Duration.ofMinutes(6));
        String expired = adapter.load(
                provider("p-c", 1, 1, "https://c.example/jwks"), false);
        assertTrue(expired.contains("c-new"));
        verify(transport, times(5)).execute(any(HttpTransportRequest.class));
    }

    @Test
    void concurrentColdLoadsUseOneOutboundRequest() throws Exception {
        CountDownLatch fetchEntered = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        when(transport.execute(any(HttpTransportRequest.class))).thenAnswer(invocation -> {
            fetchEntered.countDown();
            if (!releaseFetch.await(5, TimeUnit.SECONDS)) {
                throw new java.io.IOException("test timeout");
            }
            return ok(jwks("single-flight"));
        });
        EmbedIdentityProviderSnapshot provider = provider(
                "provider-1", 1, 1, "https://keys.partner.example/jwks");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return adapter.load(provider, false);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(fetchEntered.await(5, TimeUnit.SECONDS));
            releaseFetch.countDown();
            for (Future<String> future : futures) {
                assertTrue(future.get(5, TimeUnit.SECONDS).contains("single-flight"));
            }
        } finally {
            releaseFetch.countDown();
            executor.shutdownNow();
        }

        verify(transport, times(1)).execute(any(HttpTransportRequest.class));
    }

    @Test
    void rejectsRedirectNon200OversizeBadJsonPrivateJwkAndTooManyKeys() throws Exception {
        when(transport.execute(any(HttpTransportRequest.class)))
                .thenReturn(
                        new HttpTransportResult(302, jwks("redirect"), null, false),
                        new HttpTransportResult(503, jwks("unavailable"), null, false),
                        ok("x".repeat(256 * 1024 + 1)),
                        ok("{not-json"),
                        ok("""
                                {"keys":[{"kty":"RSA","kid":"private",
                                  "use":"sig","n":"abc","e":"AQAB","d":"secret"}]}
                                """),
                        ok(tooManyKeys()));
        for (int attempt = 0; attempt < 6; attempt++) {
            EmbedIdentityProviderSnapshot provider = provider(
                    "provider-1", 1, attempt + 1,
                    "https://keys.partner.example/jwks");
            assertThrows(IllegalStateException.class,
                    () -> adapter.load(provider, false));
        }
        verify(transport, times(6)).execute(any(HttpTransportRequest.class));
    }

    @Test
    void filtersPublicOkpFromMixedSetsButRejectsOkpOnlyOrPrivateOkp() throws Exception {
        String publicOkp = """
                {"kty":"OKP","kid":"okp","use":"sig","crv":"Ed25519",
                 "x":"11qYAYKxCrfVS_7TyWfy3G4EMRdcQHoTSgLZZKhFSqA"}
                """.strip();
        String rsa = """
                {"kty":"RSA","kid":"rsa","use":"sig","n":"abc","e":"AQAB"}
                """.strip();
        String privateOkp = """
                {"kty":"OKP","kid":"private-okp","use":"sig","crv":"Ed25519",
                 "x":"11qYAYKxCrfVS_7TyWfy3G4EMRdcQHoTSgLZZKhFSqA","d":"secret"}
                """.strip();
        when(transport.execute(any(HttpTransportRequest.class)))
                .thenReturn(ok("{\"keys\":[" + publicOkp + "," + rsa + "]}"))
                .thenReturn(ok("{\"keys\":[" + publicOkp + "]}"))
                .thenReturn(ok("{\"keys\":[" + privateOkp + "," + rsa + "]}"));

        String normalized = adapter.load(provider(
                "mixed", 1, 1, "https://keys.partner.example/jwks"), false);
        var keys = objectMapper.readTree(normalized).path("keys");
        assertEquals(1, keys.size());
        assertEquals("RSA", keys.get(0).path("kty").asText());
        assertEquals("rsa", keys.get(0).path("kid").asText());

        assertThrows(IllegalStateException.class, () -> adapter.load(provider(
                "okp-only", 1, 1, "https://keys.partner.example/jwks"), false));
        assertThrows(IllegalStateException.class, () -> adapter.load(provider(
                "private-okp", 1, 1, "https://keys.partner.example/jwks"), false));
        verify(transport, times(3)).execute(any(HttpTransportRequest.class));
    }

    @Test
    void failedForceRefreshUsesCooldownAndKeepsPreviousKeys() throws Exception {
        when(transport.execute(any(HttpTransportRequest.class)))
                .thenReturn(ok(jwks("known")))
                .thenReturn(new HttpTransportResult(503, "unavailable", null, false))
                .thenReturn(ok(jwks("rotated")));
        EmbedIdentityProviderSnapshot provider = provider(
                "provider-1", 1, 1, "https://keys.partner.example/jwks");

        assertTrue(adapter.load(provider, false).contains("known"));
        assertThrows(IllegalStateException.class, () -> adapter.load(provider, true));
        // 失败后的连续 unknown kid 强刷复用旧公钥，不再形成串行出站风暴。
        assertTrue(adapter.load(provider, true).contains("known"));
        assertTrue(adapter.load(provider, true).contains("known"));
        verify(transport, times(2)).execute(any(HttpTransportRequest.class));

        clock.advance(Duration.ofSeconds(11));
        assertTrue(adapter.load(provider, true).contains("rotated"));
        verify(transport, times(3)).execute(any(HttpTransportRequest.class));
    }

    @Test
    void rejectsNonHttpsQueryAndPrivateDnsDestinations() {
        PinnedHttpTransport unused = mock(PinnedHttpTransport.class);
        PinnedHttpEmbedRemoteJwkSetAdapter localAdapter = adapter(unused, 4);
        assertThrows(IllegalArgumentException.class, () -> localAdapter.load(
                provider("p", 1, 1, "http://keys.partner.example/jwks"), false));
        assertThrows(IllegalArgumentException.class, () -> localAdapter.load(
                provider("p", 1, 1, "https://keys.partner.example/jwks?token=secret"),
                false));
        verifyNoInteractions(unused);

        WorkflowHttpProperties properties = new WorkflowHttpProperties();
        properties.setMaxResponseBytes(256 * 1024);
        PinnedHttpTransport realTransport = new PinnedHttpTransport(
                new RestEndpointPolicy(properties), properties);
        PinnedHttpEmbedRemoteJwkSetAdapter realAdapter = adapter(realTransport, 4);

        assertThrows(IllegalStateException.class, () -> realAdapter.load(
                provider("private", 1, 1, "https://127.0.0.1/jwks"), false));
    }

    private PinnedHttpEmbedRemoteJwkSetAdapter adapter(
            PinnedHttpTransport value,
            int maxEntries) {
        return new PinnedHttpEmbedRemoteJwkSetAdapter(
                value, objectMapper, clock, Duration.ofMinutes(5),
                Duration.ofSeconds(10), maxEntries);
    }

    private static HttpTransportResult ok(String body) {
        return new HttpTransportResult(200, body, null, false);
    }

    private static String jwks(String kid) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid
                + "\",\"use\":\"sig\",\"n\":\"abc\",\"e\":\"AQAB\"}]}";
    }

    private static String tooManyKeys() {
        String keys = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> "{\"kty\":\"RSA\",\"kid\":\"k" + index
                        + "\",\"use\":\"sig\",\"n\":\"abc\",\"e\":\"AQAB\"}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"keys\":[" + keys + "]}";
    }

    private static EmbedIdentityProviderSnapshot provider(
            String id,
            long securityVersion,
            long keyVersion,
            String url) {
        return new EmbedIdentityProviderSnapshot(
                id, "SIGNED_JWT", "ACTIVE", "https://issuer.partner.example",
                "partner", "[\"flow-embed-launch\"]", "[\"RS256\"]",
                "REMOTE_JWKS", null, url, 30, 60, keyVersion, securityVersion);
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            current.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
