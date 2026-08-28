package com.workflow.embed.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.session.EmbedSessionAuthenticationService;
import com.workflow.embed.application.session.EmbedSessionExchangeService;
import com.workflow.embed.domain.EmbedSessionIssued;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class EmbedSessionRequestShapeTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void exchangeRejectsUnknownAuthorizationCoordinatesUnderLenientJackson() throws Exception {
        String valid = """
                {
                  "launchCode":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                  "channelId":"channel_0123456789",
                  "parentOrigin":"https://portal.partner.example",
                  "parentNonce":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                  "childNonce":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                  "sdkVersion":"1.0.0"
                }
                """;
        assertEquals("channel_0123456789", objectMapper.readValue(
                valid, EmbedSessionController.ExchangeRequest.class).channelId());
        assertThrows(Exception.class, () -> objectMapper.readValue(
                valid.replace(
                        "\"sdkVersion\":\"1.0.0\"",
                        "\"sdkVersion\":\"1.0.0\",\"flowUserId\":\"user-2\""),
                EmbedSessionController.ExchangeRequest.class));
    }

    @Test
    void heartbeatRejectsUnknownFieldsUnderLenientJackson() throws Exception {
        assertEquals(Boolean.TRUE, objectMapper.readValue(
                "{\"visible\":true,\"clientTime\":\"2026-08-27T07:00:00Z\"}",
                EmbedSessionController.HeartbeatRequest.class).visible());
        assertThrows(Exception.class, () -> objectMapper.readValue(
                "{\"visible\":true,\"extendSeconds\":86400}",
                EmbedSessionController.HeartbeatRequest.class));
    }

    @Test
    void exchangePeerAddressUsesOnlyNormalizedSocketLiteral() {
        assertEquals("127.0.0.1", EmbedSessionController.normalizePeerAddress("127.0.0.1"));
        assertEquals("0:0:0:0:0:0:0:1", EmbedSessionController.normalizePeerAddress("::1"));
        assertEquals("unknown", EmbedSessionController.normalizePeerAddress("partner.example"));
        assertEquals("unknown", EmbedSessionController.normalizePeerAddress(null));
    }

    @Test
    void sessionHttpAdapterPassesGuardCorrelationWithoutUsingLaunchIdAsRequestId() {
        EmbedSessionExchangeService exchangeService = mock(EmbedSessionExchangeService.class);
        EmbedSessionAuthenticationService authenticationService =
                mock(EmbedSessionAuthenticationService.class);
        EmbedSessionController controller = new EmbedSessionController(
                exchangeService, authenticationService);
        EmbedAuditCorrelation correlation = EmbedAuditCorrelation.of(
                "trace-session-http", "request-session-http");
        when(exchangeService.exchange(any(), eq(correlation))).thenReturn(
                new EmbedSessionIssued(
                        "session-1",
                        "opaque-token",
                        Instant.parse("2026-08-27T09:00:00Z"),
                        Instant.parse("2026-08-27T08:30:00Z"),
                        60,
                        "/api/embed/v1/runtime/bootstrap",
                        "flow-embed/1"));
        MockHttpServletRequest request = correlatedRequest();
        request.setRemoteAddr("127.0.0.1");

        controller.exchange(
                "launch-1",
                "1",
                request,
                new EmbedSessionController.ExchangeRequest(
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        "channel_0123456789",
                        "https://portal.partner.example",
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                        "1.0.0"));
        controller.logout(
                "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                correlatedRequest());

        verify(exchangeService).exchange(any(), eq(correlation));
        verify(authenticationService).logoutAuthorization(
                "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                correlation);
    }

    private static MockHttpServletRequest correlatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-session-http");
        request.addHeader("X-Request-Id", "request-session-http");
        return request;
    }
}
