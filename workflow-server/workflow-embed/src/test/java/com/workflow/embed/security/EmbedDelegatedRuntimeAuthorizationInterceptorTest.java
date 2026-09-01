package com.workflow.embed.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import com.workflow.contracts.embed.EmbedDelegatedRuntimeApi;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class EmbedDelegatedRuntimeAuthorizationInterceptorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passesCompleteEndpointDeclarationToPolicy() throws Exception {
        EmbedDelegatedRuntimePolicy policy = mock(
                EmbedDelegatedRuntimePolicy.class);
        EmbedDelegatedRuntimeAuthorizationInterceptor interceptor =
                new EmbedDelegatedRuntimeAuthorizationInterceptor(
                        policy, objectMapper);
        AuthenticatedEmbedSession session = session();
        EmbedNativeFormTarget target = target();
        MockHttpServletRequest request = delegatedRequest(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handler("futureReference");
        EmbedDelegatedRuntimeApi declaration = FutureReferenceController.class
                .getDeclaredMethod("futureReference")
                .getAnnotation(EmbedDelegatedRuntimeApi.class);
        when(policy.authorize(request, session, null, declaration))
                .thenReturn(target);

        assertTrue(interceptor.preHandle(request, response, handler));
        verify(policy).authorize(request, session, null, declaration);
        assertEquals(
                new EmbedDelegatedRequestContext.AuthorizedTarget(
                        "order", "form-1", "release-1", 3,
                        "VIEW", "record-1"),
                request.getAttribute(
                        EmbedDelegatedRequestContext.TARGET_ATTRIBUTE));
    }

    @Test
    void unannotatedNativeFlowHandlerFallsThroughToPlatformAuthorization()
            throws Exception {
        EmbedDelegatedRuntimePolicy policy = mock(
                EmbedDelegatedRuntimePolicy.class);
        AuthenticatedEmbedSession session = session();
        MockHttpServletRequest request = delegatedRequest(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        EmbedDelegatedRuntimeAuthorizationInterceptor interceptor =
                new EmbedDelegatedRuntimeAuthorizationInterceptor(
                        policy, objectMapper);

        assertTrue(interceptor.preHandle(
                request, response, handler("privateEndpoint")));
        assertEquals(200, response.getStatus());
        verifyNoInteractions(policy);
    }

    @Test
    void listTargetWithoutDefaultFormDoesNotUnboxNullableReleaseVersion()
            throws Exception {
        EmbedDelegatedRuntimePolicy policy = mock(
                EmbedDelegatedRuntimePolicy.class);
        EmbedDelegatedRuntimeAuthorizationInterceptor interceptor =
                new EmbedDelegatedRuntimeAuthorizationInterceptor(
                        policy, objectMapper);
        AuthenticatedEmbedSession session = session();
        MockHttpServletRequest request = delegatedRequest(session);
        HandlerMethod handler = handler("futureReference");
        EmbedDelegatedRuntimeApi declaration = FutureReferenceController.class
                .getDeclaredMethod("futureReference")
                .getAnnotation(EmbedDelegatedRuntimeApi.class);
        EmbedNativeFormTarget listTarget = new EmbedNativeFormTarget(
                "order", null, null, null,
                "default", "list-release-1", 2,
                "LIST", null, null,
                Map.of(), Map.of(), Map.of());
        when(policy.authorize(request, session, null, declaration))
                .thenReturn(listTarget);

        assertTrue(interceptor.preHandle(
                request, new MockHttpServletResponse(), handler));
        assertEquals(
                new EmbedDelegatedRequestContext.AuthorizedTarget(
                        "order", null, null, null, "LIST", null),
                request.getAttribute(
                        EmbedDelegatedRequestContext.TARGET_ATTRIBUTE));
    }

    private static MockHttpServletRequest delegatedRequest(
            AuthenticatedEmbedSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/future-reference/options");
        request.setAttribute(
                EmbedDelegatedRequestContext.VERIFIED_ATTRIBUTE,
                Boolean.TRUE);
        request.setAttribute(
                EmbedDelegatedRequestContext.AUTHENTICATED_SESSION_ATTRIBUTE,
                session);
        return request;
    }

    private static HandlerMethod handler(String name)
            throws NoSuchMethodException {
        FutureReferenceController controller =
                new FutureReferenceController();
        return new HandlerMethod(
                controller,
                FutureReferenceController.class.getDeclaredMethod(name));
    }

    private static AuthenticatedEmbedSession session() {
        return new AuthenticatedEmbedSession(
                "session-1", "app-1", "grant-1", "view-1", "release-1",
                "user-1", "alice", "https://portal.example",
                "channel-1234567890", "VIEW", "record-1",
                Map.of(), Set.of("RECORD_VIEW"),
                Instant.parse("2026-08-31T00:00:00Z"),
                Instant.parse("2026-08-31T01:00:00Z"));
    }

    private static EmbedNativeFormTarget target() {
        return new EmbedNativeFormTarget(
                "order", "form-1", "release-1", 3,
                "default", "list-release-1", 2,
                "VIEW", "record-1", "process-1",
                Map.of(), Map.of(), Map.of());
    }

    private static final class FutureReferenceController {

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.REFERENCE_READ,
                requiredCapability = EmbedDelegatedRuntimeApi.Capability
                        .RECORD_VIEW,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.NONE)
        void futureReference() {
        }

        void privateEndpoint() {
        }
    }
}
