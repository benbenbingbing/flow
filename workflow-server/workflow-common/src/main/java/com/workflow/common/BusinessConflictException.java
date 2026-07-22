package com.workflow.common;

import lombok.Getter;

/**
 * 当前资源状态与请求操作冲突。
 */
@Getter
public class BusinessConflictException extends RuntimeException {

    private final String errorCode;

    public BusinessConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
