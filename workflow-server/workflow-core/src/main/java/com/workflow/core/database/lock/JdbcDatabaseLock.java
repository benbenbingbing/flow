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
 * OB MySQL 专用事务最多保持24小时，操作必须在该上限内完成；释放时的标记校验不是运行中失锁 fencing。
 */
public final class JdbcDatabaseLock implements DatabaseLockPort {
    private final DataSource connections;
    private final DatabaseRuntimeDialect dialect;

    /**
     * 建立跨实例锁适配器；所有调用必须从此数据源取得独立物理连接。
     *
     * @param connections 非池化数据源，关闭连接必须真正结束数据库会话
     * @param vendor 决定原生会话锁或独立事务锁的数据库方言
     */
    public JdbcDatabaseLock(DataSource connections, DatabaseVendor vendor) {
        this.connections = connections;
        this.dialect = DatabaseDialects.runtime(vendor);
    }

    /**
     * 非阻塞争抢命名空间内的业务锁，成功后由句柄独占连接直到关闭。
     *
     * @param namespace 经过方言校验的锁命名空间，隔离发布、清理等业务
     * @param key 业务身份，哈希后构造稳定锁键，不直接拼接到SQL
     * @return 获得锁的独立会话句柄；只有明确的锁竞争返回空
     * @throws IllegalStateException 数据库结构、连接、返回状态或会话清理异常
     */
    @Override
    public Optional<Handle> tryAcquire(String namespace, String key) {
        DatabaseLockPlan plan = dialect.lockPlan(namespace, key);
        Connection connection = null;
        try {
            connection = connections.getConnection();
            for (String sql : plan.sessionInitSql()) {
                try (var statement = connection.createStatement()) { statement.execute(sql); }
            }
            // OB 两种模式均只在专用连接中持锁，DDL 和业务提交不能结束此事务。
            if (plan.rollbackOnRelease()) connection.setAutoCommit(false);
            int code = acquire(connection, plan);
            if (code != plan.acquiredCode() && code != plan.busyCode()) {
                throw new SQLException("数据库锁获取返回未知状态: " + code);
            }
            boolean acquired = code == plan.acquiredCode();
            if (!acquired) {
                Connection unused = connection;
                connection = null;
                discardSession(unused, plan);
                return Optional.empty();
            }
            return Optional.of(new HeldLock(connection, plan));
        } catch (SQLException | RuntimeException exception) {
            if (connection != null) {
                try { discardSession(connection, plan); } catch (SQLException closeFailure) { exception.addSuppressed(closeFailure); }
            }
            throw new IllegalStateException("获取数据库发布锁失败", exception);
        }
    }

    /**
     * OB MySQL 用未提交的唯一键行充当锁标记，首次并发初始化和后续争抢走同一原子写路径。
     * 不提交标记，不用 INSERT IGNORE；缺表、权限、重复键及事务失效都必须向上传播。
     */
    private int acquire(Connection connection, DatabaseLockPlan plan) throws SQLException {
        if (plan.invocation() != DatabaseLockPlan.Invocation.TEXT_TRANSACTION_INSERT) {
            return invoke(connection, plan.acquireSql(), plan);
        }
        try (var statement = connection.prepareStatement(plan.acquireSql())) {
            statement.setString(1, (String) plan.key());
            try {
                int inserted = statement.executeUpdate();
                if (inserted != 1) throw new SQLException("数据库事务锁未创建唯一标记");
                return plan.acquiredCode();
            } catch (SQLException failure) {
                // 只识别该 INSERT 的 NO WAIT 行锁冲突，不能把网络或事务超时伪装成正常竞争。
                if (failure.getErrorCode() == plan.busyErrorCode()) return plan.busyCode();
                throw failure;
            }
        }
    }

    /**
     * 执行纯方言给出的调用描述；值按约定绑定，NULL 或未知状态必须报错。
     *
     * @param connection 获取或释放锁的独立物理连接
     * @param sql 方言提供的锁调用或标记检查SQL
     * @param plan 决定锁键绑定类型和返回值读取方式
     * @return 供调用方校验获取、竞争或释放状态的返回码
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
            if (plan.invocation() == DatabaseLockPlan.Invocation.TEXT_QUERY
                    || plan.invocation() == DatabaseLockPlan.Invocation.TEXT_TRANSACTION_INSERT) {
                statement.setString(1, (String) plan.key());
            }
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
     * @param connection 当前句柄持有的独立物理连接
     * @param plan 决定显式释放或检查标记后回滚
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private void release(Connection connection, DatabaseLockPlan plan) throws SQLException {
        if (plan.rollbackOnRelease()) {
            SQLException failure = null;
            if (plan.invocation() == DatabaseLockPlan.Invocation.TEXT_TRANSACTION_INSERT) {
                // 专用事务也有超时上限：标记丢失必须报告，不能在事务已失效时仍宣称正常释放。
                try {
                    if (invoke(connection, plan.releaseSql(), plan) != plan.releasedCode()) {
                        throw new SQLException("数据库事务锁标记已丢失，持锁事务可能已超时或断开");
                    }
                } catch (SQLException exception) { failure = exception; }
            }
            try { connection.rollback(); }
            catch (SQLException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
            if (failure != null) throw failure;
        } else if (invoke(connection, plan.releaseSql(), plan) != plan.releasedCode()) {
            throw new SQLException("发布锁不再由当前会话持有");
        }
    }

    /** 竞争者和获取失败的会话也显式回滚，避免依赖驱动关闭连接时的隐式事务清理。 */
    private void discardSession(Connection connection, DatabaseLockPlan plan) throws SQLException {
        SQLException failure = null;
        if (plan.rollbackOnRelease()) {
            try { connection.rollback(); } catch (SQLException exception) { failure = exception; }
        }
        try { closeSession(connection); }
        catch (SQLException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    /**
     * 关闭物理会话；常规关闭失败时尝试中止连接，避免遗留持锁会话。
     *
     * @param connection 不再被任何句柄使用的独立连接
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
     * 独占锁连接的句柄；原子关闭标记防止重复释放或并发关闭同一会话。
     */
    private final class HeldLock implements Handle {
        private final Connection connection;
        private final DatabaseLockPlan plan;
        private final AtomicBoolean closed = new AtomicBoolean();
        /** 接管已成功持锁的连接，其生命周期完全由本句柄负责。 */
        private HeldLock(Connection connection, DatabaseLockPlan plan) { this.connection = connection; this.plan = plan; }
        /**
         * 幂等释放锁并关闭会话，即使释放失败也执行物理连接清理。
         *
         * @throws IllegalStateException 锁已丢失，或释放及连接清理失败
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
