package com.workflow.integration.database.api.error;

/** 只分类驱动提供的状态和错误码，不依赖 JDBC 类型、异常文本或执行连接。 */
@FunctionalInterface
public interface DatabaseErrorDialect {
    /**
     * 无法确认的错误返回 UNKNOWN；不得据模糊文本将其当作唯一键冲突。
     *
     * @param sqlState 数据库 SQLState，后续用于识别唯一约束冲突
     * @param vendorCode 数据库供应商错误码，与 SQLState 一起判断唯一约束冲突
     * @return 处理后的{@code classify}结果，供调用方继续处理
     */
    DatabaseErrorKind classify(String sqlState, int vendorCode);
}
