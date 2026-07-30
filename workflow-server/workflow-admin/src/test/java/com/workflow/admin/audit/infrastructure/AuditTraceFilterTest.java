package com.workflow.admin.audit.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.workflow.core.web.CorrelationContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuditTraceFilterTest {

    @Test
    void propagatesSafeTraceIdAndClearsMdc()
            throws Exception {
        AuditTraceFilter filter = new AuditTraceFilter();
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(
                AuditTraceFilter.TRACE_ID_HEADER,
                "request-123");
        request.addHeader(
                CorrelationContext.REQUEST_ID_HEADER,
                "frontend-request-456");
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                        assertEquals(
                                "request-123",
                                MDC.get(
                                        AuditTraceFilter
                                                .TRACE_ID_MDC_KEY));
                        assertEquals(
                                "request-123",
                                MDC.get(
                                        CorrelationContext
                                                .BUSINESS_TRACE_MDC_KEY));
                        assertEquals(
                                "frontend-request-456",
                                MDC.get(
                                        CorrelationContext
                                                .REQUEST_ID_MDC_KEY));
                });

        assertEquals(
                "request-123",
                response.getHeader(
                        AuditTraceFilter.TRACE_ID_HEADER));
        assertEquals(
                "frontend-request-456",
                response.getHeader(
                        CorrelationContext.REQUEST_ID_HEADER));
        assertNull(MDC.get(AuditTraceFilter.TRACE_ID_MDC_KEY));
        assertNull(MDC.get(CorrelationContext.BUSINESS_TRACE_MDC_KEY));
        assertNull(MDC.get(CorrelationContext.REQUEST_ID_MDC_KEY));
    }

    @Test
    void replacesUnsafeTraceId() throws Exception {
        AuditTraceFilter filter = new AuditTraceFilter();
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(
                AuditTraceFilter.TRACE_ID_HEADER,
                "unsafe trace");
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> {
                });

        String generated = response.getHeader(
                AuditTraceFilter.TRACE_ID_HEADER);
        assertEquals(32, generated.length());
        assertFalse(generated.contains(" "));
    }
}
