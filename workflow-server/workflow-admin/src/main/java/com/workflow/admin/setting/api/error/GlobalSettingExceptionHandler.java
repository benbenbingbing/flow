package com.workflow.admin.setting.api.error;

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
    /**
     * 处理全局设置异常，并将结果传给后续步骤。
     *
     * @param exception 异常，作为 {@code ResponseEntity.status} 的输入影响后续处理
     * @return 处理后的全局设置异常结果，供调用方继续处理
     */
    @ExceptionHandler(GlobalSettingException.class)
    public ResponseEntity<Result<Void>> handle(GlobalSettingException exception) {
        return ResponseEntity.status(exception.status()).body(
                Result.error(exception.status(), exception.errorCode(), exception.getMessage()));
    }
}
