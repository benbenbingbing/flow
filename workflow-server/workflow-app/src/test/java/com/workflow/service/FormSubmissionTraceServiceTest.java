package com.workflow.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormSubmissionTraceServiceTest {

    private final FormSubmissionTraceService service =
            new FormSubmissionTraceService();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void reusesClientBusinessTraceKeyWithinRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(
                FormSubmissionTraceService.BUSINESS_TRACE_HEADER,
                "ui_retry_123");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        FormSubmissionExecutionContext first =
                service.current(
                        "ENTITY_UPDATE",
                        null,
                        Map.of());
        FormSubmissionExecutionContext second =
                service.current(
                        "ENTITY_UPDATE",
                        null,
                        Map.of());

        assertEquals(
                "ui_retry_123",
                first.businessTraceKey());
        assertEquals(
                first.businessTraceKey(),
                second.businessTraceKey());
    }

    @Test
    void differentBusinessSubmissionsProduceDifferentBindingKeys() {
        FormSubmissionExecutionContext first =
                new FormSubmissionExecutionContext(
                        "trace-1",
                        "ENTITY_UPDATE",
                        Map.of());
        FormSubmissionExecutionContext second =
                new FormSubmissionExecutionContext(
                        "trace-2",
                        "ENTITY_UPDATE",
                        Map.of());

        assertNotEquals(
                first.bindingIdempotencyKey(
                        "form-1",
                        "node:node-1",
                        "source-1",
                        0),
                second.bindingIdempotencyKey(
                        "form-1",
                        "node:node-1",
                        "source-1",
                        0));
    }

    @Test
    void rejectsUnsafeBusinessTraceHeader() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(
                FormSubmissionTraceService.BUSINESS_TRACE_HEADER,
                "bad trace key");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.current(
                        "ENTITY_CREATE",
                        null,
                        Map.of()));
    }
}
