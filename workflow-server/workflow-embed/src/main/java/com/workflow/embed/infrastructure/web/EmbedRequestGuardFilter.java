package com.workflow.embed.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.api.response.EmbedApiEnvelope;
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

    /**
     * 初始化嵌入式请求保护过滤，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param maxBodyBytes 最大请求体字节依赖，保存到当前对象供后续业务方法调用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public EmbedRequestGuardFilter(ObjectMapper objectMapper, int maxBodyBytes) {
        if (objectMapper == null || maxBodyBytes < 1) {
            throw new IllegalArgumentException("Embed request guard configuration is invalid");
        }
        this.objectMapper = objectMapper;
        this.maxBodyBytes = maxBodyBytes;
    }

    /**
     * 处理{@code do}过滤内部，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理{@code do}过滤内部
     * @param response 响应，作为 {@code filterChain.doFilter} 的输入影响后续处理
     * @param filterChain 过滤链，供本方法处理{@code do}过滤内部时使用
     * @throws ServletException 过滤器或请求处理链执行失败时抛出
     * @throws IOException 读取或写入外部资源失败时抛出
     */
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

    /**
     * 判断是否委托{@code multipart}；判断结果决定调用方的后续分支。
     *
     * @param request 本次请求，后续经校验后用于判断是否委托{@code multipart}
     * @return 委托{@code multipart}条件成立时为 true，否则为 false
     */
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

    /**
     * 写入{@code too}{@code large}；后续读取或执行将使用更新后的状态。
     *
     * @param response 响应，作为 {@code objectMapper.writeValue} 的输入影响后续处理
     * @param traceId 追踪ID，后续用于写入{@code too}{@code large}时定位或关联目标
     * @param requestId 请求ID，后续用于写入{@code too}{@code large}时定位或关联目标
     * @throws IOException 读取或写入外部资源失败时抛出
     */
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

    /**
     * 承载受限的输入；后续由服务层校验并用于执行对应操作。
     */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final int maxBodyBytes;
        private ServletInputStream stream;

        /**
         * 初始化受限请求，保存构造参数供后续方法使用。
         *
         * @param request 本次请求，后续经校验后用于初始化受限
         * @param maxBodyBytes 最大请求体字节依赖，保存到当前对象供后续业务方法调用
         */
        private LimitedRequest(HttpServletRequest request, int maxBodyBytes) {
            super(request);
            this.maxBodyBytes = maxBodyBytes;
        }

        /**
         * 读取输入流；查询结果供调用方展示或继续处理。
         *
         * @return 符合条件的Servlet输入流结果，供调用方继续处理
         * @throws IOException 读取或写入外部资源失败时抛出
         */
        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedServletInputStream(super.getInputStream(), maxBodyBytes);
            }
            return stream;
        }

        /**
         * 读取{@code reader}；查询结果供调用方展示或继续处理。
         *
         * @return 符合条件的{@code buffered}{@code reader}结果，供调用方继续处理
         * @throws IOException 读取或写入外部资源失败时抛出
         */
        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), StandardCharsets.UTF_8));
        }
    }

    /**
     * 封装受限Servlet输入流相关能力和状态；供同一业务流程的后续处理使用。
     */
    private static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final int maxBodyBytes;
        private int count;

        /**
         * 初始化受限Servlet输入流，保存构造参数供后续方法使用。
         *
         * @param delegate 委托依赖，保存到当前对象供后续业务方法调用
         * @param maxBodyBytes 最大请求体字节依赖，保存到当前对象供后续业务方法调用
         */
        private LimitedServletInputStream(ServletInputStream delegate, int maxBodyBytes) {
            this.delegate = delegate;
            this.maxBodyBytes = maxBodyBytes;
        }

        /**
         * 读取受限Servlet输入流；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的受限Servlet输入流结果，供调用方继续处理
         * @throws IOException 读取或写入外部资源失败时抛出
         */
        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        /**
         * 读取受限Servlet输入流；查询结果供调用方展示或继续处理。
         *
         * @param bytes 字节，供本方法读取受限Servlet输入流时使用
         * @param offset 偏移参数，用于限制后续查询范围和返回数量
         * @param length 长度，作为 {@code Math.min} 的输入影响后续处理
         * @return 读取后的受限Servlet输入流结果，供调用方继续处理
         * @throws IOException 读取或写入外部资源失败时抛出
         */
        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int allowed = Math.min(length, maxBodyBytes - count + 1);
            int read = delegate.read(bytes, offset, allowed);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        /**
         * 添加受限Servlet输入流；结果供后续流程传递或持久化。
         *
         * @param value 待添加受限Servlet输入流的原始输入，结果供调用方继续使用
         * @throws PayloadTooLargeException 操作失败时向调用方传递
         */
        private void add(int value) throws PayloadTooLargeException {
            count += value;
            if (count > maxBodyBytes) {
                throw new PayloadTooLargeException();
            }
        }

        /**
         * 判断是否{@code finished}；判断结果决定调用方的后续分支。
         *
         * @return {@code finished}条件成立时为 true，否则为 false
         */
        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        /**
         * 判断是否就绪；判断结果决定调用方的后续分支。
         *
         * @return 就绪条件成立时为 true，否则为 false
         */
        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        /**
         * 设置读取监听器；后续读取或执行将使用更新后的状态。
         *
         * @param readListener 读取监听器，供本方法设置读取监听器时使用
         */
        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }

    /**
     * 表示载荷{@code too}{@code large}处理失败；调用方可据此区分错误并终止后续操作。
     */
    private static final class PayloadTooLargeException extends IOException {
    }
}
