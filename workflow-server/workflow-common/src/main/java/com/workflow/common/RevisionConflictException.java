package com.workflow.common;

import lombok.Getter;

/**
 * 客户端提交的 expectedRevision 已落后于服务器草稿。
 */
@Getter
public class RevisionConflictException extends RuntimeException {

    private final Object currentData;

    public RevisionConflictException(String message, Object currentData) {
        super(message);
        this.currentData = currentData;
    }
}
