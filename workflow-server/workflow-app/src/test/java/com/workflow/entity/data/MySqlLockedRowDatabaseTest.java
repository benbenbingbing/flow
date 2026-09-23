package com.workflow.entity.data;

import org.springframework.jdbc.core.JdbcTemplate;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.JdbcWriteAttempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.database.JdbcLockedRow;
import com.workflow.contracts.entity.mutation.*;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository.GateKey;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper.EntityFormUniqueValueGateMapper;
import com.workflow.entity.version.application.*;
import com.workflow.entity.version.infrastructure.persistence.mapper.*;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedSessionExchangeMapper;
import com.workflow.integration.database.api.*;
import com.workflow.outbox.api.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.Harness;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;

/** MySQL 实库验证占位行、业务事务行锁与三个调用方的并发边界，所有表均随机命名。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlLockedRowDatabaseTest {
    private JdbcLockedRow locks(Harness h) { return new JdbcLockedRow(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL)); }

    @Test
    void requiresTheCallingDataSourcesTransactionBeforeWriting() {
        try (var f = new Fixture()) {
            f.table("locked_row", "id VARCHAR(64) PRIMARY KEY");
            var h = new Harness(f); var locks = locks(h);
            assertThrows(IllegalStateException.class, () -> locks.ensureAndLock("locked_row", Map.of("id", "new"), List.of("id")));
            var unrelated = new TransactionTemplate(new DataSourceTransactionManager(f.source));
            assertThrows(IllegalStateException.class, () -> unrelated.executeWithoutResult(status ->
                    locks.ensureAndLock("locked_row", Map.of("id", "new"), List.of("id"))));
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM locked_row", Integer.class));
        }
    }

    @Test
    void existingValuesArePreservedAndLockSurvivesUntilCommit() {
        try (var f = new Fixture()) {
            String table = f.table("locked_row", "id VARCHAR(64) PRIMARY KEY, amount INT NOT NULL");
            var h = new Harness(f); var locks = locks(h);
            h.jdbc.update("INSERT INTO locked_row VALUES ('same',7),('other',0)");
            h.tx.executeWithoutResult(status -> {
                locks.ensureAndLock("locked_row", Map.of("id", "same", "amount", 0), List.of("id"));
                assertEquals(7, h.jdbc.queryForObject("SELECT amount FROM locked_row WHERE id='same'", Integer.class));
                try (var connection = f.source.getConnection(); var statement = connection.createStatement()) {
                    statement.execute("SET SESSION innodb_lock_wait_timeout=1");
                    assertEquals(1, statement.executeUpdate("UPDATE " + table + " SET amount=amount+1 WHERE id='other'"));
                    var error = assertThrows(SQLException.class,
                            () -> statement.executeUpdate("UPDATE " + table + " SET amount=amount+1 WHERE id='same'"));
                    assertEquals(1205, error.getErrorCode());
                } catch (SQLException error) { throw new IllegalStateException(error); }
            });
            assertEquals(1, h.jdbc.update("UPDATE locked_row SET amount=amount+1 WHERE id='same'"));
            assertEquals(8, h.jdbc.queryForObject("SELECT amount FROM locked_row WHERE id='same'", Integer.class));
        }
    }

    @Test
    void failedTransactionRemovesItsNewPlaceholderAndRestoresUpdates() {
        try (var f = new Fixture()) {
            f.table("locked_row", "id VARCHAR(64) PRIMARY KEY, amount INT NOT NULL");
            var h = new Harness(f); var locks = locks(h);
            h.jdbc.update("INSERT INTO locked_row VALUES ('existing',4)");
            h.tx.executeWithoutResult(status -> {
                locks.ensureAndLock("locked_row", Map.of("id", "new", "amount", 0), List.of("id"));
                locks.ensureAndLock("locked_row", Map.of("id", "existing", "amount", 0), List.of("id"));
                h.jdbc.update("UPDATE locked_row SET amount=9 WHERE id='existing'"); status.setRollbackOnly();
            });
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM locked_row", Integer.class));
            assertEquals(4, h.jdbc.queryForObject("SELECT amount FROM locked_row WHERE id='existing'", Integer.class));
            h.tx.executeWithoutResult(status -> locks.ensureAndLock("locked_row", Map.of("id", "new", "amount", 3), List.of("id")));
            assertEquals(3, h.jdbc.queryForObject("SELECT amount FROM locked_row WHERE id='new'", Integer.class));
        }
    }

    @Test
    void unrelatedUniqueConflictAndMultipleMatchesCannotGrantTheWrongLock() {
        try (var f = new Fixture()) {
            f.table("locked_row", "id VARCHAR(64) PRIMARY KEY, label VARCHAR(64) UNIQUE");
            f.table("bad_row", "id VARCHAR(64)");
            var h = new Harness(f); var locks = locks(h);
            h.jdbc.update("INSERT INTO locked_row VALUES ('old','same')");
            assertThrows(DataAccessException.class, () -> h.tx.executeWithoutResult(status ->
                    locks.ensureAndLock("locked_row", Map.of("id", "new", "label", "same"), List.of("id"))));
            assertEquals("old", h.jdbc.queryForObject("SELECT id FROM locked_row", String.class));
            h.jdbc.update("INSERT INTO bad_row VALUES ('same'),('same')");
            assertThrows(DataAccessException.class, () -> h.tx.executeWithoutResult(status ->
                    locks.ensureAndLock("bad_row", Map.of("id", "same"), List.of("id"))));
            assertEquals(2, h.jdbc.queryForObject("SELECT COUNT(*) FROM bad_row", Integer.class));
        }
    }

    @Test
    void recoveredInsertConflictPreservesPriorWorkAndRequiresTheExactTargetRow() {
        try (var f = new Fixture()) {
            f.table("locked_row", "id VARCHAR(64) PRIMARY KEY, label VARCHAR(64) UNIQUE, amount INT NOT NULL");
            var h = new Harness(f);
            var mysql = DatabaseDialects.insert(DatabaseVendor.MYSQL);
            // 用真实 MySQL 重复 INSERT 触发执行器恢复分支，不以此代替其他产品的 MERGE 实测。
            var recoverable = new DatabaseInsertDialect() {
                public BoundSqlStatement insert(String table, Map<String, ?> values) { return mysql.insert(table, values); }
                public boolean isUniqueViolation(String state, int code) { return mysql.isUniqueViolation(state, code); }
                public DatabaseRowLockPlan rowLock(String table, Map<String, ?> values, List<String> keys) {
                    return new DatabaseRowLockPlan(insert(table, values), mysql.rowLock(table, values, keys).lock(), true);
                }
            };
            var locks = new JdbcLockedRow(h.jdbc, recoverable);
            h.jdbc.update("INSERT INTO locked_row VALUES ('existing','same',4)");
            h.tx.executeWithoutResult(status -> {
                h.jdbc.update("UPDATE locked_row SET amount=5 WHERE id='existing'");
                locks.ensureAndLock("locked_row", Map.of("id", "existing", "label", "same", "amount", 0), List.of("id"));
                assertEquals(5, h.jdbc.queryForObject("SELECT amount FROM locked_row WHERE id='existing'", Integer.class));
                assertThrows(DataAccessException.class, () -> locks.ensureAndLock("locked_row",
                        Map.of("id", "different", "label", "same", "amount", 0), List.of("id")));
                h.jdbc.update("UPDATE locked_row SET amount=6 WHERE id='existing'");
            });
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM locked_row", Integer.class));
            assertEquals(6, h.jdbc.queryForObject("SELECT amount FROM locked_row", Integer.class));
        }
    }

    @Test
    void versionServiceAllocatesDistinctNumbersAndNeverLowersHistoricalMinimum() throws Exception {
        try (var f = new Fixture()) {
            f.table("entity_record_version_counter", "entity_code VARCHAR(100), record_id VARCHAR(64), last_version_no INT NOT NULL, "
                    + "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(entity_code,record_id)");
            var h = new Harness(f, EntityRecordVersionCounterMapper.class); var locks = locks(h);
            var mapper = h.mapper(EntityRecordVersionCounterMapper.class);
            var versions = mock(EntityRecordVersionMapper.class); var snapshots = mock(EntityRecordSnapshotService.class);
            when(snapshots.capture(anyString(), anyString(), any(), anyBoolean())).thenReturn(
                    new EntityRecordSnapshotService.SnapshotCapture(Map.of("recordId", "record"), "hash", "release", 1));
            when(versions.findMaxVersionNo("asset", "record")).thenReturn(0);
            // 版本号分配使用真实服务/计数器 Mapper；无关快照与 Outbox 以替身隔离业务数据。
            var service = new EntityRecordVersionService(versions, snapshots, mock(OutboxPublisher.class), new ObjectMapper().findAndRegisterModules(),
                    mock(EntityVersionConfigurationService.class), mock(EntityVersionPolicyMatcher.class), mapper,
                    mock(EntityRecordVersionDatasetMapper.class), mock(EntityRecordVersionDatasetRowMapper.class),
                    mock(EntityDataDynamicService.class), mock(EntityAggregateWriter.class), locks,
                new JdbcWriteAttempt(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL)));
            var scenario = new EntityVersionPolicyMatcher.MatchedScenario("CHANGE", "变更", null, 100, null);
            var numbers = concurrent(6, index -> h.tx.execute(status -> {
                String key = "request-" + index;
                var context = EntityMutationContext.builder(EntityMutationSourceType.APPROVAL_TASK, "CHANGE", "变更")
                        .operator("user", "测试").trace(key, key).build();
                var command = new EntityMutationCommand(key, "asset", "record", EntityMutationOperationType.UPDATE, Map.of(), context);
                return service.createIfMatched(command, scenario, Map.of("id", "record"), false).getVersionNo();
            }));
            assertEquals(List.of(1, 2, 3, 4, 5, 6), numbers.stream().sorted().toList());
            h.tx.executeWithoutResult(status -> {
                locks.ensureAndLock("entity_record_version_counter", Map.of("entity_code", "asset", "record_id", "record", "last_version_no", 0),
                        List.of("entity_code", "record_id"));
                mapper.raiseMinimum("asset", "record", 20);
                assertEquals(20, mapper.lock("asset", "record").getLastVersionNo());
                mapper.raiseMinimum("asset", "record", 3);
                assertEquals(20, mapper.lock("asset", "record").getLastVersionNo());
            });
            assertEquals(20, h.jdbc.queryForObject("SELECT last_version_no FROM entity_record_version_counter", Integer.class));
        }
    }

    @Test
    void stableGateOrderSerializesOppositeInputOrders() throws Exception {
        try (var f = new Fixture()) {
            f.table("entity_form_unique_value_gate", "scope_key VARCHAR(255), value_hash CHAR(64), PRIMARY KEY(scope_key,value_hash)");
            f.table("gate_effect", "id INT PRIMARY KEY, hits INT NOT NULL");
            var h = new Harness(f, EntityFormUniqueValueGateMapper.class);
            h.jdbc.update("INSERT INTO gate_effect VALUES (1,0)");
            var gates = new EntityFormUniqueValueGateRepository(h.mapper(EntityFormUniqueValueGateMapper.class), locks(h));
            var first = new GateKey("ENTITY:asset:code", "a".repeat(64)); var second = new GateKey("ENTITY:asset:name", "b".repeat(64));
            concurrent(6, index -> h.tx.execute(status -> {
                gates.lockAll(index % 2 == 0 ? List.of(first, second, first) : List.of(second, first));
                int before = h.jdbc.queryForObject("SELECT hits FROM gate_effect", Integer.class);
                h.jdbc.update("UPDATE gate_effect SET hits=? WHERE id=1", before + 1); return true;
            }));
            assertEquals(2, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_form_unique_value_gate", Integer.class));
            assertEquals(6, h.jdbc.queryForObject("SELECT hits FROM gate_effect", Integer.class));
        }
    }

    @Test
    void sessionCounterInitializationCannotResetItsLimitOrVersion() throws Exception {
        try (var f = new Fixture()) {
            f.table("embed_session_counter", "grant_id VARCHAR(64), flow_user_id VARCHAR(64), active_count INT NOT NULL, lock_version BIGINT NOT NULL, "
                    + "create_time DATETIME(6), update_time DATETIME(6), PRIMARY KEY(grant_id,flow_user_id)");
            var h = new Harness(f, EmbedSessionExchangeMapper.class); var locks = locks(h);
            var mapper = h.mapper(EmbedSessionExchangeMapper.class); LocalDateTime now = LocalDateTime.parse("2026-09-22T02:00:00");
            var values = Map.<String, Object>of("grant_id", "grant", "flow_user_id", "user", "active_count", 0,
                    "lock_version", 0L, "create_time", now, "update_time", now);
            var increments = concurrent(6, index -> h.tx.execute(status -> {
                locks.ensureAndLock("embed_session_counter", values, List.of("grant_id", "flow_user_id"));
                assertNotNull(mapper.lockCounter("grant", "user")); return mapper.incrementCounter("grant", "user", 3, now);
            }));
            assertEquals(3, increments.stream().mapToInt(Integer::intValue).sum());
            assertEquals(3, h.jdbc.queryForObject("SELECT active_count FROM embed_session_counter", Integer.class));
            assertEquals(3L, h.jdbc.queryForObject("SELECT lock_version FROM embed_session_counter", Long.class));
        }
    }
}
