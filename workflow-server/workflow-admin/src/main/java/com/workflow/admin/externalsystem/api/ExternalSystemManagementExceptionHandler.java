package com.workflow.admin.externalsystem.api;

import com.workflow.core.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将外部系统管理失败映射为稳定的 HTTP 状态和业务错误码。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ExternalSystemManagementExceptionHandler {

    /**
     * 返回前端可稳定识别的外部系统管理错误。
     *
     * @param exception 业务异常
     * @return 带 HTTP 状态、错误码和错误消息的响应
     */
    @ExceptionHandler(ExternalSystemManagementException.class)
    public ResponseEntity<Result<Void>> handle(
            ExternalSystemManagementException exception) {
        return ResponseEntity.status(exception.status())
                .body(Result.error(
                        exception.status(),
                        exception.errorCode().name(),
                        exception.getMessage()));
    }
}
