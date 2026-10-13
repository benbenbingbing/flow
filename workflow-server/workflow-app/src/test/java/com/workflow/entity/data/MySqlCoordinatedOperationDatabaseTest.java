package com.workflow.entity.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.bootstrap.DatabaseBootstrapJobCoordinator;
import com.workflow.core.database.jdbc.JdbcDatabaseClock;
import com.workflow.core.database.jdbc.JdbcLockedRow;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.definition.application.EntitySchemaOperationService;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.Harness;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 启动任务与结构操作的真实 MySQL 事务验证；随机表隔离，结构计划只保存而不执行 DDL。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlCoordinatedOperationDatabaseTest {
    @Test
    void bootstrapRunsEachVersionOnceAcrossNodesAndPreservesCompletedWork() throws Exception {
        try (var f = new Fixture()) {
            bootstrapTables(f);
            var h = new Harness(f);
            h.jdbc.update("INSERT INTO bootstrap_effect VALUES (1,0)");
            var coordinator = bootstrap(h);
            var results = concurrent(6, index -> coordinator.executeOnce("catalog", 1, () -> {
                h.jdbc.update("UPDATE bootstrap_effect SET hits=hits+1 WHERE id=1"); return "done";
            }));
            assertEquals(1, results.stream().filter(Optional::isPresent).count());
            assertEquals(1, h.jdbc.queryForObject("SELECT hits FROM bootstrap_effect", Integer.class));
            assertEquals(Optional.of("v2"), coordinator.executeOnce("catalog", 2, () -> {
                h.jdbc.update("UPDATE bootstrap_effect SET hits=hits+1 WHERE id=1"); return "v2";
            }));
            assertTrue(coordinator.executeOnce("catalog", 1, () -> fail("旧版本不得再次执行")).isEmpty());
            assertEquals(2, h.jdbc.queryForObject("SELECT completed_version FROM workflow_bootstrap_job", Integer.class));
            assertEquals(2, h.jdbc.queryForObject("SELECT hits FROM bootstrap_effect", Integer.class));
            var createdAt = h.jdbc.queryForObject("SELECT create_time FROM workflow_bootstrap_job", LocalDateTime.class);
            var completedAt = h.jdbc.queryForObject("SELECT completed_at FROM workflow_bootstrap_job", LocalDateTime.class);
            var utc = clock(h).utcNow();
            assertFalse(completedAt.isBefore(createdAt));
            assertFalse(completedAt.isAfter(utc));
            assertTrue(completedAt.isAfter(utc.minusMinutes(1)));
        }
    }

    @Test
    void failedBootstrapRollsBackBothFirstClaimAndVersionUpgrade() {
        try (var f = new Fixture()) {
            bootstrapTables(f); var h = new Harness(f); var coordinator = bootstrap(h);
            h.jdbc.update("INSERT INTO bootstrap_effect VALUES (1,0)");
            assertThrows(IllegalStateException.class, () -> coordinator.executeOnce("catalog", 1, () -> {
                h.jdbc.update("UPDATE bootstrap_effect SET hits=100 WHERE id=1"); throw new IllegalStateException("first failure");
            }));
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM workflow_bootstrap_job", Integer.class));
            assertEquals(0, h.jdbc.queryForObject("SELECT hits FROM bootstrap_effect", Integer.class));
            assertEquals(Optional.of("v1"), coordinator.executeOnce("catalog", 1, () -> "v1"));
            assertThrows(IllegalStateException.class, () -> coordinator.executeOnce("catalog", 2, () -> {
                h.jdbc.update("UPDATE bootstrap_effect SET hits=100 WHERE id=1"); throw new IllegalStateException("upgrade failure");
            }));
            assertEquals(1, h.jdbc.queryForObject("SELECT completed_version FROM workflow_bootstrap_job", Integer.class));
            assertEquals(0, h.jdbc.queryForObject("SELECT hits FROM bootstrap_effect", Integer.class));
            assertEquals(Optional.of("v2"), coordinator.executeOnce("catalog", 2, () -> "v2"));
        }
    }

    @Test
    void competingSchemaPreparationsReuseThePlanAndRetainTerminalStateAndEvidence() throws Exception {
        try (var f = new Fixture()) {
            schemaTables(f); var h = new Harness(f); var service = schema(h);
            var entity = entity(); var plan = List.of("CREATE TABLE biz_asset (id VARCHAR(64))");
            var operations = concurrent(6, index -> service.prepare(entity, List.of(), plan, "user-" + index));
            assertEquals(1, operations.stream().map(value -> value.getId()).distinct().count());
            var id = operations.get(0).getId();
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_schema_operation", Integer.class));
            assertEquals(6, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_schema_operation_event", Integer.class));
            String author = h.jdbc.queryForObject("SELECT created_by FROM entity_schema_operation", String.class);
            service.markRunning(id);
            service.markFailed(id, new IllegalStateException("DDL evidence"));
            assertEquals("DDL evidence", service.latest(entity.getId()).getErrorMessage());
            assertEquals(id, service.prepare(entity, List.of(), plan, "later").getId());
            assertEquals(author, h.jdbc.queryForObject("SELECT created_by FROM entity_schema_operation", String.class));
            assertEquals(1, service.latest(entity.getId()).getAttemptCount());
            service.terminate(entity.getId());
            int events = h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_schema_operation_event", Integer.class);
            var error = assertThrows(BusinessConflictException.class, () -> service.prepare(entity, List.of(), plan, "later"));
            assertEquals("ENTITY_SCHEMA_OPERATION_TERMINATED", error.getErrorCode());
            assertEquals(events, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_schema_operation_event", Integer.class));
            assertEquals("TERMINATED", service.latest(entity.getId()).getStatus());
            var revised = service.prepare(entity, List.of(), List.of("CREATE TABLE biz_asset (id VARCHAR(100))"), "new");
            service.markRunning(revised.getId());
            service.complete(revised.getId(), entity, List.of());
            events = h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_schema_operation_event", Integer.class);
            assertEquals("SCHEMA_CONSISTENT", service.prepare(entity, List.of(), revised.getPlan(), "later").getStatus());
            assertEquals(events, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_schema_operation_event", Integer.class));
        }
    }

    @Test
    void lockedSchemaReadSeesACommittedWinnerDespiteAnOlderRepeatableReadSnapshot() {
        try (var f = new Fixture()) {
            schemaTables(f); var h = new Harness(f); var service = schema(h); var raw = rawSchema(h);
            var entity = entity(); var plan = List.of("CREATE TABLE biz_asset (id VARCHAR(64))");
            h.tx.executeWithoutResult(status -> {
                // 建立早于竞争者提交的快照；REQUIRES_NEW 的 prepare 随后在另一连接完成。
                assertNull(raw.preview(entity, List.of(), plan).getId());
                var committed = service.prepare(entity, List.of(), plan, "winner");
                assertNull(raw.preview(entity, List.of(), plan).getId());
                var current = raw.prepare(entity, List.of(), plan, "follower");
                assertEquals(committed.getId(), current.getId());
            });
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_schema_operation", Integer.class));
            assertEquals("winner", h.jdbc.queryForObject("SELECT created_by FROM entity_schema_operation", String.class));
        }
    }

    private static DatabaseBootstrapJobCoordinator bootstrap(Harness h) {
        return transactional(new DatabaseBootstrapJobCoordinator(com.workflow.support.BusinessMapperTestSupport.mapper(h.jdbc.getDataSource(), com.workflow.bootstrap.infrastructure.persistence.mapper.BootstrapJobMapper.class), locks(h), clock(h)), h);
    }

    private static JdbcDatabaseClock clock(Harness h) {
        return new JdbcDatabaseClock(h.jdbc, DatabaseVendor.MYSQL);
    }

    private static JdbcLockedRow locks(Harness h) {
        return new JdbcLockedRow(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL));
    }

    private static EntitySchemaOperationService schema(Harness h) { return transactional(rawSchema(h), h); }

    private static EntitySchemaOperationService rawSchema(Harness h) {
        var tables = mock(DynamicTableService.class);
        when(tables.inspectSchemaDrift(any(EntityDefinition.class), anyList())).thenReturn(List.of());
        when(tables.scanUniqueConflicts(any(EntityDefinition.class), anyList())).thenReturn(List.of());
        when(tables.targetSchemaFingerprint(any(EntityDefinition.class), anyList())).thenReturn("a".repeat(64));
        when(tables.actualSchemaFingerprint(any(EntityDefinition.class))).thenReturn("a".repeat(64));
        return new EntitySchemaOperationService(com.workflow.support.BusinessMapperTestSupport.mapper(h.jdbc.getDataSource(), com.workflow.entity.definition.infrastructure.persistence.mapper.EntitySchemaOperationMapper.class), new ObjectMapper(), tables, locks(h));
    }

    /** 使用生产事务注解，验证独立事务和 REQUIRED 的实际传播边界。 */
    @SuppressWarnings("unchecked")
    static <T> T transactional(T target, Harness h) {
        var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(h.tx.getTransactionManager());
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    private static EntityDefinition entity() {
        var entity = new EntityDefinition(); entity.setId("asset-definition"); entity.setEntityCode("asset"); return entity;
    }

    private static void bootstrapTables(Fixture f) {
        f.table("workflow_bootstrap_job", "job_name VARCHAR(128) PRIMARY KEY, completed_version INT NOT NULL, owner_id VARCHAR(100), "
                + "completed_at DATETIME(6), create_time DATETIME(6) NOT NULL, update_time DATETIME(6) NOT NULL");
        f.table("bootstrap_effect", "id INT PRIMARY KEY, hits INT NOT NULL");
    }

    private static void schemaTables(Fixture f) {
        f.table("entity_schema_operation", "id VARCHAR(64) PRIMARY KEY, entity_id VARCHAR(64), entity_code VARCHAR(100), status VARCHAR(32), "
                + "plan_hash CHAR(64), idempotency_key VARCHAR(160) UNIQUE, plan_json TEXT, target_fingerprint CHAR(64), actual_fingerprint CHAR(64), "
                + "drift_json TEXT, unique_conflict_json TEXT, risk_level VARCHAR(16), risk_reason VARCHAR(1000), estimated_rows BIGINT, "
                + "lock_risk VARCHAR(16), release_window VARCHAR(255), attempt_count INT DEFAULT 0, error_message TEXT, started_at DATETIME, finished_at DATETIME, "
                + "created_by VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE(entity_id,plan_hash)");
        f.table("entity_schema_operation_event", "id VARCHAR(64) PRIMARY KEY, operation_id VARCHAR(64), from_status VARCHAR(32), to_status VARCHAR(32), "
                + "message VARCHAR(1000), create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
    }
}
