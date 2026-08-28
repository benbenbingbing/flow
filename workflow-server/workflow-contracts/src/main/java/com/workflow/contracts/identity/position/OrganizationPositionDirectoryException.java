package com.workflow.contracts.identity.position;

/**
 * 组织职务目录的结构化异常，流程运行时将其稳定码写入待处置事件。
 */
public class OrganizationPositionDirectoryException extends RuntimeException {

    private final OrganizationPositionErrorCode errorCode;

    public OrganizationPositionDirectoryException(
            OrganizationPositionErrorCode errorCode,
            String message) {
        super(message);
        if (errorCode == null) {
            throw new IllegalArgumentException("组织职务目录错误码不能为空");
        }
        this.errorCode = errorCode;
    }

    public OrganizationPositionDirectoryException(
            OrganizationPositionErrorCode errorCode,
            String message,
            Throwable cause) {
        super(message, cause);
        if (errorCode == null) {
            throw new IllegalArgumentException("组织职务目录错误码不能为空");
        }
        this.errorCode = errorCode;
    }

    public OrganizationPositionErrorCode errorCode() {
        return errorCode;
    }
}
