package com.workflow.common;

import lombok.Getter;

/**
 * 业务禁止异常。
 *
 * <p>当请求操作因业务规则被禁止（如状态不允许、权限不足等）时抛出，
 * 携带稳定的业务错误编码 {@code errorCode} 以便上层统一处理。</p>
 */
@Getter
public class BusinessForbiddenException extends ForbiddenException {

    /** 稳定的业务错误编码，用于前端/调用方区分具体禁止原因 */
    private final String errorCode;

    /**
     * 构造业务禁止异常。
     *
     * @param errorCode 业务错误编码
     * @param message   异常描述信息
     */
    public BusinessForbiddenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
