package com.workflow.openapi.api.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.workflow.contracts.embed.EmbedBoundaryFailure;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class OpenApiExceptionHandlerEmbedBoundaryTest {

    private final OpenApiExceptionHandler handler =
            new OpenApiExceptionHandler();

    @Test
    void preservesSafeEmbedStatusCodeAndRetryHint() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-launch-limit");

        var response = handler.handleBoundaryFailure(
                new BoundaryException(
                        429,
                        "EMBED_LAUNCH_RATE_LIMITED",
                        12L,
                        "Launch rate limit exceeded"),
                request);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("12", response.getHeaders().getFirst(
                HttpHeaders.RETRY_AFTER));
        assertEquals("EMBED_LAUNCH_RATE_LIMITED",
                response.getBody().errorCode());
        assertEquals("trace-launch-limit", response.getBody().traceId());
        assertNull(response.getBody().data());
    }

    private static final class BoundaryException
            extends RuntimeException implements EmbedBoundaryFailure {

        private final int status;
        private final String errorCode;
        private final Long retryAfterSeconds;

        private BoundaryException(
                int status,
                String errorCode,
                Long retryAfterSeconds,
                String message) {
            super(message);
            this.status = status;
            this.errorCode = errorCode;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        @Override
        public int status() {
            return status;
        }

        @Override
        public String errorCode() {
            return errorCode;
        }

        @Override
        public Long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
