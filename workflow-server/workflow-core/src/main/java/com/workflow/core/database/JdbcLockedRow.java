package com.workflow.core.database;

import com.workflow.integration.database.api.sql.BoundSqlStatement;
import com.workflow.integration.database.api.write.DatabaseInsertDialect;
import com.workflow.integration.database.api.error.DatabaseErrorKind;
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

    /**
     * 初始化JDBC已锁定行，保存构造参数供后续方法使用。
     *
     * @param jdbc JDBC，保存在对象中供后续校验、查询或展示
     * @param dialect 方言，保存在对象中供后续校验、查询或展示
     */
    public JdbcLockedRow(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.dialect = Objects.requireNonNull(dialect);
        this.errors = DatabaseExceptionClassifier.forInsert(dialect);
    }

    /**
     * 缺失时创建占位行，已有行保持初始值不变，并取得唯一键行锁直到业务事务结束。
     * keyColumns 必须为已有的即时唯一约束；多行锁的稳定顺序由业务负责。
     * 没有本数据源的 Spring 事务、目标行缺失或匹配多行均失败，禁止静默退化为无锁读取。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param initialValues 缺失行首次创建时的列值；已有行的业务值不会被覆盖
     * @param keyColumns 对应主键或唯一约束的列，后续用于锁定目标行
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

    /**
     * 准备JDBC已锁定行；结果供调用方的后续步骤使用。
     *
     * @param connection 连接，供本方法准备JDBC已锁定行时使用
     * @param command 本次命令，后续经校验后用于准备JDBC已锁定行
     * @return 准备后的JDBC已锁定行结果，供调用方继续处理
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
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

    /**
     * 判断是否{@code duplicate}；判断结果决定调用方的后续分支。
     *
     * @param error 错误，作为 {@code errors.classify} 的输入影响后续处理
     * @return {@code duplicate}条件成立时为 true，否则为 false
     */
    private boolean isDuplicate(SQLException error) {
        return errors.classify(error) == DatabaseErrorKind.UNIQUE;
    }
}
