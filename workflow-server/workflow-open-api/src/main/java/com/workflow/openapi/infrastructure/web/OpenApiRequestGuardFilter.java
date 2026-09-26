package com.workflow.openapi.infrastructure.web;

import com.workflow.openapi.api.error.OpenPayloadTooLargeException;

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

/**
 * 封装打开API请求保护过滤相关能力和状态；供同一业务流程的后续处理使用。
 */
public class OpenApiRequestGuardFilter extends OncePerRequestFilter {

    public static final int MAX_BODY_BYTES = 1_048_576;

    private final ObjectMapper objectMapper;

    /**
     * 初始化打开API请求保护过滤，保存构造参数供后续方法使用。
     *
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public OpenApiRequestGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理{@code do}过滤内部，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理{@code do}过滤内部
     * @param response 响应，作为 {@code writeTooLarge} 的输入影响后续处理
     * @param filterChain 过滤链，供本方法处理{@code do}过滤内部时使用
     * @throws ServletException 过滤器或请求处理链执行失败时抛出
     * @throws IOException 读取或写入外部资源失败时抛出
     */
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

    /**
     * 写入{@code too}{@code large}；后续读取或执行将使用更新后的状态。
     *
     * @param response 响应，作为 {@code objectMapper.writeValue} 的输入影响后续处理
     * @param traceId 追踪ID，后续用于写入{@code too}{@code large}时定位或关联目标
     * @throws IOException 读取或写入外部资源失败时抛出
     */
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

    /**
     * 承载受限的输入；后续由服务层校验并用于执行对应操作。
     */
    private static final class LimitedRequest
            extends HttpServletRequestWrapper {

        private ServletInputStream stream;

        /**
         * 初始化受限请求，保存构造参数供后续方法使用。
         *
         * @param request 本次请求，后续经校验后用于初始化受限
         */
        private LimitedRequest(HttpServletRequest request) {
            super(request);
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
                stream = new LimitedServletInputStream(
                        super.getInputStream());
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
                    getInputStream(),
                    StandardCharsets.UTF_8));
        }
    }

    /**
     * 封装受限Servlet输入流相关能力和状态；供同一业务流程的后续处理使用。
     */
    private static final class LimitedServletInputStream
            extends ServletInputStream {

        private final ServletInputStream delegate;
        private int count;

        /**
         * 初始化受限Servlet输入流，保存构造参数供后续方法使用。
         *
         * @param delegate 委托依赖，保存到当前对象供后续业务方法调用
         */
        private LimitedServletInputStream(
                ServletInputStream delegate) {
            this.delegate = delegate;
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

        /**
         * 添加受限Servlet输入流；结果供后续流程传递或持久化。
         *
         * @param value 待添加受限Servlet输入流的原始输入，结果供调用方继续使用
         * @throws OpenPayloadTooLargeException 操作失败时向调用方传递
         */
        private void add(int value)
                throws OpenPayloadTooLargeException {
            count += value;
            if (count > MAX_BODY_BYTES) {
                throw new OpenPayloadTooLargeException();
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
}
