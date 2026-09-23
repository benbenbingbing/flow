package com.workflow.core.database.lock;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.runtime.DatabaseRuntimeDialect;
import com.workflow.integration.database.api.runtime.DatabaseLockPlan;
import com.workflow.core.database.port.DatabaseLockPort;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 跨进程发布锁适配器。DataSource 必须每次创建独立物理连接，不能使用业务连接池。
 * 锁连接从不执行业务 SQL/DDL，也不加入 Spring 事务；close 后会话和锁一起释放。
 */
public final class JdbcDatabaseLock implements DatabaseLockPort {
    private final DataSource connections;
    private final DatabaseRuntimeDialect dialect;

    /**
     * 初始化JDBC数据库锁定，保存构造参数供后续方法使用。
     *
     * @param connections {@code connections}依赖，保存到当前对象供后续业务方法调用
     * @param vendor 供应商，保存在对象中供后续校验、查询或展示
     */
    public JdbcDatabaseLock(DataSource connections, DatabaseVendor vendor) {
        this.connections = connections;
        this.dialect = DatabaseDialects.runtime(vendor);
    }

    /**
     * 处理尝试获取，并将结果传给后续步骤。
     *
     * @param namespace 命名空间，作为 {@code dialect.lockPlan} 的输入影响后续处理
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 匹配的尝试获取；未找到时为空
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    @Override
    public Optional<Handle> tryAcquire(String namespace, String key) {
        DatabaseLockPlan plan = dialect.lockPlan(namespace, key);
        Connection connection = null;
        try {
            connection = connections.getConnection();
            // OB Oracle 部分版本只支持事务结束释放的 DBMS_LOCK。
            // 该事务只持锁，DDL 和业务提交都在其他物理连接，释放时单独回滚。
            if (plan.rollbackOnRelease()) connection.setAutoCommit(false);
            int code = invoke(connection, plan.acquireSql(), plan);
            if (code != plan.acquiredCode() && code != plan.busyCode()) {
                throw new SQLException("数据库锁获取返回未知状态: " + code);
            }
            boolean acquired = code == plan.acquiredCode();
            if (!acquired) {
                Connection unused = connection;
                connection = null;
                closeSession(unused);
                return Optional.empty();
            }
            return Optional.of(new HeldLock(connection, plan));
        } catch (SQLException | RuntimeException exception) {
            if (connection != null) {
                try { closeSession(connection); } catch (SQLException closeFailure) { exception.addSuppressed(closeFailure); }
            }
            throw new IllegalStateException("获取数据库发布锁失败", exception);
        }
    }

    /**
     * 执行纯方言给出的调用描述；值按约定绑定，NULL 或未知状态必须报错。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @param sql SQL，作为 {@code try} 的输入影响后续处理
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @return 处理后的{@code invoke}结果，供调用方继续处理
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private int invoke(Connection connection, String sql, DatabaseLockPlan plan) throws SQLException {
        if (plan.invocation() == DatabaseLockPlan.Invocation.INTEGER_CALL) {
            try (var statement = connection.prepareCall(sql)) {
                statement.registerOutParameter(1, Types.INTEGER);
                statement.setInt(2, (Integer) plan.key());
                statement.execute();
                int code = statement.getInt(1);
                if (statement.wasNull()) throw new SQLException("数据库锁返回 NULL");
                return code;
            }
        }
        try (var statement = connection.prepareStatement(sql)) {
            if (plan.invocation() == DatabaseLockPlan.Invocation.TEXT_QUERY) statement.setString(1, (String) plan.key());
            else statement.setLong(1, (Long) plan.key());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("数据库锁没有返回结果");
                int code = plan.invocation() == DatabaseLockPlan.Invocation.BOOLEAN_QUERY
                        ? (result.getBoolean(1) ? 1 : 0) : result.getInt(1);
                if (result.wasNull()) throw new SQLException("数据库锁返回 NULL，不能当作普通锁竞争");
                return code;
            }
        }
    }

    /**
     * 只有专用持锁事务允许用回滚释放；其他产品执行方言的显式释放语句。
     *
     * @param connection 连接，供本方法处理发布版本时使用
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private void release(Connection connection, DatabaseLockPlan plan) throws SQLException {
        if (plan.rollbackOnRelease()) {
            connection.rollback();
        } else if (invoke(connection, plan.releaseSql(), plan) != plan.releasedCode()) {
            throw new SQLException("发布锁不再由当前会话持有");
        }
    }

    /**
     * 处理关闭会话，并将结果传给后续步骤。
     *
     * @param connection 连接，供本方法处理关闭会话时使用
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private void closeSession(Connection connection) throws SQLException {
        try { connection.close(); }
        catch (SQLException failure) {
            // close 失败不能把仍可能持锁的会话留给后续请求。
            try { connection.abort(Runnable::run); } catch (SQLException abortFailure) { failure.addSuppressed(abortFailure); }
            throw failure;
        }
    }

    /**
     * 封装{@code held}锁定相关能力和状态；供同一业务流程的后续处理使用。
     */
    private final class HeldLock implements Handle {
        private final Connection connection;
        private final DatabaseLockPlan plan;
        private final AtomicBoolean closed = new AtomicBoolean();
        /**
         * 初始化{@code held}锁定，保存构造参数供后续方法使用。
         *
         * @param connection 连接依赖，保存到当前对象供后续业务方法调用
         * @param plan 方案依赖，保存到当前对象供后续业务方法调用
         */
        private HeldLock(Connection connection, DatabaseLockPlan plan) { this.connection = connection; this.plan = plan; }
        /**
         * 处理关闭，并将结果传给后续步骤。
         *
         * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
         */
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            SQLException failure = null;
            try { release(connection, plan); } catch (SQLException exception) { failure = exception; }
            finally {
                try { closeSession(connection); }
                catch (SQLException exception) {
                    if (failure == null) failure = exception; else failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw new IllegalStateException("释放数据库发布锁失败，已关闭独立会话", failure);
        }
    }

}
