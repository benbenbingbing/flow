package com.workflow.integration.database.api;

/** 只分类驱动提供的状态和错误码，不依赖 JDBC 类型、异常文本或执行连接。 */
@FunctionalInterface
public interface DatabaseErrorDialect {
    /** 无法确认的错误返回 UNKNOWN；不得据模糊文本将其当作唯一键冲突。 */
    DatabaseErrorKind classify(String sqlState, int vendorCode);
}
