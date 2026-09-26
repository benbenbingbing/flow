package com.workflow.admin.identity.position.api.error;

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

    /**
     * 处理位置管理异常，并将结果传给后续步骤。
     *
     * @param exception 异常，作为 {@code ResponseEntity.status} 的输入影响后续处理
     * @return 处理后的位置管理异常结果，供调用方继续处理
     */
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
