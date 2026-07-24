package com.workflow.config;

import com.workflow.common.BusinessConflictException;
import com.workflow.common.BusinessForbiddenException;
import com.workflow.common.ForbiddenException;
import com.workflow.common.RevisionConflictException;
import com.workflow.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务状态冲突异常，返回 409 状态码。
     *
     * @param e 业务冲突异常
     * @return 包含错误码与错误信息的 409 响应
     */
    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessConflictException(BusinessConflictException e) {
        log.warn("业务状态冲突: errorCode={}, message={}", e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, e.getErrorCode(), e.getMessage()));
    }

    /**
     * 处理配置修订版本冲突异常，返回 409 状态码并附带当前最新数据。
     *
     * @param e 修订冲突异常
     * @return 包含错误信息与当前最新数据的 409 响应
     */
    @ExceptionHandler(RevisionConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleRevisionConflictException(
            RevisionConflictException e) {
        log.warn("配置修订冲突: {}", e.getMessage());
        ApiResponse<Object> response =
                ApiResponse.error(409, "CONFIG_REVISION_CONFLICT", e.getMessage());
        response.setData(e.getCurrentData());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 处理访问拒绝异常，返回 403 状态码。
     *
     * <p>当异常为 {@link BusinessForbiddenException} 时附带业务错误码，否则返回通用 403。
     *
     * @param e 访问拒绝异常
     * @return 403 响应
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(ForbiddenException e) {
        log.warn("访问拒绝: {}", e.getMessage());
        if (e instanceof BusinessForbiddenException businessException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(
                            403,
                            businessException.getErrorCode(),
                            businessException.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, e.getMessage()));
    }

    /**
     * 处理非法参数异常，返回 400 状态码。
     *
     * @param e 非法参数异常
     * @return 包含错误信息的 400 响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("请求参数异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
    }

    /**
     * 处理静态资源未找到异常，返回 404 状态码。
     *
     * @return 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "资源不存在"));
    }

    /**
     * 处理数据库唯一约束冲突异常
     */
    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ApiResponse<Void> handleDuplicateKeyException(Exception e) {
        log.warn("数据完整性异常: {}", e.getMessage());
        
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("Data too long for column")) {
                int start = message.indexOf("column '");
                if (start >= 0) {
                    int fieldStart = start + 8;
                    int fieldEnd = message.indexOf("'", fieldStart);
                    if (fieldEnd > fieldStart) {
                        String fieldName = message.substring(fieldStart, fieldEnd);
                        return ApiResponse.error("字段 '" + fieldName + "' 内容过长，请缩短后重试");
                    }
                }
                return ApiResponse.error("字段内容过长，请缩短后重试");
            }
            // 处理没有默认值的字段错误
            if (message.contains("doesn't have a default value")) {
                int start = message.indexOf("Field '");
                if (start >= 0) {
                    int fieldStart = start + 7;
                    int fieldEnd = message.indexOf("'", fieldStart);
                    if (fieldEnd > fieldStart) {
                        String fieldName = message.substring(fieldStart, fieldEnd);
                        return ApiResponse.error("字段 '" + fieldName + "' 不能为空且没有默认值，请检查表单配置");
                    }
                }
                return ApiResponse.error("必填字段没有填写且没有默认值");
            }
            // 提取重复键信息
            if (message.contains("Duplicate entry")) {
                int start = message.indexOf("Duplicate entry");
                int end = message.indexOf(" for key");
                if (start >= 0 && end > start) {
                    String duplicateValue = message.substring(start + 16, end);
                    String keyName = "";
                    int keyStart = message.indexOf("'", end);
                    int keyEnd = message.indexOf("'", keyStart + 1);
                    if (keyStart > 0 && keyEnd > keyStart) {
                        keyName = message.substring(keyStart + 1, keyEnd);
                    }
                    return ApiResponse.error("数据重复: 值 '" + duplicateValue + "' 在字段 '" + keyName + "' 中已存在");
                }
            }
        }
        
        return ApiResponse.error("数据保存失败: " + (message != null ? message.substring(0, Math.min(100, message.length())) : ""));
    }

    /**
     * 处理业务异常（RuntimeException）
     */
    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResponse.error(e.getMessage());
    }

    /**
     * 处理数据库异常
     */
    @ExceptionHandler(SQLException.class)
    public ApiResponse<Void> handleSQLException(SQLException e) {
        log.error("数据库异常: ", e);
        return ApiResponse.error("数据库操作失败，请稍后重试");
    }

    /**
     * 处理其他所有异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return ApiResponse.error("系统繁忙，请稍后重试");
    }
}
