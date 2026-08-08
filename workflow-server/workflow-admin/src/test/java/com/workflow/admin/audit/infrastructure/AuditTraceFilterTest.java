package com.workflow.admin.audit.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.workflow.core.web.CorrelationContext;
import jakarta.servlet.ServletException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
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

    @Test
    void logsCompletedRequestWithDiagnosticContext()
            throws Exception {
        AuditTraceFilter filter = new AuditTraceFilter();
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/process/start");
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        response.setStatus(201);

        List<ILoggingEvent> events = captureLogs(() ->
                filter.doFilter(
                        request,
                        response,
                        (ignoredRequest, ignoredResponse) -> {
                        }));

        ILoggingEvent event = events.get(events.size() - 1);
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().contains(
                "traceId=" + response.getHeader(
                        AuditTraceFilter.TRACE_ID_HEADER)));
        assertTrue(event.getFormattedMessage().contains(
                "requestId=" + response.getHeader(
                        CorrelationContext.REQUEST_ID_HEADER)));
        assertTrue(event.getFormattedMessage().contains(
                "method=POST"));
        assertTrue(event.getFormattedMessage().contains(
                "path=/api/process/start"));
        assertTrue(event.getFormattedMessage().contains(
                "status=201"));
        assertTrue(event.getFormattedMessage().contains(
                "durationMs="));
        assertTrue(event.getFormattedMessage().contains(
                "failureType=NONE"));
        assertEquals(
                response.getHeader(AuditTraceFilter.TRACE_ID_HEADER),
                event.getMDCPropertyMap().get(
                        AuditTraceFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    void logsEscapedFailureBeforeClearingMdc() {
        AuditTraceFilter filter = new AuditTraceFilter();
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/process/failure");
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        ListAppender<ILoggingEvent> appender = startAppender();
        try {
            assertThrows(
                    ServletException.class,
                    () -> filter.doFilter(
                            request,
                            response,
                            (ignoredRequest, ignoredResponse) -> {
                                throw new ServletException("failed");
                            }));
        } finally {
            logger().detachAppender(appender);
        }

        ILoggingEvent event =
                appender.list.get(appender.list.size() - 1);
        assertTrue(event.getFormattedMessage().contains(
                "status=500"));
        assertTrue(event.getFormattedMessage().contains(
                "failureType=ServletException"));
        assertNull(MDC.get(AuditTraceFilter.TRACE_ID_MDC_KEY));
    }

    private List<ILoggingEvent> captureLogs(
            ThrowingRunnable action) throws Exception {
        ListAppender<ILoggingEvent> appender = startAppender();
        try {
            action.run();
            return List.copyOf(appender.list);
        } finally {
            logger().detachAppender(appender);
        }
    }

    private ListAppender<ILoggingEvent> startAppender() {
        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
        return appender;
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(
                AuditTraceFilter.class);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
