package com.workflow.openapi.api.error;

import com.workflow.core.logging.LogValue;
import com.workflow.contracts.embed.error.EmbedBoundaryFailure;
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

/**
 * 负责打开API异常的业务处理；协调校验、状态变化及后续结果传递。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = OpenIntegrationEndpoint.class)
public class OpenApiExceptionHandler {

    /**
     * 处理打开API，并将结果传给后续步骤。
     *
     * @param exception 异常，作为 {@code ResponseEntity.status} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理打开API
     * @return 处理后的打开API结果，供调用方继续处理
     */
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

    /**
     * 处理校验，并将结果传给后续步骤。
     *
     * @param exception 异常，供本方法处理校验时使用
     * @param request 本次请求，后续经校验后用于处理校验
     * @return 处理后的校验结果，供调用方继续处理
     */
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

    /**
     * 处理无效，并将结果传给后续步骤。
     *
     * @param exception 异常，供本方法处理无效时使用
     * @param request 本次请求，后续经校验后用于处理无效
     * @return 处理后的无效结果，供调用方继续处理
     */
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

    /**
     * 判断是否具有原因；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否具有原因的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续原因采用的处理分支
     * @return 原因条件成立时为 true，否则为 false
     */
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

    /**
     * 处理{@code unexpected}，并将结果传给后续步骤。
     *
     * @param exception 异常，供本方法处理{@code unexpected}时使用
     * @param request 本次请求，后续经校验后用于处理{@code unexpected}
     * @return 处理后的{@code unexpected}结果，供调用方继续处理
     */
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

    /**
     * 保留 Embed 业务边界已经完成脱敏的状态码与错误码，同时避免 Open API 反向依赖
     * workflow-embed 实现模块。
     *
     * @param exception 异常，作为 {@code handleUnexpected} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理{@code boundary}失败
     * @return 处理后的{@code boundary}失败结果，供调用方继续处理
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleBoundaryFailure(
            RuntimeException exception,
            HttpServletRequest request) {
        if (!(exception instanceof EmbedBoundaryFailure failure)) {
            return handleUnexpected(exception, request);
        }
        int status = failure.status();
        if (status < 400 || status > 599) {
            return handleUnexpected(exception, request);
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (failure.retryAfterSeconds() != null
                && failure.retryAfterSeconds() > 0) {
            response.header(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(failure.retryAfterSeconds()));
        }
        return response.body(OpenApiResponse.error(
                status,
                exception.getMessage(),
                failure.errorCode(),
                null,
                OpenRequestTrace.get(request)));
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @param message 消息，供本方法处理无效时使用
     * @param data 数据，后续用于处理无效并传递处理结果
     * @param request 本次请求，后续经校验后用于处理无效
     * @return 处理后的无效结果，供调用方继续处理
     */
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
