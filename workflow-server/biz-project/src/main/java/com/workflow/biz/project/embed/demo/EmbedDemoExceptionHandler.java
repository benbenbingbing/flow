package com.workflow.biz.project.embed.demo;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 错误响应只返回可公开元数据，不回显校验拒绝值或上游响应。 */
@RestControllerAdvice
@Profile("partner-embed-demo")
class EmbedDemoExceptionHandler {
    @ExceptionHandler(EmbedDemoException.class)
    ResponseEntity<Map<String, Object>> handle(EmbedDemoException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", exception.status);
        body.put("errorCode", exception.errorCode);
        body.put("message", "嵌入请求失败，请根据 errorCode 和 traceId 排查");
        if (exception.traceId != null) {
            body.put("traceId", exception.traceId);
        }
        var response = ResponseEntity.status(exception.status).cacheControl(CacheControl.noStore());
        if (exception.retryAfter != null) {
            response.header("Retry-After", exception.retryAfter);
        }
        return response.body(body);
    }

    /** 包括未知字段、格式错误和 Bean Validation，统一拒绝，避免暴露绑定详情。 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, Object>> invalidRequest(Exception ignored) {
        return handle(EmbedDemoException.badRequest());
    }
}
