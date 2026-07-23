package com.workflow.common;

import lombok.Getter;

/**
 * 当前资源状态与请求操作冲突。
 */
@Getter
public class BusinessConflictException extends RuntimeException {

    /** 稳定的业务错误编码，用于前端/调用方区分具体冲突类型 */
    private final String errorCode;

    /**
     * 构造业务冲突异常。
     *
     * @param errorCode 业务错误编码
     * @param message   异常描述信息
     */
    public BusinessConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
