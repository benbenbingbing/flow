package com.workflow.embed.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.api.web.EmbedApiEnvelope;
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
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Embed API 的请求体硬上限。
 *
 * <p>同时检查 {@code Content-Length} 和实际读取字节数，因此分块传输也不能绕过限制。过滤器
 * 必须位于 JSON 反序列化和 Embed Token 鉴权之前，避免超大请求消耗运行态资源。</p>
 */
public final class EmbedRequestGuardFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final int maxBodyBytes;

    public EmbedRequestGuardFilter(ObjectMapper objectMapper, int maxBodyBytes) {
        if (objectMapper == null || maxBodyBytes < 1) {
            throw new IllegalArgumentException("Embed request guard configuration is invalid");
        }
        this.objectMapper = objectMapper;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = CorrelationContext.businessTraceId(request);
        String requestId = CorrelationContext.requestId(request);
        response.setHeader(CorrelationContext.BUSINESS_TRACE_HEADER, traceId);
        response.setHeader(CorrelationContext.REQUEST_ID_HEADER, requestId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (isDelegatedMultipart(request)) {
            // 原生上传继续使用平台统一 multipart 大小、文件权限和
            // 存储策略；Embed JSON 的协议上限不能改变原生文件组件语义。
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > maxBodyBytes) {
            writeTooLarge(response, traceId, requestId);
            return;
        }
        try {
            filterChain.doFilter(new LimitedRequest(request, maxBodyBytes), response);
        } catch (PayloadTooLargeException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            response.reset();
            writeTooLarge(response, traceId, requestId);
        }
    }

    private static boolean isDelegatedMultipart(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contentType = request.getContentType();
        return path != null
                && path.startsWith("/api/")
                && !path.startsWith("/api/embed/")
                && request.getHeader(
                        EmbedSessionAuthenticationFilter.PROTOCOL_HEADER) != null
                && contentType != null
                && contentType.toLowerCase(java.util.Locale.ROOT)
                        .startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private void writeTooLarge(
            HttpServletResponse response,
            String traceId,
            String requestId)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationContext.BUSINESS_TRACE_HEADER, traceId);
        response.setHeader(CorrelationContext.REQUEST_ID_HEADER, requestId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), new EmbedApiEnvelope<>(
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "Request body is too large",
                "PAYLOAD_TOO_LARGE",
                null,
                traceId));
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final int maxBodyBytes;
        private ServletInputStream stream;

        private LimitedRequest(HttpServletRequest request, int maxBodyBytes) {
            super(request);
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedServletInputStream(super.getInputStream(), maxBodyBytes);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final int maxBodyBytes;
        private int count;

        private LimitedServletInputStream(ServletInputStream delegate, int maxBodyBytes) {
            this.delegate = delegate;
            this.maxBodyBytes = maxBodyBytes;
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
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int allowed = Math.min(length, maxBodyBytes - count + 1);
            int read = delegate.read(bytes, offset, allowed);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(int value) throws PayloadTooLargeException {
            count += value;
            if (count > maxBodyBytes) {
                throw new PayloadTooLargeException();
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

    private static final class PayloadTooLargeException extends IOException {
    }
}
