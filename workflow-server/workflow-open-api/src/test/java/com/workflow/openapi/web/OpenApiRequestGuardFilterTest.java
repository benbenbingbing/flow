package com.workflow.openapi.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.web.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OpenApiRequestGuardFilterTest {

    private OpenApiRequestGuardFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new OpenApiRequestGuardFilter(objectMapper);
    }

    @Test
    void rejectsDeclaredBodyLargerThanOneMebibyte()
            throws Exception {
        MockHttpServletRequest request =
                request(OpenApiRequestGuardFilter.MAX_BODY_BYTES + 1);
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) ->
                        chainCalls.incrementAndGet());

        assertEquals(413, response.getStatus());
        assertEquals(0, chainCalls.get());
        assertEquals(
                "PAYLOAD_TOO_LARGE",
                objectMapper.readTree(
                                response.getContentAsByteArray())
                        .path("errorCode")
                        .asText());
        assertEquals(
                "trace-known-length",
                response.getHeader(OpenRequestTrace.HEADER));
        assertEquals(
                "request-known-length",
                response.getHeader(CorrelationContext.REQUEST_ID_HEADER));
        assertEquals(
                "no-store",
                response.getHeader("Cache-Control"));
    }

    @Test
    void rejectsUnknownLengthBodyWhileItIsRead()
            throws Exception {
        MockHttpServletRequest source =
                request(OpenApiRequestGuardFilter.MAX_BODY_BYTES + 1);
        HttpServletRequest unknownLength =
                new HttpServletRequestWrapper(source) {
                    @Override
                    public long getContentLengthLong() {
                        return -1;
                    }

                    @Override
                    public int getContentLength() {
                        return -1;
                    }
                };
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                unknownLength,
                response,
                (guardedRequest, ignoredResponse) ->
                        guardedRequest.getInputStream()
                                .readAllBytes());

        assertEquals(413, response.getStatus());
        assertEquals(
                "PAYLOAD_TOO_LARGE",
                objectMapper.readTree(
                                response.getContentAsByteArray())
                        .path("errorCode")
                        .asText());
    }

    @Test
    void invalidTraceIdentifierIsReplacedAndEchoed()
            throws Exception {
        MockHttpServletRequest request = request(32);
        request.removeHeader(OpenRequestTrace.HEADER);
        request.addHeader(
                OpenRequestTrace.HEADER,
                "trace value with spaces");
        request.removeHeader(CorrelationContext.REQUEST_ID_HEADER);
        request.addHeader(
                CorrelationContext.REQUEST_ID_HEADER,
                "request-open-1");
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (guardedRequest, ignoredResponse) -> {
                    assertEquals(
                            32,
                            guardedRequest.getInputStream()
                                    .readAllBytes()
                                    .length);
                    assertNotEquals(
                            "trace value with spaces",
                            OpenRequestTrace.get(
                                    (HttpServletRequest)
                                            guardedRequest));
                });

        String traceId =
                response.getHeader(OpenRequestTrace.HEADER);
        assertTrue(traceId.matches("[A-Za-z0-9._-]{1,64}"));
        assertEquals(
                "request-open-1",
                response.getHeader(CorrelationContext.REQUEST_ID_HEADER));
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest request(int bodySize) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/open/v1/process-instances");
        request.setContent(new byte[bodySize]);
        request.addHeader(
                OpenRequestTrace.HEADER,
                "trace-known-length");
        request.addHeader(
                CorrelationContext.REQUEST_ID_HEADER,
                "request-known-length");
        return request;
    }
}
