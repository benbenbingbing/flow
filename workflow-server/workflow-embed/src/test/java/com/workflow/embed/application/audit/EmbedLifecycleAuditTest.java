package com.workflow.embed.application.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.embed.application.audit.EmbedLifecycleAudit.Operator;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class EmbedLifecycleAuditTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");

    @Test
    void auditPayloadUsesClosedWhitelistAndNeverCarriesBoundarySecrets() throws Exception {
        List<SystemAuditEvent> events = new ArrayList<>();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EmbedLifecycleAudit audit = audit(events::add, registry);

        EmbedAuditCorrelation correlation = EmbedAuditCorrelation.of(
                "trace-lifecycle", "request-lifecycle");
        audit.launchIssued("lch_0123456789abcdef", correlation);
        audit.exchangeRejected(EmbedErrorCode.EMBED_LAUNCH_INVALID, correlation);
        audit.sessionTerminated(new EmbedSessionTerminationResult(
                        EmbedSessionTermination.TERMINATED, true,
                        "ems_0123456789abcdef", "app-1", "view-1", "flow-user-1", "REVOKED"),
                Surface.ADMIN_REVOKE, new Operator("admin-1", "alice"), correlation);

        assertEquals(3, events.size());
        for (SystemAuditEvent event : events) {
            assertNull(event.beforeData());
            assertNull(event.operatorIp());
            assertNull(event.userAgent());
            assertNull(event.errorMessage());
            assertEquals(java.util.Set.of("surface", "outcome", "reason", "requestId"),
                    ((Map<?, ?>) event.afterData()).keySet());
            assertEquals("trace-lifecycle", event.traceId());
            assertEquals("request-lifecycle",
                    ((Map<?, ?>) event.afterData()).get("requestId"));
            assertNull(event.operationId());
            String rendered = event.toString().toLowerCase(java.util.Locale.ROOT);
            assertFalse(rendered.contains("launchcode"));
            assertFalse(rendered.contains("accesstoken"));
            assertFalse(rendered.contains("subject"));
            assertFalse(rendered.contains("context"));
            assertFalse(rendered.contains("jwt"));
        }
        assertEquals(AuditResult.FAILURE, events.get(1).result());
        assertFalse(events.get(1).required());
        assertTrue(events.get(0).required());
        assertTrue(events.get(2).required());
        assertEquals("lch_0123456789abcdef", events.get(0).targetId());
        assertNull(events.get(1).targetId());
        assertEquals("ems_0123456789abcdef", events.get(2).targetId());

        // 指标标签只来自封闭枚举，不能出现 Launch/Session/Application 等业务 ID。
        registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag -> {
            assertFalse(tag.getValue().contains("0123456789abcdef"));
            assertFalse(tag.getValue().contains("app-1"));
        }));
        assertEquals(3, registry.getMeters().size());
    }

    @Test
    void traceOnlyCorrelationDoesNotInventRequestOrResourceIds() {
        List<SystemAuditEvent> events = new ArrayList<>();
        EmbedLifecycleAudit audit = audit(events::add, new SimpleMeterRegistry());

        audit.authenticationRejected(
                EmbedErrorCode.EMBED_SESSION_INVALID,
                EmbedAuditCorrelation.of("trace-only", null));

        SystemAuditEvent event = events.get(0);
        assertEquals("trace-only", event.traceId());
        assertFalse(((Map<?, ?>) event.afterData()).containsKey("requestId"));
        assertNull(event.operationId());
        assertNull(event.targetId());
    }

    @Test
    void everyRequestBoundLifecycleSurfaceKeepsCorrelationSeparateFromTarget() {
        List<SystemAuditEvent> events = new ArrayList<>();
        EmbedLifecycleAudit audit = audit(events::add, new SimpleMeterRegistry());
        EmbedAuditCorrelation correlation = EmbedAuditCorrelation.of(
                "trace-all", "request-all");

        audit.launchRejected(EmbedErrorCode.EMBED_VIEW_NOT_GRANTED, correlation);
        audit.exchangeSucceeded("session-exchange", correlation);
        audit.authenticationRejected(EmbedErrorCode.EMBED_SESSION_INVALID, correlation);
        audit.sessionTerminated(terminated("session-logout", "LOGGED_OUT"),
                Surface.LOGOUT, null, correlation);
        audit.sessionTerminated(terminated("session-expired", "EXPIRED"),
                Surface.EXPIRY, null, correlation);
        audit.sessionTerminated(terminated("session-revoked", "REVOKED"),
                Surface.ADMIN_REVOKE, new Operator("admin-1", "alice"), correlation);
        audit.launchRevoked(
                "launch-revoked", new Operator("admin-1", "alice"), correlation);

        assertEquals(7, events.size());
        for (SystemAuditEvent event : events) {
            assertEquals("trace-all", event.traceId());
            assertEquals("request-all",
                    ((Map<?, ?>) event.afterData()).get("requestId"));
            assertNull(event.operationId());
        }
        assertNull(events.get(0).targetId());
        assertEquals("session-exchange", events.get(1).targetId());
        assertNull(events.get(2).targetId());
        assertEquals("session-logout", events.get(3).targetId());
        assertEquals("session-expired", events.get(4).targetId());
        assertEquals("session-revoked", events.get(5).targetId());
        assertEquals("launch-revoked", events.get(6).targetId());
    }

    @Test
    void requiredAuditFailurePropagatesToTransactionalCaller() {
        EmbedLifecycleAudit audit = audit(event -> {
            throw new IllegalStateException("outbox unavailable");
        }, new SimpleMeterRegistry());

        assertThrows(IllegalStateException.class,
                () -> audit.launchIssued(
                        "lch_0123456789abcdef",
                        EmbedAuditCorrelation.of("trace-required", "request-required")));
    }

    private static EmbedLifecycleAudit audit(
            SystemAuditPort port, MeterRegistry registry) {
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return new EmbedLifecycleAudit(
                port, new EmbedLifecycleMetrics(provider), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EmbedSessionTerminationResult terminated(
            String sessionId,
            String status) {
        return new EmbedSessionTerminationResult(
                EmbedSessionTermination.TERMINATED,
                true,
                sessionId,
                "app-1",
                "view-1",
                "flow-user-1",
                status);
    }
}
