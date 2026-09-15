package com.workflow.admin.setting.api;

import com.workflow.core.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将设置异常转换为稳定响应；并发冲突使用 409，便于客户端重新读取。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalSettingExceptionHandler {
    @ExceptionHandler(GlobalSettingException.class)
    public ResponseEntity<Result<Void>> handle(GlobalSettingException exception) {
        return ResponseEntity.status(exception.status()).body(
                Result.error(exception.status(), exception.errorCode(), exception.getMessage()));
    }
}
