package com.workflow.embed.application.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.embed.application.audit.EmbedRuntimeAudit.Operation;
import com.workflow.embed.application.audit.EmbedRuntimeAudit.RequestOperation;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmbedRuntimeAuditTest {

    private static final Instant NOW = Instant.parse("2026-08-27T09:00:00Z");
    private static final Set<String> COORDINATE_KEYS = Set.of(
            "applicationId",
            "viewId",
            "viewReleaseId",
            "sessionId",
            "flowUserId",
            "operation",
            "outcome",
            "httpStatus");

    @Test
    void requiredCreateUsesClosedCoordinatesAndPropagatesAuditFailure() {
        List<SystemAuditEvent> events = new ArrayList<>();
        EmbedRuntimeAudit audit = audit(events::add);

        audit.recordCreatedRequired(
                authenticated(), "record-1", "trace-create", 37L);

        assertEquals(1, events.size());
        SystemAuditEvent event = events.get(0);
        assertEquals("EMBED_RECORD_CREATE", event.operationName());
        assertEquals("trace-create", event.traceId());
        assertEquals("user-1", event.operatorId());
        assertEquals("POST", event.requestMethod());
        assertEquals("/api/embed/v1/runtime/records", event.requestPath());
        assertEquals("RECORD", event.targetType());
        assertEquals("record-1", event.targetId());
        assertEquals(AuditResult.SUCCESS, event.result());
        assertTrue(event.required());
        assertEquals(37L, event.durationMs());
        assertEquals(NOW.atOffset(ZoneOffset.UTC).toLocalDateTime(), event.createdAt());
        assertClosedPayload(event, "CREATED", 201);

        EmbedRuntimeAudit failing = audit(ignored -> {
            throw new IllegalStateException("outbox unavailable");
        });
        assertThrows(IllegalStateException.class,
                () -> failing.recordCreatedRequired(
                        authenticated(), "record-1", "trace-create", 1L));
    }

    @Test
    void readReplayAndFailureAreBestEffortAndKeepStableOperationCodes() {
        List<SystemAuditEvent> events = new ArrayList<>();
        EmbedRuntimeAudit audit = audit(events::add);

        audit.recordCompletedBestEffort(
                authenticated(),
                new RequestOperation(Operation.LIST_QUERY, null),
                "trace-list", 200, false, null, 4L);
        audit.recordCompletedBestEffort(
                authenticated(),
                new RequestOperation(Operation.RECORD_DETAIL, "record-404"),
                "trace-detail", 404, false, null, 5L);
        audit.recordCompletedBestEffort(
                authenticated(),
                new RequestOperation(Operation.RECORD_CREATE, null),
                "trace-replay", 201, true,
                "/api/embed/v1/runtime/records/record-1", 6L);
        // 首次 201 已由业务事务写 required 审计，HTTP 完成阶段不得重复写。
        audit.recordCompletedBestEffort(
                authenticated(),
                new RequestOperation(Operation.RECORD_CREATE, null),
                "trace-create", 201, false,
                "/api/embed/v1/runtime/records/record-2", 7L);

        assertEquals(3, events.size());
        assertEquals("EMBED_RUNTIME_LIST_QUERY", events.get(0).operationName());
        assertEquals("EMBED_RUNTIME_RECORD_DETAIL", events.get(1).operationName());
        assertEquals(AuditResult.FAILURE, events.get(1).result());
        assertEquals("HTTP_404", events.get(1).errorCode());
        assertEquals("record-404", events.get(1).targetId());
        assertEquals("EMBED_RECORD_CREATE_REPLAY", events.get(2).operationName());
        assertEquals("record-1", events.get(2).targetId());
        assertFalse(events.get(0).required());
        assertFalse(events.get(1).required());
        assertFalse(events.get(2).required());
        assertClosedPayload(events.get(0), "SUCCESS", 200);
        assertClosedPayload(events.get(1), "FAILURE", 404);
        assertClosedPayload(events.get(2), "IDEMPOTENT_REPLAY", 201);

        EmbedRuntimeAudit unavailable = audit(ignored -> {
            throw new IllegalStateException("best effort unavailable");
        });
        assertDoesNotThrow(() -> unavailable.recordCompletedBestEffort(
                authenticated(),
                new RequestOperation(Operation.LIST_QUERY, null),
                "trace-list", 200, false, null, 1L));
        assertDoesNotThrow(() -> unavailable.recordUnhandledFailureBestEffort(
                authenticated(),
                new RequestOperation(Operation.RECORD_DETAIL, "record-1"),
                "trace-detail", 1L));
    }

    @Test
    void classifierAcceptsOnlyExactV1RoutesAndSafeRecordIds() {
        assertEquals(Operation.LIST_QUERY,
                EmbedRuntimeAudit.classify(
                                "POST", "/api/embed/v1/runtime/list/query")
                        .orElseThrow().operation());
        assertEquals(Operation.RECORD_CREATE,
                EmbedRuntimeAudit.classify(
                                "POST", "/api/embed/v1/runtime/records")
                        .orElseThrow().operation());
        RequestOperation detail = EmbedRuntimeAudit.classify(
                        "GET", "/api/embed/v1/runtime/records/record-1")
                .orElseThrow();
        assertEquals(Operation.RECORD_DETAIL, detail.operation());
        assertEquals("record-1", detail.recordId());

        assertTrue(EmbedRuntimeAudit.classify(
                "GET", "/api/embed/v1/runtime/records/record/child").isEmpty());
        assertTrue(EmbedRuntimeAudit.classify(
                "PATCH", "/api/embed/v1/runtime/records/record-1").isEmpty());
        assertTrue(EmbedRuntimeAudit.classify(
                "POST", "/api/embed/v1/runtime/list/query/extra").isEmpty());
    }

    private static void assertClosedPayload(
            SystemAuditEvent event,
            String outcome,
            int status) {
        assertNull(event.beforeData());
        assertNull(event.changedFields());
        assertNull(event.operatorIp());
        assertNull(event.userAgent());
        assertNull(event.errorMessage());
        assertTrue(event.afterData() instanceof Map<?, ?>);
        Map<?, ?> data = (Map<?, ?>) event.afterData();
        assertEquals(COORDINATE_KEYS, data.keySet());
        assertEquals("app-1", data.get("applicationId"));
        assertEquals("view-1", data.get("viewId"));
        assertEquals("release-1", data.get("viewReleaseId"));
        assertEquals("session-1", data.get("sessionId"));
        assertEquals("user-1", data.get("flowUserId"));
        assertEquals(event.operationName(), data.get("operation"));
        assertEquals(outcome, data.get("outcome"));
        assertEquals(status, data.get("httpStatus"));

        String rendered = event.toString();
        assertFalse(rendered.contains("token-must-never-appear"));
        assertFalse(rendered.contains("launch-code-must-never-appear"));
        assertFalse(rendered.contains("jwt-must-never-appear"));
        assertFalse(rendered.contains("subject-must-never-appear"));
        assertFalse(rendered.contains("context-secret-must-never-appear"));
        assertFalse(rendered.contains("https://portal.partner.example"));
        assertFalse(rendered.contains("PartnerBrowser/1.0"));
    }

    private static EmbedRuntimeAudit audit(SystemAuditPort port) {
        return new EmbedRuntimeAudit(
                port, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AuthenticatedEmbedSession authenticated() {
        return new AuthenticatedEmbedSession(
                "session-1",
                "app-1",
                "grant-1",
                "provider-1",
                "binding-subject-must-never-appear",
                "view-1",
                "release-1",
                "user-1",
                "alice",
                "https://portal.partner.example",
                "channel-1234567890",
                "LIST",
                null,
                Map.of(
                        "secret", "context-secret-must-never-appear",
                        "assertion", "jwt-must-never-appear",
                        "code", "launch-code-must-never-appear",
                        "token", "token-must-never-appear"),
                Set.of("LIST_QUERY", "RECORD_VIEW", "RECORD_CREATE"),
                NOW.plusSeconds(900),
                NOW.plusSeconds(3600));
    }
}
