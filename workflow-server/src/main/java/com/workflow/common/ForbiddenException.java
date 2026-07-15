package com.workflow.common;

/**
 * 访问被拒绝。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
