package com.workflow.contracts.audit;

/**
 * 系统审计操作类型。
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    UPSERT,
    DELETE,
    BATCH_DELETE,
    ENABLE,
    DISABLE,
    RESET_PASSWORD,
    ASSIGN_PERMISSION,
    PUBLISH,
    UNPUBLISH,
    ROLLBACK,
    IMPORT,
    EXPORT,
    UPLOAD,
    START,
    APPROVE,
    REJECT,
    TRANSFER,
    WITHDRAW,
    RESUBMIT,
    TERMINATE,
    ADD_SIGN,
    CANCEL_ADD_SIGN,
    CC,
    RETRY,
    LOGIN,
    LOGOUT,
    CONFIGURE,
    OTHER
}
