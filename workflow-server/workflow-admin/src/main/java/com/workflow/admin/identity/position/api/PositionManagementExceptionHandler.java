package com.workflow.admin.identity.position.api;

import com.workflow.core.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将职务领域失败稳定映射为可供前端逐项展示的错误码。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PositionManagementExceptionHandler {

    @ExceptionHandler(PositionManagementException.class)
    public ResponseEntity<Result<Void>> handle(
            PositionManagementException exception) {
        return ResponseEntity.status(exception.status())
                .body(Result.error(
                        exception.status(),
                        exception.errorCode().name(),
                        exception.getMessage()));
    }
}
