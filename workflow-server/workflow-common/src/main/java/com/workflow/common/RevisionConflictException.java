package com.workflow.common;

import lombok.Getter;

/**
 * 客户端提交的 expectedRevision 已落后于服务器草稿。
 */
@Getter
public class RevisionConflictException extends RuntimeException {

    /** 服务器端当前的最新数据，便于客户端基于最新数据重试或合并 */
    private final Object currentData;

    /**
     * 构造版本号冲突异常。
     *
     * @param message     异常描述信息
     * @param currentData 服务器端当前的最新数据
     */
    public RevisionConflictException(String message, Object currentData) {
        super(message);
        this.currentData = currentData;
    }
}
