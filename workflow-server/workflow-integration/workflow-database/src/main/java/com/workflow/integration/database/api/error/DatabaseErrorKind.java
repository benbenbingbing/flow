package com.workflow.integration.database.api.error;

/** 数据库错误的稳定语义；UNKNOWN 不表示成功，也不能作为幂等重复处理。 */
public enum DatabaseErrorKind {
    UNIQUE, NOT_NULL, MISSING_DEFAULT, VALUE_TOO_LONG, FOREIGN_KEY, CHECK,
    NUMERIC_RANGE, DEADLOCK, LOCK_TIMEOUT, CONNECTION, TRANSACTION_ROLLBACK, UNKNOWN
}
