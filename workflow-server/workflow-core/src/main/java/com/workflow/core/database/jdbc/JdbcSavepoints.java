package com.workflow.core.database.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;

/** 保存点释放的驱动兼容边界；不支持释放时仍由调用方的提交/回滚清理，不能提前结束事务。 */
final class JdbcSavepoints {
    private JdbcSavepoints() { }

    /**
     * 释放本次写入的保存点；仅容忍驱动明确不支持释放，其他错误继续交给事务调用方处理。
     * OceanBase Connector/J 2.4 的 Oracle 模式在客户端直接抛 17023/99999，异常类型却是
     * SQLTransientConnectionException，因此不能只检查 SQLFeatureNotSupportedException，
     * 也不能把所有连接异常吞掉。身份、错误码、状态和独立错误链必须同时匹配。
     *
     * @param connection 当前业务事务的连接，本方法不会提交或回滚该事务
     * @param point 本次语句创建的保存点；自动提交模式没有保存点时允许为 null
     * @throws SQLException 释放失败且不是已确认的不支持能力时抛出，防止继续使用损坏事务
     */
    static void release(Connection connection, Savepoint point) throws SQLException {
        if (point == null) return;
        try {
            connection.releaseSavepoint(point);
        } catch (SQLException failure) {
            // 带 cause/next/suppressed 的错误可能同时包含断连或事务损坏，不能降级为能力缺失。
            if (failure.getCause() != null || failure.getNextException() != null
                    || failure.getSuppressed().length != 0) throw failure;
            if (failure instanceof SQLFeatureNotSupportedException) return;
            if (failure.getErrorCode() == 17023 && "99999".equals(failure.getSQLState())
                    && "OceanBase Connector/J".equals(connection.getMetaData().getDriverName())) return;
            throw failure;
        }
    }
}
