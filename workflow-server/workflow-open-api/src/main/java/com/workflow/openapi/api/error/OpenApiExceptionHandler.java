package com.workflow.openapi.api.error;

import com.workflow.core.logging.LogValue;
import com.workflow.openapi.api.OpenIntegrationEndpoint;
import com.workflow.openapi.api.response.OpenApiResponse;
import com.workflow.openapi.web.OpenRequestTrace;
import com.workflow.openapi.web.OpenPayloadTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = OpenIntegrationEndpoint.class)
public class OpenApiExceptionHandler {

    @ExceptionHandler(OpenApiException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleOpenApi(
            OpenApiException exception,
            HttpServletRequest request) {
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(exception.getStatus())
                        .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (exception.getRetryAfterSeconds() != null) {
            response.header(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(
                            exception.getRetryAfterSeconds()));
        }
        return response.body(OpenApiResponse.error(
                exception.getStatus(),
                exception.getMessage(),
                exception.getErrorCode(),
                exception.getData(),
                OpenRequestTrace.get(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<Map<String, String>> violations =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .limit(20)
                        .map(error -> Map.of(
                                "path", error.getField(),
                                "reason", error.getDefaultMessage() == null
                                        ? "invalid"
                                        : error.getDefaultMessage()))
                        .toList();
        return invalid(
                "Request validation failed",
                Map.of("violations", violations),
                request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<OpenApiResponse<Object>> handleInvalid(
            Exception exception,
            HttpServletRequest request) {
        if (hasCause(
                exception,
                OpenPayloadTooLargeException.class)) {
            return ResponseEntity.status(413)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(OpenApiResponse.error(
                            413,
                            "Request body exceeds 1 MiB",
                            "PAYLOAD_TOO_LARGE",
                            null,
                            OpenRequestTrace.get(request)));
        }
        return invalid(
                "Request is invalid",
                null,
                request);
    }

    private boolean hasCause(
            Throwable value,
            Class<? extends Throwable> type) {
        Throwable current = value;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenApiResponse<Object>> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        log.error(
                "开放接口处理失败: traceId={}, path={}",
                LogValue.safe(OpenRequestTrace.get(request)),
                LogValue.safe(request.getRequestURI()),
                exception);
        return ResponseEntity.status(503)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(OpenApiResponse.error(
                        503,
                        "Integration capability is temporarily unavailable",
                        "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                        null,
                        OpenRequestTrace.get(request)));
    }

    private ResponseEntity<OpenApiResponse<Object>> invalid(
            String message,
            Object data,
            HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(OpenApiResponse.error(
                        400,
                        message,
                        "INVALID_REQUEST",
                        data,
                        OpenRequestTrace.get(request)));
    }
}
