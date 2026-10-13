package com.workflow.database;

import com.workflow.core.database.jdbc.InitializedDriverDataSource;
import com.workflow.core.database.lock.JdbcDatabaseLock;
import com.workflow.core.database.port.DatabaseLockPort;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 OB MySQL 回归；测试 URL 必须指向已应用 V116 的独立验证库，不修改现有业务数据。 */
@EnabledIfEnvironmentVariable(named = "FLOW_OBMYSQL_TEST_URL", matches = ".+")
class OceanBaseDatabaseLockDatabaseTest {
    @Test
    void firstUseContentionHasExactlyOneWinnerAndDoesNotWaitForRelease() throws Exception {
        var source = new TrackingSource();
        var pool = Executors.newFixedThreadPool(8);
        try {
            // 每轮用新键覆盖“尚无锁行”的首次并发获取；成功者一直持有到所有竞争者返回。
            for (int round = 0; round < 20; round++) {
                String key = UUID.randomUUID().toString();
                var start = new CountDownLatch(1);
                var attempted = new CountDownLatch(8);
                var release = new CountDownLatch(1);
                var winners = new AtomicInteger();
                var futures = new ArrayList<Future<?>>();
                try {
                    for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                        Optional<DatabaseLockPort.Handle> acquired = Optional.empty();
                        try {
                            start.await();
                            try {
                                acquired = new JdbcDatabaseLock(source, DatabaseVendor.OCEANBASE_MYSQL)
                                        .tryAcquire("flow:entity", key);
                                if (acquired.isPresent()) winners.incrementAndGet();
                            } finally { attempted.countDown(); }
                            if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("release timeout");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(error);
                        } finally { acquired.ifPresent(DatabaseLockPort.Handle::close); }
                    }));
                    start.countDown();
                    assertTrue(attempted.await(5, TimeUnit.SECONDS), "NO WAIT losers must return while winner is held");
                    assertEquals(1, winners.get(), "round=" + round);
                } finally {
                    release.countDown();
                    for (var future : futures) future.get(10, TimeUnit.SECONDS);
                }
                try (var reacquired = new JdbcDatabaseLock(source, DatabaseVendor.OCEANBASE_MYSQL)
                        .tryAcquire("flow:entity", key).orElseThrow()) { assertNotNull(reacquired); }
            }
        } finally { pool.shutdownNow(); }
        for (var connection : source.opened) assertTrue(connection.isClosed(), "dedicated connection leaked");
        try (var c = source.getConnection(); var s = c.createStatement();
             var r = s.executeQuery("SELECT COUNT(*) FROM sys_database_lock")) {
            assertTrue(r.next());
            assertEquals(0, r.getInt(1), "lock markers must never be committed");
        }
    }

    @Test
    void businessCommitRollbackDdlAndOtherKeysDoNotReleaseTheLock() throws Exception {
        var source = new TrackingSource();
        var locks = new JdbcDatabaseLock(source, DatabaseVendor.OCEANBASE_MYSQL);
        String key = UUID.randomUUID().toString();
        String table = "lock_probe_" + key.replace("-", "");
        try (var held = locks.tryAcquire("flow:entity", key).orElseThrow();
             var business = source.getConnection(); var s = business.createStatement()) {
            business.setAutoCommit(false);
            s.execute("SELECT 1");
            business.commit();
            assertTrue(locks.tryAcquire("flow:entity", key).isEmpty());
            business.rollback();
            assertTrue(locks.tryAcquire("flow:entity", key).isEmpty());
            s.execute("CREATE TABLE " + table + " (id INT)");
            try {
                assertTrue(locks.tryAcquire("flow:entity", key).isEmpty());
                try (var other = locks.tryAcquire("flow:entity", key + "-other").orElseThrow()) {
                    assertNotNull(other);
                    assertTrue(locks.tryAcquire("flow:entity", key).isEmpty());
                }
            } finally { s.execute("DROP TABLE " + table); }
        }
        try (var reacquired = locks.tryAcquire("flow:entity", key).orElseThrow()) { assertNotNull(reacquired); }
    }

    @Test
    void timeoutSettingsAreAppliedOnlyToHeldSessionAndTransactionLossIsDetected() throws Exception {
        var source = new TrackingSource();
        var locks = new JdbcDatabaseLock(source, DatabaseVendor.OCEANBASE_MYSQL);
        String key = UUID.randomUUID().toString();
        var held = locks.tryAcquire("flow:entity", key).orElseThrow();
        var connection = source.opened.get(0);
        try {
            assertFalse(connection.getAutoCommit());
            try (var s = connection.createStatement(); var r = s.executeQuery(
                    "SELECT @@ob_trx_lock_timeout, @@ob_trx_timeout, @@ob_trx_idle_timeout")) {
                assertTrue(r.next());
                assertEquals(0, r.getLong(1));
                assertEquals(86_400_000_000L, r.getLong(2));
                assertEquals(86_400_000_000L, r.getLong(3));
            }
            // 新建的业务连接仍保持较短测试值，证明锁参数没有污染全局或业务数据源。
            try (var business = source.getConnection(); var s = business.createStatement();
                 var r = s.executeQuery("SELECT @@ob_trx_timeout")) {
                assertTrue(r.next());
                assertEquals(1_000_000L, r.getLong(1));
            }
            // 模拟数据库自动终止持锁事务；close 必须报告失锁，并仍关闭专用物理连接。
            connection.rollback();
            assertThrows(IllegalStateException.class, held::close);
            assertTrue(connection.isClosed());
        } finally { held.close(); }
        try (var reacquired = locks.tryAcquire("flow:entity", key).orElseThrow()) { assertNotNull(reacquired); }
    }

    /** 保留物理连接引用仅用于断言清理和模拟事务丢失，生产锁不会暴露连接。 */
    private static final class TrackingSource extends AbstractDataSource {
        private final InitializedDriverDataSource delegate = new InitializedDriverDataSource(
                System.getenv("FLOW_OBMYSQL_TEST_URL"), System.getenv("FLOW_OBMYSQL_TEST_USER"),
                System.getenv("FLOW_OBMYSQL_TEST_PASSWORD"), null,
                DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.OCEANBASE_MYSQL));
        private final List<Connection> opened = new CopyOnWriteArrayList<>();

        @Override public Connection getConnection() throws SQLException {
            var connection = delegate.getConnection();
            // 缩短普通连接事务期限，确认锁适配器会独立覆盖，不受租户默认值影响。
            try (var s = connection.createStatement()) { s.execute("SET SESSION ob_trx_timeout = 1000000"); }
            opened.add(connection);
            return connection;
        }
        @Override public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("test source uses configured credentials");
        }
    }
}
