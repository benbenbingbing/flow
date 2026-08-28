package com.workflow.embed.application.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.port.EmbedSessionPersistencePort;
import com.workflow.embed.domain.EmbedSessionSecuritySnapshot;
import com.workflow.embed.domain.EmbedSessionTermination;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbedSessionExpiryReaperTest {

    private static final Instant NOW = Instant.parse("2026-08-27T05:00:00Z");

    @Test
    void expiresBatchThroughSharedIdempotentTerminationPrimitive() {
        FakePersistence persistence = new FakePersistence();
        EmbedSessionTerminationService terminationService =
                new EmbedSessionTerminationService(
                        persistence, mock(EmbedLifecycleAudit.class));
        EmbedSessionExpiryReaper reaper = new EmbedSessionExpiryReaper(
                persistence, terminationService, Clock.fixed(NOW, ZoneOffset.UTC));

        int expired = reaper.expireBatch();

        assertEquals(1, expired);
        assertEquals(List.of("digest-1", "digest-2", "digest-3"), persistence.terminated);
        assertEquals(NOW, persistence.scannedAt);
        assertEquals(200, persistence.limit);
    }

    private static final class FakePersistence implements EmbedSessionPersistencePort {

        private final List<String> terminated = new ArrayList<>();
        private Instant scannedAt;
        private int limit;

        @Override
        public Optional<EmbedSessionSecuritySnapshot> findByTokenDigest(String tokenDigest) {
            return Optional.empty();
        }

        @Override
        public boolean touchLastSeen(String sessionId, Instant expectedLastSeen, Instant now) {
            return false;
        }

        @Override
        public Optional<EmbedSessionSecuritySnapshot> heartbeat(
                String sessionId, Instant now, Instant requestedIdleExpiry) {
            return Optional.empty();
        }

        @Override
        public List<String> findExpiredTokenDigests(Instant now, int limit) {
            this.scannedAt = now;
            this.limit = limit;
            return List.of("digest-1", "digest-2", "digest-3");
        }

        @Override
        public EmbedSessionTermination terminate(
                String tokenDigest, String terminalStatus, String reason, Instant now) {
            terminated.add(tokenDigest);
            assertEquals("EXPIRED", terminalStatus);
            assertEquals(null, reason);
            assertEquals(NOW, now);
            return Map.of(
                    "digest-1", EmbedSessionTermination.TERMINATED,
                    "digest-2", EmbedSessionTermination.EXPIRED,
                    "digest-3", EmbedSessionTermination.INVALID)
                    .get(tokenDigest);
        }
    }
}
