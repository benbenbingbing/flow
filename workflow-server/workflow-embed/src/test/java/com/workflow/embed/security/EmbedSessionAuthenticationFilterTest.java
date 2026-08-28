package com.workflow.embed.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRequestUserContextPort;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Outcome;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Reason;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.application.audit.EmbedRuntimeAudit;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedRuntimeAudit.Operation;
import com.workflow.embed.application.audit.EmbedRuntimeAudit.RequestOperation;
import com.workflow.embed.application.session.EmbedSessionAuthenticationService;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeRequestClass;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class EmbedSessionAuthenticationFilterTest {

    private static final String TOKEN = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);

    @Test
    void establishesAndAlwaysClearsEmbedAndFlowUserContexts() throws Exception {
        EmbedSessionAuthenticationService service = mock(EmbedSessionAuthenticationService.class);
        AuthenticatedEmbedSession authenticated = authenticated();
        when(service.authenticateAuthorization(
                eq("Bearer " + TOKEN), any(EmbedAuditCorrelation.class)))
                .thenReturn(authenticated);
        AtomicBoolean flowContextOpen = new AtomicBoolean();
        EmbedRequestUserContextPort userContext = (userId, username, sessionId) -> {
            flowContextOpen.set(true);
            return () -> flowContextOpen.set(false);
        };
        EmbedTrafficControlPort trafficControl = mock(EmbedTrafficControlPort.class);
        EmbedTrafficControlPort.RuntimeLease lease =
                new EmbedTrafficControlPort.RuntimeLease("lease-1");
        when(trafficControl.acquireRuntime(
                "app-1", "grant-1", "session-1", RuntimeRequestClass.READ))
                .thenReturn(lease);
        EmbedSessionAuthenticationFilter filter = new EmbedSessionAuthenticationFilter(
                service, userContext, trafficControl, new ObjectMapper(),
                mock(EmbedLifecycleMetrics.class),
                mock(EmbedRuntimeAudit.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/embed/v1/runtime/bootstrap");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertEquals(authenticated, EmbedContextHolder.require());
            assertEquals(authenticated, request.getAttribute(
                    EmbedSessionAuthenticationFilter.AUTHENTICATED_SESSION_ATTRIBUTE));
            assertEquals(true, flowContextOpen.get());
            throw new ServletException("downstream failure");
        };

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, chain));

        assertFalse(flowContextOpen.get());
        assertTrueEmptyContext();
        verify(trafficControl).releaseRuntime(lease);
    }

    @Test
    void logoutBypassesBusinessAuthenticationFilterForIdempotentGuard() throws Exception {
        EmbedSessionAuthenticationService service = mock(EmbedSessionAuthenticationService.class);
        EmbedSessionAuthenticationFilter filter = new EmbedSessionAuthenticationFilter(
                service, (id, name, session) -> () -> { },
                mock(EmbedTrafficControlPort.class), new ObjectMapper(),
                mock(EmbedLifecycleMetrics.class),
                mock(EmbedRuntimeAudit.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "DELETE", "/api/embed/v1/session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertEquals(true, called.get());
        assertNull(request.getAttribute(
                EmbedSessionAuthenticationFilter.AUTHENTICATED_SESSION_ATTRIBUTE));
    }

    @Test
    void authenticatedHeartbeatIsQuotaControlledWithStableRetryAfter() throws Exception {
        EmbedSessionAuthenticationService service = mock(EmbedSessionAuthenticationService.class);
        when(service.authenticateAuthorization(
                eq("Bearer " + TOKEN), any(EmbedAuditCorrelation.class)))
                .thenReturn(authenticated());
        EmbedTrafficControlPort trafficControl = mock(EmbedTrafficControlPort.class);
        when(trafficControl.acquireRuntime(
                "app-1", "grant-1", "session-1", RuntimeRequestClass.HEARTBEAT))
                .thenThrow(new EmbedException(
                        429,
                        EmbedErrorCode.RATE_LIMIT_EXCEEDED,
                        "Embed request quota exceeded",
                        17L));
        EmbedLifecycleMetrics metrics = mock(EmbedLifecycleMetrics.class);
        EmbedSessionAuthenticationFilter filter = new EmbedSessionAuthenticationFilter(
                service,
                (id, name, session) -> {
                    throw new AssertionError("user context must not open after quota rejection");
                },
                trafficControl,
                new ObjectMapper(),
                metrics,
                mock(EmbedRuntimeAudit.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/embed/v1/session/heartbeat");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertFalse(called.get());
        assertEquals(429, response.getStatus());
        assertEquals("17", response.getHeader("Retry-After"));
        assertEquals("RATE_LIMIT_EXCEEDED",
                new ObjectMapper().readTree(response.getContentAsByteArray())
                        .path("errorCode").asText());
        verify(metrics).record(Surface.RUNTIME, Outcome.REJECTED, Reason.RATE_LIMIT);
    }

    @Test
    void completedListAndReplayCreateAreReportedWithTrustedResponseOutcome()
            throws Exception {
        EmbedSessionAuthenticationService service = mock(
                EmbedSessionAuthenticationService.class);
        AuthenticatedEmbedSession authenticated = authenticated();
        when(service.authenticateAuthorization(
                eq("Bearer " + TOKEN), any(EmbedAuditCorrelation.class)))
                .thenReturn(authenticated);
        EmbedTrafficControlPort trafficControl = mock(EmbedTrafficControlPort.class);
        when(trafficControl.acquireRuntime(
                "app-1", "grant-1", "session-1", RuntimeRequestClass.READ))
                .thenReturn(new EmbedTrafficControlPort.RuntimeLease("lease-read"));
        when(trafficControl.acquireRuntime(
                "app-1", "grant-1", "session-1", RuntimeRequestClass.WRITE))
                .thenReturn(new EmbedTrafficControlPort.RuntimeLease("lease-write"));
        EmbedRuntimeAudit runtimeAudit = mock(EmbedRuntimeAudit.class);
        EmbedSessionAuthenticationFilter filter = new EmbedSessionAuthenticationFilter(
                service,
                (id, name, session) -> () -> { },
                trafficControl,
                new ObjectMapper(),
                mock(EmbedLifecycleMetrics.class),
                runtimeAudit);

        MockHttpServletRequest list = request(
                "POST", "/api/embed/v1/runtime/list/query", "trace-list");
        MockHttpServletResponse listResponse = new MockHttpServletResponse();
        filter.doFilter(list, listResponse, (req, res) ->
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200));
        verify(runtimeAudit).recordCompletedBestEffort(
                eq(authenticated),
                eq(new RequestOperation(Operation.LIST_QUERY, null)),
                eq("trace-list"),
                eq(200),
                eq(false),
                eq(null),
                anyLong());

        MockHttpServletRequest create = request(
                "POST", "/api/embed/v1/runtime/records", "trace-replay");
        MockHttpServletResponse createResponse = new MockHttpServletResponse();
        filter.doFilter(create, createResponse, (req, res) -> {
            jakarta.servlet.http.HttpServletResponse http =
                    (jakarta.servlet.http.HttpServletResponse) res;
            http.setStatus(201);
            http.setHeader("Idempotent-Replay", "true");
            http.setHeader("Location",
                    "/api/embed/v1/runtime/records/record-1");
        });
        verify(runtimeAudit).recordCompletedBestEffort(
                eq(authenticated),
                eq(new RequestOperation(Operation.RECORD_CREATE, null)),
                eq("trace-replay"),
                eq(201),
                eq(true),
                eq("/api/embed/v1/runtime/records/record-1"),
                anyLong());
        verify(trafficControl).acquireRuntime(
                "app-1", "grant-1", "session-1", RuntimeRequestClass.READ);
        verify(trafficControl).acquireRuntime(
                "app-1", "grant-1", "session-1", RuntimeRequestClass.WRITE);
        verify(service).authenticateAuthorization(
                "Bearer " + TOKEN,
                EmbedAuditCorrelation.of("trace-list", "request-trace-list"));
    }

    @Test
    void authenticatedUnhandledRecordFailureIsAuditedBeforePropagation()
            throws Exception {
        EmbedSessionAuthenticationService service = mock(
                EmbedSessionAuthenticationService.class);
        AuthenticatedEmbedSession authenticated = authenticated();
        when(service.authenticateAuthorization(
                eq("Bearer " + TOKEN), any(EmbedAuditCorrelation.class)))
                .thenReturn(authenticated);
        EmbedTrafficControlPort trafficControl = mock(EmbedTrafficControlPort.class);
        when(trafficControl.acquireRuntime(
                "app-1", "grant-1", "session-1", RuntimeRequestClass.READ))
                .thenReturn(new EmbedTrafficControlPort.RuntimeLease("lease-1"));
        EmbedRuntimeAudit runtimeAudit = mock(EmbedRuntimeAudit.class);
        EmbedSessionAuthenticationFilter filter = new EmbedSessionAuthenticationFilter(
                service,
                (id, name, session) -> () -> { },
                trafficControl,
                new ObjectMapper(),
                mock(EmbedLifecycleMetrics.class),
                runtimeAudit);
        MockHttpServletRequest request = request(
                "GET", "/api/embed/v1/runtime/records/record-1", "trace-detail");

        assertThrows(ServletException.class, () -> filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (req, res) -> {
                    throw new ServletException("downstream failure");
                }));

        verify(runtimeAudit).recordUnhandledFailureBestEffort(
                eq(authenticated),
                eq(new RequestOperation(Operation.RECORD_DETAIL, "record-1")),
                eq("trace-detail"),
                anyLong());
    }

    private static MockHttpServletRequest request(
            String method,
            String path,
            String traceId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        request.addHeader("X-Trace-Id", traceId);
        request.addHeader("X-Request-Id", "request-" + traceId);
        return request;
    }

    private static void assertTrueEmptyContext() {
        assertEquals(java.util.Optional.empty(), EmbedContextHolder.current());
    }

    private static AuthenticatedEmbedSession authenticated() {
        return new AuthenticatedEmbedSession(
                "session-1", "app-1", "grant-1", "view-1", "release-1",
                "user-1", "alice", "https://portal.partner.example",
                "channel-1234567890", "LIST", null, Map.of(), Set.of("LIST_QUERY"),
                Instant.parse("2026-08-27T03:10:00Z"),
                Instant.parse("2026-08-27T04:00:00Z"));
    }
}
