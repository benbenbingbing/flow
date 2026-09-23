package com.workflow.core.database;

import com.workflow.integration.database.api.DatabaseInsertDialect;
import com.workflow.integration.database.api.DatabaseErrorKind;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** 调用 integration 的纯插入方言，执行及语句级事务恢复属于 core JDBC 基础设施。 */
public class JdbcIdempotentInsert {
    private final JdbcTemplate jdbc;
    private final DatabaseInsertDialect dialect;
    private final DatabaseExceptionClassifier errors;

    public JdbcIdempotentInsert(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.dialect = Objects.requireNonNull(dialect);
        this.errors = DatabaseExceptionClassifier.forInsert(dialect);
    }

    /**
     * 插入一行返回 true，主键/唯一约束冲突返回 false，其他错误向上抛出。
     * 加入调用方事务，不提交；冲突仅回滚本次语句，不丢弃事务此前工作。
     *
     * <p>必须有即时唯一约束；不适用于延迟约束或触发器内其他写入的唯一冲突。
     * false 不授予锁或执行权，调用方须核验既有业务键与请求摘要。
     * 本方法不能用作计数器/锁行初始化，死锁与连接失败仍由外层重试。</p>
     */
    public boolean insertIfAbsent(String table, Map<String, ?> values) {
        var command = dialect.insert(table, values);
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            // PostgreSQL 等产品在 SQL 失败后会中止当前事务，必须先建立保存点。
            // 使用 Spring 绑定的同一连接，避免幂等占用脱离业务事务提前提交。
            Savepoint point = connection.getAutoCommit() ? null : connection.setSavepoint();
            boolean inserted;
            try (var statement = connection.prepareStatement(command.sql())) {
                for (int index = 0; index < command.parameters().size(); index++) {
                    JdbcScalarParameterBinder.bind(statement, index + 1, command.parameters().get(index));
                }
                if (statement.executeUpdate() != 1) throw new SQLException("单行插入未返回一行写入结果");
                inserted = true;
            } catch (SQLException error) {
                if (point != null) {
                    try {
                        connection.rollback(point);
                    } catch (SQLException rollbackError) {
                        // 死锁可能已经回滚整个事务，保存点失效时绝不能返回“已存在”。
                        error.addSuppressed(rollbackError);
                        throw error;
                    }
                }
                if (!isDuplicate(error)) throw error;
                inserted = false;
            }
            release(connection, point);
            return inserted;
        }));
    }

    private boolean isDuplicate(SQLException error) {
        return errors.classify(error) == DatabaseErrorKind.UNIQUE;
    }

    /** Oracle JDBC 等不支持显式释放时交给事务结束清理，不能通过提交来模拟释放。 */
    private static void release(Connection connection, Savepoint point) throws SQLException {
        if (point == null) return;
        try {
            connection.releaseSavepoint(point);
        } catch (SQLFeatureNotSupportedException unsupported) {
            // 保留调用方的原事务边界。
        }
    }
}
