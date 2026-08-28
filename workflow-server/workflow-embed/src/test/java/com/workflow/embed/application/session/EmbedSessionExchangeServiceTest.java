package com.workflow.embed.application.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.port.EmbedIdGeneratorPort;
import com.workflow.embed.application.port.EmbedLaunchExchangeLookupPort;
import com.workflow.embed.application.port.EmbedSecretGeneratorPort;
import com.workflow.embed.application.port.EmbedSessionExchangeTransactionPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.EmbedApplicationSnapshot;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedExternalIdentityBinding;
import com.workflow.embed.domain.EmbedFlowUser;
import com.workflow.embed.domain.EmbedGrantSnapshot;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.EmbedLaunchExchangeCandidate;
import com.workflow.embed.domain.EmbedSessionExchangePlan;
import com.workflow.embed.domain.EmbedSessionIssued;
import com.workflow.embed.domain.EmbedViewSnapshot;
import com.workflow.embed.domain.PersistedEmbedLaunch;
import com.workflow.embed.domain.ProtectedContext;
import com.workflow.embed.infrastructure.crypto.AesGcmEmbedContextProtection;
import com.workflow.embed.infrastructure.crypto.Sha256EmbedDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EmbedSessionExchangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T02:00:00Z");
    private static final String LAUNCH_CODE = encoded((byte) 1);
    private static final String PARENT_NONCE = encoded((byte) 2);
    private static final String CHILD_NONCE = encoded((byte) 3);
    private static final EmbedAuditCorrelation CORRELATION =
            EmbedAuditCorrelation.of("trace-exchange", "request-exchange");

    @Test
    void exchangesOnceAndReencryptsContextIntoBoundedSession() throws Exception {
        Fixture fixture = new Fixture(candidate(NOW.plusSeconds(60), 2));
        EmbedSessionExchangeCommand command = command(LAUNCH_CODE);

        EmbedSessionIssued issued = fixture.service.exchange(command, CORRELATION);

        EmbedSessionExchangePlan plan = fixture.transaction.plans.get(0);
        assertEquals("ems_1", issued.sessionId());
        assertEquals(NOW.plusSeconds(1200), issued.expiresAt());
        assertEquals(NOW.plusSeconds(300), issued.idleExpiresAt());
        assertEquals(Set.of("LIST_QUERY", "RECORD_VIEW"),
                fixture.mapper.readValue(plan.capabilitySnapshotJson(), Set.class));
        assertNotEquals(
                plan.candidate().launch().context().ciphertext(),
                plan.sessionContext().ciphertext());
        assertEquals(Map.of("supplierId", "S-1"), fixture.protection.unprotectSession(
                "app-1", plan.sessionId(), plan.sessionContext().ciphertext(),
                plan.sessionContext().cipherKeyVersion()));
        assertEquals(new Sha256EmbedDigest().sha256(issued.accessToken()),
                plan.sessionTokenDigest());
        assertFalse(command.toString().contains(LAUNCH_CODE));
        assertFalse(command.toString().contains(PARENT_NONCE));
        assertFalse(issued.toString().contains(issued.accessToken()));
        verify(fixture.trafficControl).consumeExchange("launch-1", "127.0.0.1");
        verify(fixture.audit).exchangeSucceeded("ems_1", CORRELATION);
    }

    @Test
    void stripsUnsupportedMutationAndProcessCapabilitiesAtExchangeBoundary() throws Exception {
        Fixture fixture = new Fixture(candidate(
                NOW.plusSeconds(60), 2, "LIST",
                "[\"LIST_QUERY\",\"RECORD_VIEW\",\"RECORD_UPDATE\","
                        + "\"ACTION_EXECUTE\",\"PROCESS_START\"]",
                "[\"LIST_QUERY\",\"RECORD_VIEW\",\"RECORD_UPDATE\","
                        + "\"ACTION_EXECUTE\",\"PROCESS_START\"]"));

        fixture.service.exchange(command(LAUNCH_CODE), CORRELATION);

        EmbedSessionExchangePlan plan = fixture.transaction.plans.get(0);
        assertEquals(Set.of("LIST_QUERY", "RECORD_VIEW"),
                fixture.mapper.readValue(plan.capabilitySnapshotJson(), Set.class));
    }

    @Test
    void rejectsLegacyEditLaunchBeforeCreatingSession() {
        Fixture fixture = new Fixture(candidate(
                NOW.plusSeconds(60), 2, "EDIT",
                "[\"RECORD_VIEW\",\"RECORD_UPDATE\"]",
                "[\"RECORD_VIEW\",\"RECORD_UPDATE\"]"));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.exchange(command(LAUNCH_CODE), CORRELATION));

        assertEquals(EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED, error.getErrorCode());
        assertFalse(fixture.transaction.consumed);
    }

    @Test
    void unknownOrMismatchedCodeUsesUniformInvalidError() {
        Fixture fixture = new Fixture(candidate(NOW.plusSeconds(60), 2));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.exchange(
                        command(encoded((byte) 99)), CORRELATION));

        assertEquals(EmbedErrorCode.EMBED_LAUNCH_INVALID, error.getErrorCode());
        assertEquals(401, error.getStatus());
        verify(fixture.audit).exchangeRejected(
                EmbedErrorCode.EMBED_LAUNCH_INVALID, CORRELATION);
    }

    @Test
    void correctHighEntropyCodeCanReportConfirmedExpiry() {
        Fixture fixture = new Fixture(candidate(NOW.minusNanos(1), 2));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.exchange(command(LAUNCH_CODE), CORRELATION));

        assertEquals(EmbedErrorCode.EMBED_LAUNCH_EXPIRED, error.getErrorCode());
        assertEquals(410, error.getStatus());
    }

    @Test
    void concurrentExchangeOfSameLaunchSucceedsExactlyOnce() throws Exception {
        Fixture fixture = new Fixture(candidate(NOW.plusSeconds(60), 2));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> exchange = () -> {
            ready.countDown();
            start.await();
            try {
                fixture.service.exchange(command(LAUNCH_CODE), CORRELATION);
                return true;
            } catch (EmbedException error) {
                assertEquals(EmbedErrorCode.EMBED_LAUNCH_INVALID, error.getErrorCode());
                return false;
            }
        };
        Future<Boolean> first = executor.submit(exchange);
        Future<Boolean> second = executor.submit(exchange);
        ready.await();
        start.countDown();

        int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
        executor.shutdownNow();

        assertEquals(1, successes);
        assertEquals(1, fixture.transaction.activeCount);
        assertEquals(1, fixture.transaction.plans.size());
    }

    @Test
    void sessionLimitDoesNotConsumeLaunchAndCanBeRetriedAfterSlotRelease() {
        Fixture fixture = new Fixture(candidate(NOW.plusSeconds(60), 1));
        fixture.transaction.activeCount = 1;

        EmbedException limited = assertThrows(
                EmbedException.class,
                () -> fixture.service.exchange(command(LAUNCH_CODE), CORRELATION));
        assertEquals(EmbedErrorCode.EMBED_SESSION_LIMIT_EXCEEDED, limited.getErrorCode());
        assertFalse(fixture.transaction.consumed);

        fixture.transaction.activeCount = 0;
        fixture.service.exchange(command(LAUNCH_CODE), CORRELATION);
        assertTrue(fixture.transaction.consumed);
    }

    private static EmbedSessionExchangeCommand command(String code) {
        return new EmbedSessionExchangeCommand(
                "launch-1", code, "channel-1234567890",
                "https://PORTAL.PARTNER.EXAMPLE:443/",
                PARENT_NONCE, CHILD_NONCE, "1.0.0", "127.0.0.1");
    }

    private static EmbedLaunchExchangeCandidate candidate(Instant expiry, int maxActive) {
        return candidate(
                expiry, maxActive, "LIST",
                "[\"LIST_QUERY\",\"RECORD_VIEW\",\"RECORD_DELETE\"]",
                "[\"LIST_QUERY\",\"RECORD_VIEW\",\"RECORD_DELETE\"]");
    }

    private static EmbedLaunchExchangeCandidate candidate(
            Instant expiry,
            int maxActive,
            String entryMode,
            String releaseCapabilities,
            String grantCapabilities) {
        ObjectMapper mapper = new ObjectMapper();
        AesGcmEmbedContextProtection protection = protection(mapper);
        ProtectedContext launchContext = protection.protectLaunch(
                "app-1", "launch-1", Map.of("supplierId", "S-1"));
        PersistedEmbedLaunch launch = new PersistedEmbedLaunch(
                "launch-1", "app-1", "grant-1", "view-1", "release-1",
                "provider-1", 4, 3, 5, 6, "user-1", "binding-1", 7,
                "a".repeat(64), "subject-v1", "https://portal.partner.example",
                "channel-1234567890", entryMode, null, launchContext, "zh-CN", "light",
                new Sha256EmbedDigest().sha256(LAUNCH_CODE), expiry, "trace-1", "request-1", NOW);
        EmbedIdentityProviderSnapshot provider = new EmbedIdentityProviderSnapshot(
                "provider-1", "TRUSTED_EXTERNAL_ID", "ACTIVE", null, "erp-prod",
                "[]", "[]", null, null, null, 30, 60, 1, 4);
        EmbedGrantSnapshot grant = new EmbedGrantSnapshot(
                "grant-1", "app-1", "view-1", "ACTIVE", true,
                grantCapabilities,
                maxActive, 1200, 60, 600, 10, null, 5, provider, Set.of());
        return new EmbedLaunchExchangeCandidate(
                launch, "ISSUED", null, null, maxActive, 1200,
                releaseCapabilities, grant.capabilityCeilingJson(),
                new EmbedApplicationSnapshot("app-1", "ACTIVE", null, 3),
                new EmbedViewSnapshot("view-1", "view-key", "LIST", "ACTIVE", "release-1", 6),
                grant,
                new EmbedExternalIdentityBinding(
                        "binding-1", "app-1", "provider-1", "a".repeat(64),
                        "subject-v1", "user-1", "ACTIVE", 7, NOW.minusSeconds(1), null),
                new EmbedFlowUser("user-1", "alice", true, false, false));
    }

    private static AesGcmEmbedContextProtection protection(ObjectMapper mapper) {
        byte[] aes = new byte[32];
        byte[] hmac = new byte[32];
        java.util.Arrays.fill(aes, (byte) 11);
        java.util.Arrays.fill(hmac, (byte) 12);
        return new AesGcmEmbedContextProtection(
                aes, "context-v1", hmac, "hmac-v1", mapper, new SecureRandom());
    }

    private static String encoded(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class Fixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final AesGcmEmbedContextProtection protection = protection(mapper);
        private final AtomicInteger sequence = new AtomicInteger();
        private final FakeTransaction transaction;
        private final EmbedLifecycleAudit audit = mock(EmbedLifecycleAudit.class);
        private final EmbedTrafficControlPort trafficControl =
                mock(EmbedTrafficControlPort.class);
        private final EmbedSessionExchangeService service;

        private Fixture(EmbedLaunchExchangeCandidate candidate) {
            EmbedLaunchExchangeLookupPort lookup = digest ->
                    candidate.launch().launchCodeDigest().equals(digest)
                            ? Optional.of(candidate)
                            : Optional.empty();
            transaction = new FakeTransaction(candidate.maxActiveSessionsPerUser());
            EmbedSecretGeneratorPort secrets = bytes -> encoded(
                    (byte) (20 + sequence.incrementAndGet()));
            EmbedIdGeneratorPort ids = new EmbedIdGeneratorPort() {
                @Override
                public String nextLaunchId() {
                    return "unused";
                }

                @Override
                public String nextSessionId() {
                    return "ems_" + sequence.incrementAndGet();
                }
            };
            EmbedProperties properties = new EmbedProperties();
            properties.setSessionIdleSeconds(300);
            properties.setSessionAbsoluteSeconds(1800);
            properties.setSecretBytes(32);
            service = new EmbedSessionExchangeService(
                    lookup, transaction, protection, secrets, new Sha256EmbedDigest(), ids,
                    properties, mapper, Clock.fixed(NOW, ZoneOffset.UTC),
                    audit, trafficControl);
        }
    }

    private static final class FakeTransaction implements EmbedSessionExchangeTransactionPort {
        private final int limit;
        private final List<EmbedSessionExchangePlan> plans = new ArrayList<>();
        private boolean consumed;
        private int activeCount;

        private FakeTransaction(int limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void exchange(EmbedSessionExchangePlan plan) {
            // Mirrors the database lock order: quota is checked before the Launch is consumed.
            if (activeCount >= limit) {
                throw new EmbedException(429, EmbedErrorCode.EMBED_SESSION_LIMIT_EXCEEDED,
                        "limit");
            }
            if (consumed) {
                throw new EmbedException(401, EmbedErrorCode.EMBED_LAUNCH_INVALID, "invalid");
            }
            activeCount++;
            consumed = true;
            plans.add(plan);
        }
    }
}
