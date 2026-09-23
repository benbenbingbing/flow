package com.workflow.database;

import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import com.workflow.core.database.InitializedDriverDataSource;

import com.workflow.core.database.lock.JdbcDatabaseLock;
import com.workflow.integration.database.api.DatabaseVendor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 MySQL 会话竞争；独立连接的提交、回滚、DDL 都不得提前释放另一会话的发布锁。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlDatabaseLockDatabaseTest {
    private InitializedDriverDataSource source() {
        return new InitializedDriverDataSource(System.getenv("FLOW_MYSQL_TEST_URL"), System.getenv("FLOW_MYSQL_TEST_USER"),
                System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null, DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.MYSQL));
    }

    @Test
    void lockSurvivesOtherConnectionCommitRollbackAndDdlAndMatchesLegacyName() throws Exception {
        var source = source();
        var first = new JdbcDatabaseLock(source, DatabaseVendor.MYSQL);
        var second = new JdbcDatabaseLock(source, DatabaseVendor.MYSQL);
        String key = UUID.randomUUID().toString();
        String table = "biz_lock_test_" + key.replace("-", "").substring(0, 16);
        try (var handle = first.tryAcquire("flow:entity", key).orElseThrow();
             var other = source.getConnection(); var statement = other.createStatement()) {
            other.setAutoCommit(false);
            statement.execute("SELECT 1");
            other.commit();
            other.rollback();
            statement.execute("CREATE TABLE " + table + " (id INT)");
            try { assertTrue(second.tryAcquire("flow:entity", key).isEmpty()); }
            finally { statement.execute("DROP TABLE " + table); }
            try (var legacy = other.prepareStatement("SELECT GET_LOCK(CONCAT('flow:entity:', LEFT(SHA2(?,256),40)),0)")) {
                legacy.setString(1, key);
                try (var row = legacy.executeQuery()) { assertTrue(row.next()); assertEquals(0, row.getInt(1)); }
            }
        }
        try (var acquired = second.tryAcquire("flow:entity", key).orElseThrow()) { assertNotNull(acquired); }
    }

    @Test
    void concurrentPublishersHaveExactlyOneWinner() throws Exception {
        var source = source();
        String key = UUID.randomUUID().toString();
        var executor = Executors.newFixedThreadPool(6);
        var attempted = new CountDownLatch(6);
        var release = new CountDownLatch(1);
        var winners = new AtomicInteger();
        var tasks = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 6; i++) tasks.add(executor.submit(() -> {
                var acquired = new JdbcDatabaseLock(source, DatabaseVendor.MYSQL).tryAcquire("flow:entity", key);
                if (acquired.isPresent()) winners.incrementAndGet();
                attempted.countDown();
                try { release.await(15, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                finally { acquired.ifPresent(com.workflow.core.database.port.DatabaseLockPort.Handle::close); }
            }));
            assertTrue(attempted.await(10, TimeUnit.SECONDS));
            assertEquals(1, winners.get());
        } finally {
            release.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
        try (var acquired = new JdbcDatabaseLock(source, DatabaseVendor.MYSQL).tryAcquire("flow:entity", key).orElseThrow()) {
            assertNotNull(acquired);
        }
    }
}
