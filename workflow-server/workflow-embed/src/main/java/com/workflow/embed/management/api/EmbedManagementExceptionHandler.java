package com.workflow.embed.management.api;

import com.workflow.core.result.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 仅处理 Embed 管理命名空间的稳定业务异常。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.workflow.embed.management.api")
public class EmbedManagementExceptionHandler {

    @ExceptionHandler(EmbedManagementException.class)
    public ResponseEntity<ApiResponse<Object>> handle(EmbedManagementException exception) {
        ApiResponse<Object> response = ApiResponse.error(
                exception.status(), exception.errorCode(), exception.getMessage());
        response.setData(exception.data());
        return ResponseEntity.status(exception.status()).body(response);
    }

    /** 将 Bean Validation 失败稳定映射为 HTTP 400，并返回有限的字段级修复信息。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException exception) {
        List<Map<String, String>> violations = exception.getBindingResult()
                .getFieldErrors().stream()
                .limit(20)
                .map(error -> Map.of(
                        "path", error.getField(),
                        "reason", error.getDefaultMessage() == null
                                ? "invalid" : error.getDefaultMessage()))
                .toList();
        ApiResponse<Object> response = ApiResponse.error(
                400, "EMBED_MANAGEMENT_REQUEST_INVALID", "请求参数校验失败");
        response.setData(Map.of("violations", violations));
        return ResponseEntity.badRequest().body(response);
    }

    /** 坏 JSON、方法参数约束及应用层参数错误都使用相同的管理 API 400 契约。 */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                400, "EMBED_MANAGEMENT_REQUEST_INVALID", "请求参数不合法"));
    }
}
