package com.workflow.contracts.identity.error;

/** 可定位字段的稳定写入失败；所有异常均导致当前保存事务回滚。 */
public class IdentityWriteException extends RuntimeException {
    private final String code;
    private final String field;
    public IdentityWriteException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }
    public String code() { return code; }
    public String field() { return field; }
}
