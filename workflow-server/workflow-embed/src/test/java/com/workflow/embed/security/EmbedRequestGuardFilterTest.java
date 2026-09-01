package com.workflow.embed.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.web.CorrelationContext;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class EmbedRequestGuardFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsDeclaredOversizedBodyBeforeCallingRuntime() throws Exception {
        EmbedRequestGuardFilter filter = new EmbedRequestGuardFilter(objectMapper, 8);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/embed/v1/runtime/list/query");
        request.setContent("123456789".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Trace-Id", "trace-guard");
        request.addHeader("X-Request-Id", "request-guard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                invoked.set(true));

        assertFalse(invoked.get());
        assertEquals(413, response.getStatus());
        assertEquals("trace-guard", response.getHeader("X-Trace-Id"));
        assertEquals("request-guard", response.getHeader("X-Request-Id"));
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals("PAYLOAD_TOO_LARGE", body.path("errorCode").asText());
    }

    @Test
    void enforcesActualBytesWhenContentLengthIsUnknown() throws Exception {
        EmbedRequestGuardFilter filter = new EmbedRequestGuardFilter(objectMapper, 8);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/embed/v1/launches/launch-1/exchange") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent("123456789".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean attemptedRead = new AtomicBoolean();

        filter.doFilter(request, response, (guardedRequest, ignoredResponse) -> {
            attemptedRead.set(true);
            guardedRequest.getInputStream().readAllBytes();
        });

        assertTrue(attemptedRead.get());
        assertEquals(413, response.getStatus());
    }

    @Test
    void normalizesInvalidTraceOnceForTheResponseAndDownstreamRuntime() throws Exception {
        EmbedRequestGuardFilter filter = new EmbedRequestGuardFilter(objectMapper, 64);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/embed/v1/runtime/bootstrap");
        request.addHeader("X-Trace-Id", "unsafe trace value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] downstreamTrace = new String[1];

        filter.doFilter(request, response, (guardedRequest, ignoredResponse) ->
                downstreamTrace[0] = CorrelationContext.businessTraceId(
                        (jakarta.servlet.http.HttpServletRequest) guardedRequest));

        String responseTrace = response.getHeader("X-Trace-Id");
        assertEquals(responseTrace, downstreamTrace[0]);
        assertFalse("unsafe trace value".equals(responseTrace));
        assertTrue(responseTrace.matches("[A-Za-z0-9._-]{1,64}"));
    }

    @Test
    void delegatedMultipartUsesPlatformUploadLimitInsteadOfEmbedJsonLimit()
            throws Exception {
        EmbedRequestGuardFilter filter = new EmbedRequestGuardFilter(
                objectMapper, 8);
        byte[] content = "native-multipart-file-body"
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/file/upload");
        request.addHeader(
                EmbedSessionAuthenticationFilter.PROTOCOL_HEADER, "1");
        request.setContentType("multipart/form-data; boundary=native");
        request.setContent(content);
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (downstream, response) -> {
                    invoked.set(true);
                    assertEquals(
                            "native-multipart-file-body",
                            new String(
                                    downstream.getInputStream().readAllBytes(),
                                    StandardCharsets.UTF_8));
                });

        assertTrue(invoked.get());
    }
}
