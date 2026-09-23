package com.workflow.database;

import com.workflow.integration.database.api.DatabaseJdbcProfiles;
import com.workflow.core.database.InitializedDriverDataSource;
import com.workflow.core.database.JdbcDatabaseClock;

import com.workflow.integration.database.api.*;
import com.workflow.integration.database.dialect.MySqlSchemaDdlDialect;
import com.workflow.entity.data.infrastructure.schema.JdbcSchemaChangeQueue;
import com.workflow.migration.schema.JdbcSchemaChangeWorker;
import com.workflow.integration.database.schema.*;
import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.migration.schema.SchemaDdlReplayVerifier;
import com.workflow.core.database.lock.JdbcDatabaseLock;
import com.workflow.core.database.port.DatabaseLockPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 随机独立队列表与目标表验证真实并发、崩溃恢复和租约隔离，不消费现有发布队列。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlSchemaQueueDatabaseTest {
    @Test
    void busyFirstPageDoesNotStarveLaterRequests() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.jdbc.execute(fixture.ddl());
            List<DatabaseLockPort.Handle> held = new ArrayList<>();
            try {
                var locks = new JdbcDatabaseLock(fixture.source, DatabaseVendor.MYSQL);
                for (int i = 0; i < 20; i++) {
                    String id = fixture.queue.enqueue("ALTER TABLE `" + fixture.target + "` ADD COLUMN `busy_" + i + "` INT");
                    held.add(locks.tryAcquire("flow:schema-job", fixture.dialect.quoteIdentifier(fixture.queueTable) + ":" + id).orElseThrow());
                }
                String ready = fixture.queue.enqueue("ALTER TABLE `" + fixture.target + "` ADD COLUMN `ready` INT");
                assertTrue(fixture.worker(fixture.source).processNext());
                assertEquals("APPLIED", fixture.queue.state(ready).orElseThrow().status());
                assertEquals(20, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM " + fixture.queueTable + " WHERE status='PENDING'", Integer.class));
            } finally {
                for (var handle : held) handle.close();
            }
        }
    }

    @Test
    void concurrentProducersDeduplicateAndOnlyOneWorkerAppliesTheRequest() throws Exception {
        try (var fixture = new Fixture()) {
            var pool = Executors.newFixedThreadPool(6);
            try {
                var gate = new CountDownLatch(1);
                var results = new ArrayList<Future<String>>();
                for (int i = 0; i < 6; i++) results.add(pool.submit(() -> { gate.await(); return fixture.queue.enqueue(fixture.ddl()); }));
                gate.countDown();
                var ids = new HashSet<String>();
                for (var result : results) ids.add(result.get(15, TimeUnit.SECONDS));
                assertEquals(1, ids.size());
                String id = ids.iterator().next();
                var work = new ArrayList<Future<Boolean>>();
                for (int i = 0; i < 6; i++) work.add(pool.submit(() -> fixture.worker(fixture.source).processNext()));
                int completed = 0;
                for (var result : work) if (result.get(15, TimeUnit.SECONDS)) completed++;
                assertEquals(1, completed);
                assertEquals("APPLIED", fixture.queue.state(id).orElseThrow().status());
                assertEquals(1, fixture.jdbc.queryForObject("SELECT attempt FROM " + fixture.queueTable + " WHERE id=?", Integer.class, id));
                assertTrue(new SchemaDdlReplayVerifier(fixture.jdbc, fixture.dialect).isApplied(fixture.ddl()));
                // 完成后允许重新提交同一 DDL，活跃去重不是永久幂等键。
                assertNotEquals(id, fixture.queue.enqueue(fixture.ddl()));
            } finally { pool.shutdownNow(); }
        }
    }

    @Test
    void replayChecksTheColumnDefinitionAndRejectsSameNameWithDifferentType() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.jdbc.execute(fixture.ddl());
            var column = new SchemaColumn("memo", SchemaType.string(123), true, SchemaDefault.literal("NULL"), "重试; 文本", false);
            String ddl = fixture.dialect.addColumn(fixture.target, column).get(0);
            fixture.jdbc.execute(ddl); // 模拟 DDL 已提交，但 worker 尚未 ACK 就崩溃。
            String replay = fixture.queue.enqueue(ddl);
            assertTrue(fixture.worker(fixture.source).processNext());
            assertEquals("APPLIED", fixture.queue.state(replay).orElseThrow().status());

            String conflicting = fixture.dialect.addColumn(fixture.target,
                    new SchemaColumn("memo", SchemaType.of(SchemaType.Kind.INTEGER), true, SchemaDefault.none(), null, false)).get(0);
            String failed = fixture.queue.enqueue(conflicting);
            for (int attempt = 1; attempt <= 5; attempt++) {
                fixture.makeDue(failed);
                assertTrue(fixture.worker(fixture.source).processNext());
                assertEquals(attempt < 5 ? "PENDING" : "FAILED", fixture.queue.state(failed).orElseThrow().status());
            }
            assertNull(fixture.jdbc.queryForObject("SELECT active_hash FROM " + fixture.queueTable + " WHERE id=?", String.class, failed));
            assertEquals(123L, new JdbcSchemaMetadata(fixture.jdbc, fixture.dialect).columns(fixture.target).stream()
                    .filter(c -> c.name().equals("memo")).findFirst().orElseThrow().length());
        }
    }

    @Test
    void createIfNotExistsDoesNotAcceptWrongStructureAndIndexOrderIsVerified() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.jdbc.execute("CREATE TABLE " + fixture.target + " (id VARCHAR(64) NOT NULL PRIMARY KEY, code VARCHAR(64))");
            String request = fixture.queue.enqueue(fixture.ddl());
            assertTrue(fixture.worker(fixture.source).processNext());
            assertEquals("PENDING", fixture.queue.state(request).orElseThrow().status());
            assertTrue(fixture.queue.state(request).orElseThrow().error().contains("does not match"));
            fixture.jdbc.execute("CREATE INDEX idx_pair ON " + fixture.target + " (code,id)");
            var verifier = new SchemaDdlReplayVerifier(fixture.jdbc, fixture.dialect);
            assertFalse(verifier.isApplied("CREATE INDEX `idx_pair` ON `" + fixture.target + "` (`id`,`code`)"));
            assertTrue(verifier.isApplied("CREATE INDEX `idx_pair` ON `" + fixture.target + "` (`code`,`id`)"));
            assertFalse(verifier.isApplied("ALTER TABLE `" + fixture.target + "` ADD COLUMN `code` VARCHAR(65)"));
            assertTrue(verifier.isApplied("ALTER TABLE `" + fixture.target + "` DROP COLUMN `missing`"));
        }
    }

    @Test
    void expiredLastAttemptTerminatesAndExpiredEarlierAttemptIsReclaimed() throws Exception {
        try (var fixture = new Fixture()) {
            String id = fixture.queue.enqueue(fixture.ddl());
            var past = fixture.clock.utcNow().minusMinutes(10);
            fixture.jdbc.update("UPDATE " + fixture.queueTable + " SET status='RUNNING', owner_id='dead', attempt=1, lease_token=4, lease_until=? WHERE id=?", past, id);
            assertTrue(fixture.worker(fixture.source).processNext());
            assertEquals("APPLIED", fixture.queue.state(id).orElseThrow().status());
            assertEquals(5L, fixture.jdbc.queryForObject("SELECT lease_token FROM " + fixture.queueTable + " WHERE id=?", Long.class, id));

            String exhausted = fixture.queue.enqueue("ALTER TABLE `" + fixture.target + "` ADD COLUMN `never_applied` INT");
            fixture.jdbc.update("UPDATE " + fixture.queueTable + " SET status='RUNNING', owner_id='dead', attempt=5, lease_token=5, lease_until=? WHERE id=?", past, exhausted);
            assertTrue(fixture.worker(fixture.source).processNext());
            assertEquals("FAILED", fixture.queue.state(exhausted).orElseThrow().status());
            String lostAck = fixture.queue.enqueue(fixture.ddl());
            fixture.jdbc.update("UPDATE " + fixture.queueTable + " SET status='RUNNING', owner_id='dead', attempt=5, lease_token=5, lease_until=? WHERE id=?", past, lostAck);
            assertTrue(fixture.worker(fixture.source).processNext());
            assertEquals("APPLIED", fixture.queue.state(lostAck).orElseThrow().status());
        }
    }

    @Test
    void expiredLeaseCannotOverlapRunningDdlAndOldWorkerCannotAcknowledgeNewOwner() throws Exception {
        try (var fixture = new Fixture()) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            DataSource blocking = new DelegatingDataSource(fixture.source) {
                @Override public Connection getConnection() throws SQLException {
                    Connection actual = super.getConnection();
                    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        Object result = invoke(actual, method, args);
                        if (!method.getName().equals("createStatement")) return result;
                        return Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{Statement.class}, (statementProxy, operation, parameters) -> {
                            if (operation.getName().equals("execute") && parameters != null && fixture.ddl().equals(parameters[0])) {
                                entered.countDown();
                                if (!release.await(15, TimeUnit.SECONDS)) throw new SQLException("Test DDL gate timed out");
                            }
                            return invoke(result, operation, parameters);
                        });
                    });
                }
            };
            String id = fixture.queue.enqueue(fixture.ddl());
            var pool = Executors.newSingleThreadExecutor();
            try {
                var first = pool.submit(() -> fixture.worker(blocking).processNext());
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                var past = fixture.clock.utcNow().minusMinutes(10);
                fixture.jdbc.update("UPDATE " + fixture.queueTable + " SET lease_until=? WHERE id=?", past, id);
                assertFalse(fixture.worker(fixture.source).processNext(), "活跃会话锁必须阻止超时后的重叠 DDL");
                // 模拟已有旧版本 worker/管理员接管了租约，确认旧 token 不能覆盖新持有者。
                fixture.jdbc.update("UPDATE " + fixture.queueTable + " SET owner_id='new-owner', lease_token=lease_token+1 WHERE id=?", id);
                release.countDown();
                assertTrue(first.get(15, TimeUnit.SECONDS));
                assertEquals("RUNNING", fixture.queue.state(id).orElseThrow().status());
                assertEquals("new-owner", fixture.jdbc.queryForObject("SELECT owner_id FROM " + fixture.queueTable + " WHERE id=?", String.class, id));
                assertTrue(fixture.worker(fixture.source).processNext());
                assertEquals("APPLIED", fixture.queue.state(id).orElseThrow().status());
            } finally { release.countDown(); pool.shutdownNow(); }
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
    }

    private static final class Fixture implements AutoCloseable {
        final InitializedDriverDataSource source = new InitializedDriverDataSource(System.getenv("FLOW_MYSQL_TEST_URL"), System.getenv("FLOW_MYSQL_TEST_USER"),
                System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null, DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.MYSQL));
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final MySqlSchemaDdlDialect dialect = new MySqlSchemaDdlDialect();
        final JdbcDatabaseClock clock = new JdbcDatabaseClock(jdbc, DatabaseVendor.MYSQL);
        final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        final String queueTable = "biz_queue_test_" + suffix;
        final String target = "biz_queued_" + suffix;
        final JdbcSchemaChangeQueue queue = new JdbcSchemaChangeQueue(jdbc, dialect, queueTable);
        Fixture() {
            jdbc.execute("CREATE TABLE " + queueTable + " (id VARCHAR(36) PRIMARY KEY, ddl_hash CHAR(64) NOT NULL, active_hash CHAR(64) UNIQUE,"
                    + " ddl_statement TEXT NOT NULL, status VARCHAR(20) NOT NULL, attempt INT NOT NULL, owner_id VARCHAR(128), lease_token BIGINT NOT NULL,"
                    + " lease_until DATETIME(6), next_attempt_at DATETIME(6) NOT NULL, create_time DATETIME(6) NOT NULL, update_time DATETIME(6) NOT NULL,"
                    + " completed_time DATETIME(6), last_error VARCHAR(1000)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
        String ddl() {
            return dialect.createTable(new SchemaTable(target, List.of(
                    new SchemaColumn("id", SchemaType.string(64), false, SchemaDefault.none(), "标识", false),
                    new SchemaColumn("code", SchemaType.string(64), true, SchemaDefault.literal("值'\\甲;"), "代码", false)),
                    List.of("id"), List.of(new SchemaIndex("idx_code", List.of("code"), false)), "队列测试", true)).get(0);
        }
        JdbcSchemaChangeWorker worker(DataSource dataSource) { return new JdbcSchemaChangeWorker(dataSource, dialect, "test-" + UUID.randomUUID(), queueTable); }
        void makeDue(String id) { jdbc.update("UPDATE " + queueTable + " SET next_attempt_at=? WHERE id=?", clock.utcNow().minusSeconds(1), id); }
        @Override public void close() {
            try { jdbc.execute("DROP TABLE IF EXISTS " + target); }
            finally { jdbc.execute("DROP TABLE IF EXISTS " + queueTable); }
        }
    }
}
