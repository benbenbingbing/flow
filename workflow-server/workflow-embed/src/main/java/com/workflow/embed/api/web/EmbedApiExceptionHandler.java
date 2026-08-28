package com.workflow.embed.api.web;

import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.domain.EmbedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps expected Runtime boundary failures to the stable no-store Embed envelope. */
@RestControllerAdvice(basePackages = "com.workflow.embed.api.web")
public class EmbedApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(EmbedApiExceptionHandler.class);

    @ExceptionHandler(EmbedException.class)
    public ResponseEntity<Map<String, Object>> handle(
            EmbedException error,
            HttpServletRequest request) {
        HttpHeaders headers = responseHeaders(request);
        if (error.getRetryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER,
                    String.valueOf(error.getRetryAfterSeconds()));
        }
        return ResponseEntity.status(error.getStatus())
                .headers(headers)
                .body(errorBody(
                        error.getStatus(), error.getMessage(),
                        error.getErrorCode().name(), error.getData(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidRequest(
            MethodArgumentNotValidException error,
            HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .headers(responseHeaders(request))
                .body(errorBody(400, "Embed request is invalid", "INVALID_REQUEST", null, request));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            jakarta.validation.ConstraintViolationException.class
    })
    public ResponseEntity<Map<String, Object>> malformedRequest(
            Exception error,
            HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .headers(responseHeaders(request))
                .body(errorBody(400, "Embed request is invalid", "INVALID_REQUEST", null, request));
    }

    /**
     * Embed 边界的未知故障必须折叠为稳定 503，不能落入通用异常处理器并泄露 SQL、类名或配置。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(
            Exception error,
            HttpServletRequest request) {
        String traceId = CorrelationContext.businessTraceId(request);
        // 这里只记录异常类型和 traceId；异常 message 可能包含下游 SQL 或第三方响应片段。
        LOG.error("Embed Runtime unexpected failure: traceId={}, exceptionType={}",
                traceId,
                error.getClass().getName());
        return ResponseEntity.status(503)
                .headers(responseHeaders(request))
                .body(errorBody(
                        503,
                        "Embed runtime is temporarily unavailable",
                        "EMBED_RUNTIME_UNAVAILABLE",
                        null,
                        request));
    }

    private static Map<String, Object> errorBody(
            int status,
            String message,
            String errorCode,
            Object data,
            HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status);
        body.put("message", message);
        body.put("errorCode", errorCode);
        body.put("data", data);
        body.put("traceId", CorrelationContext.businessTraceId(request));
        return body;
    }

    /**
     * 错误响应复用 Guard 已规范化的 traceId；独立 MVC 测试或 Guard 之前的失败也会生成安全值。
     */
    private static HttpHeaders responseHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.set(
                CorrelationContext.BUSINESS_TRACE_HEADER,
                CorrelationContext.businessTraceId(request));
        return headers;
    }
}
