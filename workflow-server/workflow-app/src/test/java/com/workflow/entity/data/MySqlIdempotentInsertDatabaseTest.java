package com.workflow.entity.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.database.jdbc.JdbcIdempotentInsert;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdempotencyClaim;
import com.workflow.embed.infrastructure.persistence.adapter.MyBatisEmbedIdempotencyAdapter;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedIdempotencyMapper;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.status.application.ProcessStatusSyncOutboxHandler;
import com.workflow.process.status.application.ProcessStatusSyncPayload;
import com.workflow.process.status.infrastructure.persistence.mapper.ProcessStatusSyncMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 真实 MySQL 的唯一约束、保存点和业务事务；仅使用随机表与生产 Mapper，不访问业务表。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlIdempotentInsertDatabaseTest {
    private static final Instant NOW = Instant.parse("2026-09-22T01:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.parse("2026-09-22T01:00:00");
    private static final String HASH = "a".repeat(64);

    @Test
    void onlyUniqueViolationsAreIgnoredAndValuesStayBound() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String table = f.table("insert_case", "id VARCHAR(64) PRIMARY KEY, logical_key VARCHAR(64) UNIQUE, "
                    + "parent_id VARCHAR(64), amount INT NOT NULL CHECK(amount >= 0), note VARCHAR(100)");
            f.jdbc.execute("ALTER TABLE " + table + " ADD FOREIGN KEY(parent_id) REFERENCES " + table + "(id)");
            var h = new Harness(f);
            String value = "中文 '); DROP TABLE victim; --";
            var row = values("id", "first", "logical_key", "same", "amount", 1, "note", value, "parent_id", null);
            assertTrue(h.inserts.insertIfAbsent("insert_case", row));
            assertFalse(h.inserts.insertIfAbsent("insert_case", row));
            assertFalse(h.inserts.insertIfAbsent("insert_case", values("id", "second", "logical_key", "same", "amount", 2)));
            assertEquals(value, h.jdbc.queryForObject("SELECT note FROM insert_case WHERE id='first'", String.class));
            assertNull(h.jdbc.queryForObject("SELECT parent_id FROM insert_case WHERE id='first'", String.class));
            assertThrows(DataAccessException.class, () -> h.inserts.insertIfAbsent("insert_case", values("id", "null", "amount", null)));
            assertThrows(DataAccessException.class, () -> h.inserts.insertIfAbsent("insert_case", values("id", "check", "amount", -1)));
            assertThrows(DataAccessException.class, () -> h.inserts.insertIfAbsent("insert_case", values("id", "foreign", "amount", 1, "parent_id", "absent")));
            assertThrows(DataAccessException.class, () -> h.inserts.insertIfAbsent("insert_case", values("id", "x".repeat(65), "amount", 1)));
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM insert_case", Integer.class));
        }
    }

    @Test
    void duplicateSavepointPreservesEarlierWorkAndBusinessRollbackDoesNotCommitTheClaim() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            f.table("insert_case", "id VARCHAR(64) PRIMARY KEY, note VARCHAR(100)");
            var h = new Harness(f);
            h.inserts.insertIfAbsent("insert_case", values("id", "existing", "note", "old"));
            h.tx.executeWithoutResult(status -> {
                h.jdbc.update("UPDATE insert_case SET note='before' WHERE id='existing'");
                assertFalse(h.inserts.insertIfAbsent("insert_case", values("id", "existing", "note", "replace")));
                assertTrue(h.inserts.insertIfAbsent("insert_case", values("id", "after", "note", "after")));
            });
            assertEquals("before", h.jdbc.queryForObject("SELECT note FROM insert_case WHERE id='existing'", String.class));
            assertEquals(2, h.jdbc.queryForObject("SELECT COUNT(*) FROM insert_case", Integer.class));
            h.tx.executeWithoutResult(status -> {
                assertTrue(h.inserts.insertIfAbsent("insert_case", values("id", "rolled-back")));
                status.setRollbackOnly();
            });
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM insert_case WHERE id='rolled-back'", Integer.class));
            assertTrue(h.inserts.insertIfAbsent("insert_case", values("id", "rolled-back")));
        }
    }

    @Test
    void concurrentTransactionsHaveOneInsertWinner() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            f.table("insert_case", "id VARCHAR(64) PRIMARY KEY, note VARCHAR(100)");
            var h = new Harness(f);
            List<Boolean> results = concurrent(6, index -> h.tx.execute(status ->
                    h.inserts.insertIfAbsent("insert_case", values("id", "same", "note", "worker-" + index))));
            assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM insert_case", Integer.class));
        }
    }

    @Test
    void insertDispositionDoesNotDependOnMysqlAffectedRowsOption() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String table = f.table("insert_case", "id VARCHAR(64) PRIMARY KEY");
            for (boolean affected : new boolean[]{false, true}) {
                String url = System.getenv("FLOW_MYSQL_TEST_URL");
                if (url.matches(".*[?&]useAffectedRows=[^&]*.*")) {
                    url = url.replaceAll("([?&])useAffectedRows=[^&]*", "$1useAffectedRows=" + affected);
                } else url += (url.contains("?") ? "&" : "?") + "useAffectedRows=" + affected;
                var source = new com.workflow.core.database.jdbc.InitializedDriverDataSource(url,
                        System.getenv("FLOW_MYSQL_TEST_USER"), System.getenv("FLOW_MYSQL_TEST_PASSWORD"), null,
                        com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles.connectionInitSql(DatabaseVendor.MYSQL));
                var insert = new JdbcIdempotentInsert(new JdbcTemplate(source), DatabaseDialects.insert(DatabaseVendor.MYSQL));
                assertTrue(insert.insertIfAbsent(table, Map.of("id", "row-" + affected)));
                assertFalse(insert.insertIfAbsent(table, Map.of("id", "row-" + affected)));
            }
        }
    }

    @Test
    void embedConcurrentClaimReplayAndFenceStayAtomic() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            embedTable(f);
            var h = new Harness(f, EmbedIdempotencyMapper.class);
            var adapter = new MyBatisEmbedIdempotencyAdapter(h.mapper(EmbedIdempotencyMapper.class), new ObjectMapper(), h.inserts);
            var claims = concurrent(6, index -> h.tx.execute(status -> adapter.claim("app", "EMBED_RECORD_CREATE", "key", HASH, NOW)));
            assertEquals(1, claims.stream().filter(EmbedIdempotencyClaim::acquired).count());
            assertEquals(5, claims.stream().filter(EmbedIdempotencyClaim::processing).count());
            var first = claims.stream().filter(EmbedIdempotencyClaim::acquired).findFirst().orElseThrow();
            EmbedException mismatch = assertThrows(EmbedException.class, () -> h.tx.execute(status ->
                    adapter.claim("app", "EMBED_RECORD_CREATE", "key", "b".repeat(64), NOW)));
            assertEquals(409, mismatch.getStatus());
            h.tx.executeWithoutResult(status -> adapter.failRetryable(first, NOW));
            var second = h.tx.execute(status -> adapter.claim("app", "EMBED_RECORD_CREATE", "key", HASH, NOW));
            assertTrue(second.acquired());
            assertEquals(first.fencingToken() + 1, second.fencingToken());
            h.tx.executeWithoutResult(status -> adapter.failRetryable(first, NOW));
            assertThrows(IllegalStateException.class, () -> h.tx.executeWithoutResult(status ->
                    adapter.completeInBusinessTransaction(first, "ENTITY", "record", 201, "{}", NOW)));
            h.tx.executeWithoutResult(status -> {
                adapter.completeInBusinessTransaction(second, "ENTITY", "record", 201, "{}", NOW);
                status.setRollbackOnly();
            });
            assertTrue(h.tx.execute(status -> adapter.claim("app", "EMBED_RECORD_CREATE", "key", HASH, NOW)).processing());
            h.tx.executeWithoutResult(status -> adapter.completeInBusinessTransaction(second, "ENTITY", "record", 201, "{}", NOW));
            var replay = h.tx.execute(status -> adapter.claim("app", "EMBED_RECORD_CREATE", "key", HASH, NOW));
            assertTrue(replay.replay());
            assertEquals("record", replay.resourceId());
            assertEquals(201, replay.responseStatus());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM integration_idempotency_record", Integer.class));
        }
    }

    @Test
    void processSyncDeduplicatesBusinessKeyAndRollsBackAuditWithSideEffects() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            f.table("process_status_sync_event", "id VARCHAR(64) PRIMARY KEY, process_instance_id VARCHAR(64) NOT NULL, "
                    + "event_type VARCHAR(50) NOT NULL, event_sequence VARCHAR(128) NOT NULL, entity_code VARCHAR(63) NOT NULL, "
                    + "entity_record_id VARCHAR(64) NOT NULL, target_status VARCHAR(100), status_category VARCHAR(30), state VARCHAR(20) NOT NULL, "
                    + "applied_at DATETIME(6), create_time DATETIME(6) NOT NULL, update_time DATETIME(6) NOT NULL, "
                    + "UNIQUE(process_instance_id,event_type,event_sequence)");
            f.table("side_effect", "id INT PRIMARY KEY, hits INT NOT NULL");
            var h = new Harness(f, ProcessStatusSyncMapper.class);
            h.jdbc.update("INSERT INTO side_effect VALUES (1,0)");
            var entity = mock(EntityRecordPort.class);
            var links = mock(EntityProcessLinkMapper.class);
            when(links.updateActiveStatus(anyString(), anyString())).thenReturn(1);
            var failOnce = new AtomicBoolean(true);
            doAnswer(call -> {
                h.jdbc.update("UPDATE side_effect SET hits=hits+1 WHERE id=1");
                if (failOnce.getAndSet(false)) throw new IllegalStateException("模拟实体更新失败");
                return null;
            }).when(entity).updateStatus(anyString(), anyString(), anyString());
            var json = new ObjectMapper();
            var handler = new ProcessStatusSyncOutboxHandler(json, h.mapper(ProcessStatusSyncMapper.class), links, entity, h.inserts, () -> LOCAL_NOW);
            var payload = new ProcessStatusSyncPayload("process", "TASK_COMPLETED", "task", "expense", "record", "REVIEW", null, null);
            String body = json.writeValueAsString(payload);
            assertThrows(IllegalStateException.class, () -> h.tx.executeWithoutResult(status -> handle(handler, event("first", body))));
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM process_status_sync_event", Integer.class));
            assertEquals(0, h.jdbc.queryForObject("SELECT hits FROM side_effect", Integer.class));
            concurrent(6, index -> {
                h.tx.executeWithoutResult(status -> handle(handler, event("event-" + index, body)));
                return true;
            });
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM process_status_sync_event", Integer.class));
            assertEquals(1, h.jdbc.queryForObject("SELECT hits FROM side_effect", Integer.class));
            assertEquals("APPLIED", h.jdbc.queryForObject("SELECT state FROM process_status_sync_event", String.class));
            assertEquals(LOCAL_NOW, h.jdbc.queryForObject("SELECT applied_at FROM process_status_sync_event", LocalDateTime.class));
        }
    }

    /** 保留生产的逻辑唯一键、状态/响应约束及二进制键比较；省略与本测试无关的外部应用表。 */
    private static void embedTable(MySqlRuntimePaginationDatabaseTest.Fixture f) {
        f.table("integration_idempotency_record", "id VARCHAR(64) COLLATE utf8mb4_bin PRIMARY KEY, "
                + "application_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, operation VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, "
                + "idempotency_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL, request_hash CHAR(64) NOT NULL CHECK(request_hash REGEXP '^[0-9a-f]{64}$'), "
                + "status VARCHAR(24) NOT NULL CHECK(status IN ('PROCESSING','SUCCEEDED','FAILED_RETRYABLE')), "
                + "resource_type VARCHAR(64), resource_id VARCHAR(128), response_status SMALLINT, response_body LONGTEXT, "
                + "fencing_token BIGINT NOT NULL CHECK(fencing_token > 0), processing_started_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL, "
                + "create_time DATETIME(6) NOT NULL, update_time DATETIME(6) NOT NULL, UNIQUE(application_id,operation,idempotency_key), "
                + "CHECK((status='SUCCEEDED' AND resource_type IS NOT NULL AND resource_id IS NOT NULL AND response_status BETWEEN 200 AND 299 "
                + "AND response_body IS NOT NULL AND JSON_VALID(response_body)) OR (status<>'SUCCEEDED' AND response_status IS NULL AND response_body IS NULL))");
    }

    private static OutboxEvent event(String id, String body) {
        return new OutboxEvent(id, "PROCESS_STATUS_SYNC", "key", "PROCESS_INSTANCE", "process", body, 0, LOCAL_NOW);
    }

    private static void handle(ProcessStatusSyncOutboxHandler handler, OutboxEvent event) {
        try { handler.handle(event); }
        catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static Map<String, Object> values(Object... pairs) {
        var values = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    /** 所有参与者一起开始；每个回调在线程自己的真实事务中执行，不共享连接。 */
    static <T> List<T> concurrent(int count, IntFunction<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<T>>();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return action.apply(index); }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS)); }
    }

    static final class Harness {
        final JdbcTemplate jdbc;
        final JdbcIdempotentInsert inserts;
        final TransactionTemplate tx;
        final SqlSessionTemplate session;

        Harness(MySqlRuntimePaginationDatabaseTest.Fixture fixture, Class<?>... mappers) {
            DataSource source = fixture.isolatedDataSource();
            jdbc = new JdbcTemplate(source);
            inserts = new JdbcIdempotentInsert(jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL));
            tx = new TransactionTemplate(new DataSourceTransactionManager(source));
            tx.setTimeout(20);
            var config = new Configuration(); config.setDatabaseId("MYSQL"); config.setMapUnderscoreToCamelCase(true);
            for (var mapper : mappers) config.addMapper(mapper);
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
            try { session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }

        <T> T mapper(Class<T> type) { return session.getMapper(type); }
    }
}
