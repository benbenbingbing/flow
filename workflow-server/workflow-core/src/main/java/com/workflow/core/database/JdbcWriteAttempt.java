package com.workflow.core.database;

import com.workflow.integration.database.api.DatabaseInsertDialect;
import com.workflow.integration.database.api.DatabaseErrorKind;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;
import java.util.Objects;
import java.util.function.IntSupplier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 对调用方已有的 Mapper/JDBC 写入建立保存点，允许业务在唯一冲突后继续当前事务。 */
public class JdbcWriteAttempt {
    private final JdbcTemplate jdbc;
    private final DatabaseExceptionClassifier errors;

    public JdbcWriteAttempt(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.errors = DatabaseExceptionClassifier.forInsert(dialect);
    }

    /**
     * 执行一次同步写语句并保留其影响行数；失败时先恢复保存点，再将唯一冲突统一抛为
     * DuplicateKeyException。恢复失败始终向上失败，不能被业务当成“已存在”。
     *
     * <p>回调必须直接使用同一数据源的 Mapper/JdbcTemplate，不得跨事务代理、切换线程、
     * 使用批处理延迟执行或执行 DDL。Spring JDBC 事务内不提交、不回滚先前工作；没有
     * Spring 事务时按自动提交处理，仅翻译错误，不支持调用方手工管理的独立连接事务。
     * 保存点不会撤销 Java 对象赋值或缓存；业务重读须绕过旧缓存。</p>
     */
    public int execute(IntSupplier statement) {
        Objects.requireNonNull(statement);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            try { return statement.getAsInt(); }
            catch (RuntimeException error) { throw classify(error); }
        }
        return jdbc.execute((ConnectionCallback<Integer>) connection -> {
            if (connection.getAutoCommit() || !DataSourceUtils.isConnectionTransactional(
                    DataSourceUtils.getTargetConnection(connection), jdbc.getDataSource())) {
                throw new IllegalStateException("可恢复写入必须使用调用方事务的数据源");
            }
            Savepoint point = connection.setSavepoint();
            int result;
            try {
                result = statement.getAsInt();
            } catch (RuntimeException error) {
                try {
                    connection.rollback(point);
                    release(connection, point);
                } catch (SQLException recovery) {
                    // 死锁/断连可能使保存点失效；禁止继续使用已损坏或已整体回滚的事务。
                    recovery.addSuppressed(error);
                    throw new DataAccessResourceFailureException("写入失败且事务保存点无法恢复", recovery);
                }
                throw classify(error);
            }
            release(connection, point);
            return result;
        });
    }

    /** 厂商错误码来自纯方言；混合连接/事务错误链绝不能只因存在重复键而被吞掉。 */
    private RuntimeException classify(RuntimeException error) {
        if (errors.classify(error) == DatabaseErrorKind.UNIQUE) {
            return error instanceof DuplicateKeyException ? error : new DuplicateKeyException("数据库唯一约束冲突", error);
        }
        return error instanceof DuplicateKeyException
                ? new DataAccessResourceFailureException("重复键异常同时包含无法恢复的其他数据库错误", error) : error;
    }

    private static void release(Connection connection, Savepoint point) throws SQLException {
        try { connection.releaseSavepoint(point); }
        catch (SQLFeatureNotSupportedException unsupported) {
            // Oracle 等驱动可由外层事务结束清理；不能通过提交模拟保存点释放。
        }
    }
}
