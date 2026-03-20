package com.workflow.config;

import com.workflow.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理数据库唯一约束冲突异常
     */
    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ApiResponse<Void> handleDuplicateKeyException(Exception e) {
        log.warn("数据重复异常: {}", e.getMessage());
        
        // 提取实体编码相关的重复提示
        String message = e.getMessage();
        if (message != null && message.contains("entity_code")) {
            return ApiResponse.error("实体编码已存在，请更换其他编码");
        }
        if (message != null && message.contains("process_key")) {
            return ApiResponse.error("流程标识已存在，请更换其他标识");
        }
        if (message != null && message.contains("field_code")) {
            return ApiResponse.error("字段编码已存在，请更换其他编码");
        }
        
        return ApiResponse.error("数据已存在，请检查是否有重复项");
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
