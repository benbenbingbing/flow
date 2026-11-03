package com.workflow.admin.identity.extension.api.web;

import com.workflow.contracts.identity.error.IdentityWriteException;
import com.workflow.core.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 保存失败返回稳定错误码，前端可区分并发冲突、主权约束和数据校验。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class IdentityWriteExceptionHandler {
    @ExceptionHandler(IdentityWriteException.class)
    public ResponseEntity<Result<Void>> handle(IdentityWriteException error) {
        int status = error.code().contains("CONFLICT") ? 409
                : error.code().contains("FORBIDDEN") || error.code().contains("NOT_ALLOWED") || error.code().equals("SOURCE_FIELD_READ_ONLY") ? 403
                : error.code().equals("OBJECT_NOT_FOUND") ? 404 : 422;
        return ResponseEntity.status(status).body(Result.error(status, error.code(), error.getMessage()));
    }
}
