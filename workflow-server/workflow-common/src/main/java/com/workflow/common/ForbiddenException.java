package com.workflow.common;

/**
 * 访问被拒绝。
 */
public class ForbiddenException extends RuntimeException {

    /**
     * 构造访问被拒绝异常。
     *
     * @param message 异常描述信息
     */
    public ForbiddenException(String message) {
        super(message);
    }
}
