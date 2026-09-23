package com.workflow.core.database;

import com.workflow.integration.database.api.BoundSqlStatement;
import com.workflow.integration.database.api.DatabaseInsertDialect;
import com.workflow.integration.database.api.DatabaseErrorKind;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 执行方言的事务锁行计划；只参与调用方事务，不提交、不释放业务行锁。 */
public class JdbcLockedRow {
    private final JdbcTemplate jdbc;
    private final DatabaseInsertDialect dialect;
    private final DatabaseExceptionClassifier errors;

    public JdbcLockedRow(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.dialect = Objects.requireNonNull(dialect);
        this.errors = DatabaseExceptionClassifier.forInsert(dialect);
    }

    /**
     * 缺失时创建占位行，已有行保持初始值不变，并取得唯一键行锁直到业务事务结束。
     * keyColumns 必须为已有的即时唯一约束；多行锁的稳定顺序由业务负责。
     * 没有本数据源的 Spring 事务、目标行缺失或匹配多行均失败，禁止静默退化为无锁读取。
     */
    public void ensureAndLock(String table, Map<String, ?> initialValues, List<String> keyColumns) {
        var plan = dialect.rowLock(table, initialValues, keyColumns);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("事务锁行必须在业务事务内执行");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (connection.getAutoCommit() || !DataSourceUtils.isConnectionTransactional(
                    DataSourceUtils.getTargetConnection(connection), jdbc.getDataSource())) {
                throw new IllegalStateException("事务锁行连接未加入调用方事务");
            }
            Savepoint point = plan.recoverInsertConflict() ? connection.setSavepoint() : null;
            SQLException raced = null;
            try (var statement = prepare(connection, plan.initialize())) {
                // no-op upsert 的影响行数受驱动选项影响，执行成功后统一以锁定查询验收。
                statement.executeUpdate();
            } catch (SQLException error) {
                if (point == null) throw error;
                try { connection.rollback(point); }
                catch (SQLException restore) { error.addSuppressed(restore); throw error; }
                if (!isDuplicate(error)) throw error;
                raced = error;
            }
            try (var statement = prepare(connection, plan.lock()); var row = statement.executeQuery()) {
                if (!row.next()) {
                    // 其他唯一索引冲突并不证明目标键存在；必须向上失败，不能授予错误的锁。
                    if (raced != null) throw raced;
                    throw new SQLException("事务锁行目标不存在", "02000");
                }
                if (row.next()) throw new SQLException("事务锁行唯一键匹配多行", "21000");
            }
            if (point != null) {
                try { connection.releaseSavepoint(point); }
                catch (SQLFeatureNotSupportedException unsupported) { /* 随事务结束清理，不通过提交释放。 */ }
            }
            return null;
        });
    }

    private PreparedStatement prepare(Connection connection, BoundSqlStatement command) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(command.sql());
        try {
            for (int index = 0; index < command.parameters().size(); index++) {
                JdbcScalarParameterBinder.bind(statement, index + 1, command.parameters().get(index));
            }
            return statement;
        } catch (SQLException | RuntimeException error) {
            try { statement.close(); } catch (SQLException close) { error.addSuppressed(close); }
            throw error;
        }
    }

    private boolean isDuplicate(SQLException error) {
        return errors.classify(error) == DatabaseErrorKind.UNIQUE;
    }
}
