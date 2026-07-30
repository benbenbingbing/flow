package com.workflow.openapi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.web.CorrelationContext;
import com.workflow.openapi.api.response.OpenApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

public class OpenApiRequestGuardFilter extends OncePerRequestFilter {

    public static final int MAX_BODY_BYTES = 1_048_576;

    private final ObjectMapper objectMapper;

    public OpenApiRequestGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = CorrelationContext.businessTraceId(request);
        String requestId = CorrelationContext.requestId(request);
        response.setHeader(OpenRequestTrace.HEADER, traceId);
        response.setHeader(CorrelationContext.REQUEST_ID_HEADER, requestId);
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "no-store");
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_BODY_BYTES) {
            writeTooLarge(response, traceId);
            return;
        }
        try {
            filterChain.doFilter(
                    new LimitedRequest(request),
                    response);
        } catch (OpenPayloadTooLargeException exception) {
            if (!response.isCommitted()) {
                response.reset();
                response.setHeader(
                        OpenRequestTrace.HEADER,
                        traceId);
                writeTooLarge(response, traceId);
                return;
            }
            throw exception;
        }
    }

    private void writeTooLarge(
            HttpServletResponse response,
            String traceId) throws IOException {
        response.setStatus(413);
        response.setContentType(
                "application/json;charset=UTF-8");
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "no-store");
        objectMapper.writeValue(
                response.getOutputStream(),
                OpenApiResponse.error(
                        413,
                        "Request body exceeds 1 MiB",
                        "PAYLOAD_TOO_LARGE",
                        null,
                        traceId));
    }

    private static final class LimitedRequest
            extends HttpServletRequestWrapper {

        private ServletInputStream stream;

        private LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedServletInputStream(
                        super.getInputStream());
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(
                    getInputStream(),
                    StandardCharsets.UTF_8));
        }
    }

    private static final class LimitedServletInputStream
            extends ServletInputStream {

        private final ServletInputStream delegate;
        private int count;

        private LimitedServletInputStream(
                ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length)
                throws IOException {
            int allowed = Math.min(
                    length,
                    MAX_BODY_BYTES - count + 1);
            int read = delegate.read(bytes, offset, allowed);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(int value)
                throws OpenPayloadTooLargeException {
            count += value;
            if (count > MAX_BODY_BYTES) {
                throw new OpenPayloadTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
