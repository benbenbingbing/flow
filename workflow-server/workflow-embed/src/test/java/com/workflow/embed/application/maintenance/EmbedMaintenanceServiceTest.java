package com.workflow.embed.application.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.embed.application.port.EmbedMaintenancePort;
import com.workflow.embed.application.port.EmbedMaintenancePort.CounterCursor;
import com.workflow.embed.application.port.EmbedMaintenancePort.CounterObservation;
import com.workflow.embed.config.EmbedProperties;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class EmbedMaintenanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");

    @Test
    void scheduledBatchUsesConfiguredBoundsAndEmitsOnlyAggregateCounterAudit() throws Exception {
        FakeMaintenancePort port = new FakeMaintenancePort();
        port.stored = List.of(
                observation("grant-secret-1", "user-secret-1", 3, 1),
                observation("grant-2", "user-2", 2, 2),
                observation("grant-3", "user-3", 1, 2));
        port.active = List.of(
                observation("grant-secret-1", "user-secret-1", 3, 1),
                observation("grant-4", "user-secret-4", null, 2));
        List<SystemAuditEvent> audits = new ArrayList<>();
        EmbedProperties properties = new EmbedProperties();
        properties.setMaintenanceBatchSize(50);
        EmbedMaintenanceService service = new EmbedMaintenanceService(
                port, audits::add, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        EmbedMaintenanceService.BatchResult result = service.maintainBatch();

        assertEquals(50, port.limit);
        assertEquals(NOW, port.now);
        assertEquals(NOW.minusSeconds(3600), port.contextCutoff);
        assertEquals(NOW.minusSeconds(2_592_000), port.receiptCutoff);
        assertEquals(NOW.minusSeconds(7_776_000), port.sessionCutoff);
        assertEquals(NOW.minusSeconds(7_776_000), port.launchCutoff);
        assertEquals(3, result.reconciliation().mismatchCount());
        assertEquals(1, result.reconciliation().counterGreaterCount());
        assertEquals(2, result.reconciliation().sessionGreaterCount());
        assertEquals(1, result.reconciliation().missingCounterCount());
        assertEquals(2, result.reconciliation().maxAbsoluteDelta());
        assertFalse(result.reconciliation().scanContinues());

        assertEquals(1, audits.size());
        SystemAuditEvent audit = audits.get(0);
        assertEquals("EMBED_SESSION_COUNTER_DRIFT", audit.errorCode());
        assertEquals("EMBED_SESSION_COUNTER", audit.targetType());
        assertNull(audit.targetId());
        assertNull(audit.operatorId());
        assertNull(audit.operatorName());
        assertNull(audit.operatorIp());
        assertNull(audit.userAgent());
        assertNull(audit.beforeData());
        String safePayload = String.valueOf(audit.afterData());
        assertFalse(safePayload.contains("grant-secret"));
        assertFalse(safePayload.contains("user-secret"));

        Method method = EmbedMaintenanceService.class.getMethod("maintainBatch");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertEquals("${workflow.embed.maintenance-scan-ms:60000}",
                scheduled.fixedDelayString());
    }

    @Test
    void oneFailedStepDoesNotBlockLaterCleanupAndDoesNotAuditExceptionMessage() {
        FakeMaintenancePort port = new FakeMaintenancePort();
        port.replayFailure = new IllegalStateException("secret ciphertext must not escape");
        List<SystemAuditEvent> audits = new ArrayList<>();
        EmbedMaintenanceService service = new EmbedMaintenanceService(
                port, audits::add, new EmbedProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        EmbedMaintenanceService.BatchResult result = service.maintainBatch();

        assertEquals(0, result.deletedAssertionReplays());
        assertTrue(port.expireLaunchCalled);
        assertTrue(port.receiptCleanupCalled);
        assertTrue(port.sessionCleanupCalled);
        assertTrue(port.launchCleanupCalled);
        assertEquals(1, audits.size());
        SystemAuditEvent audit = audits.get(0);
        assertEquals("EMBED_MAINTENANCE_FAILED", audit.errorCode());
        assertNull(audit.errorMessage());
        assertFalse(String.valueOf(audit.afterData()).contains("ciphertext"));
        assertFalse(String.valueOf(audit.afterData()).contains("secret"));
        assertTrue(String.valueOf(audit.afterData()).contains("IllegalStateException"));
    }

    @Test
    void counterPagesAdvanceWithKeysetCursorAndResetAfterShortPage() {
        FakeMaintenancePort port = new FakeMaintenancePort();
        port.stored = List.of(
                observation("g1", "u1", 0, 0),
                observation("g2", "u2", 0, 0));
        port.active = List.of(
                observation("g1", "u1", 0, 0),
                observation("g2", "u2", 0, 0));
        EmbedProperties properties = new EmbedProperties();
        properties.setMaintenanceBatchSize(2);
        EmbedMaintenanceService service = new EmbedMaintenanceService(
                port, event -> { }, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        service.maintainBatch();
        service.maintainBatch();

        assertEquals(new CounterCursor("g2", "u2"), port.storedCursors.get(1));
        assertEquals(new CounterCursor("g2", "u2"), port.activeCursors.get(1));

        port.stored = List.of();
        port.active = List.of();
        service.maintainBatch();
        service.maintainBatch();
        assertNull(port.storedCursors.get(3));
        assertNull(port.activeCursors.get(3));
    }

    private static CounterObservation observation(
            String grantId,
            String userId,
            Integer stored,
            long actual) {
        return new CounterObservation(grantId, userId, stored, actual);
    }

    private static final class FakeMaintenancePort implements EmbedMaintenancePort {

        private RuntimeException replayFailure;
        private List<CounterObservation> stored = List.of();
        private List<CounterObservation> active = List.of();
        private final List<CounterCursor> storedCursors = new ArrayList<>();
        private final List<CounterCursor> activeCursors = new ArrayList<>();
        private Instant now;
        private Instant contextCutoff;
        private Instant receiptCutoff;
        private Instant sessionCutoff;
        private Instant launchCutoff;
        private int limit;
        private boolean expireLaunchCalled;
        private boolean receiptCleanupCalled;
        private boolean sessionCleanupCalled;
        private boolean launchCleanupCalled;

        @Override
        public int deleteExpiredAssertionReplays(Instant now, int limit) {
            if (replayFailure != null) {
                throw replayFailure;
            }
            this.now = now;
            this.limit = limit;
            return 1;
        }

        @Override
        public int expireIssuedLaunches(Instant now, int limit) {
            expireLaunchCalled = true;
            this.now = now;
            this.limit = limit;
            return 2;
        }

        @Override
        public int eraseTerminalSessionContexts(Instant cutoff, Instant now, int limit) {
            contextCutoff = cutoff;
            return 3;
        }

        @Override
        public List<CounterObservation> inspectStoredCounterPage(CounterCursor after, int limit) {
            storedCursors.add(after);
            return stored;
        }

        @Override
        public List<CounterObservation> inspectActiveSessionPairPage(
                CounterCursor after,
                int limit) {
            activeCursors.add(after);
            return active;
        }

        @Override
        public int deleteOrphanOperationReceipts(Instant cutoff, int limit) {
            receiptCleanupCalled = true;
            receiptCutoff = cutoff;
            return 4;
        }

        @Override
        public int deleteTerminalSessions(Instant cutoff, int limit) {
            sessionCleanupCalled = true;
            sessionCutoff = cutoff;
            return 5;
        }

        @Override
        public int deleteUnreferencedTerminalLaunches(Instant cutoff, int limit) {
            launchCleanupCalled = true;
            launchCutoff = cutoff;
            return 6;
        }
    }
}
