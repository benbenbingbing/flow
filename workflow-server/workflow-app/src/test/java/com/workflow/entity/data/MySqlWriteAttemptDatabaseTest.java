package com.workflow.entity.data;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.audit.application.SystemAuditFailureWriter;
import com.workflow.admin.audit.application.SystemAuditOutboxHandler;
import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.admin.audit.infrastructure.SystemOperationLogMapper;
import com.workflow.contracts.entity.mutation.*;
import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.version.application.EntityMutationReceiptService;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityMutationReceiptMapper;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.application.DatabaseOutboxPublisher;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Mapper、自动填充主键和 Spring 事务；所有表替换为随机名称，不读写业务表。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlWriteAttemptDatabaseTest {
    interface ItemMapper {
        @Insert("INSERT INTO write_attempt_item (id, required_value) VALUES (#{id}, #{value})")
        int insert(@Param("id") String id, @Param("value") String value);
    }

    @Test void mapperFailurePreservesEarlierWorkAndOuterRollbackStillOwnsAllChanges() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String table = f.table("write_attempt_item", "id VARCHAR(64) PRIMARY KEY, required_value VARCHAR(10) NOT NULL");
            var h = new Harness(f, ItemMapper.class);
            var mapper = h.mapper(ItemMapper.class);
            h.tx.executeWithoutResult(status -> {
                mapper.insert("before", "kept");
                assertThrows(DuplicateKeyException.class, () -> h.attempt.execute(() -> mapper.insert("before", "new")));
                assertThrows(DataIntegrityViolationException.class, () -> h.attempt.execute(() -> mapper.insert("bad", null)));
                assertEquals(1, h.attempt.execute(() -> mapper.insert("after", "kept")));
            });
            assertEquals(2, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            h.tx.executeWithoutResult(status -> {
                h.attempt.execute(() -> mapper.insert("rollback", "gone"));
                assertThrows(DuplicateKeyException.class, () -> h.attempt.execute(() -> mapper.insert("before", "new")));
                status.setRollbackOnly();
            });
            assertEquals(2, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            // 自动提交调用也使用相同唯一冲突规则，且保留驱动的影响行数。
            assertEquals(1, h.attempt.execute(() -> mapper.insert("autocommit", "kept")));
            assertThrows(DuplicateKeyException.class, () -> h.attempt.execute(() -> mapper.insert("autocommit", "new")));
        }
    }

    @Test void concurrentReceiptClaimsReplayOneCommittedResultAndKeepLosingTransactionsUsable() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String receipts = currentTable(f, "entity_mutation_receipt");
            String work = f.table("write_attempt_item", "id VARCHAR(64) PRIMARY KEY, required_value VARCHAR(10) NOT NULL");
            var h = new Harness(f, EntityMutationReceiptMapper.class, ItemMapper.class);
            var mapper = h.mapper(EntityMutationReceiptMapper.class);
            var service = new EntityMutationReceiptService(mapper, h.json, h.attempt);
            var barrier = new CyclicBarrier(6);
            var outcomes = concurrent(6, index -> h.tx.execute(status -> {
                var command = command("same", "value");
                // 固定旧快照，保证输掉竞争的请求进入 INSERT 冲突恢复分支，而非提前读取结果。
                assertNull(mapper.findByIdempotencyKey("same"));
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception error) { throw new AssertionError(error); }
                EntityMutationResult replay = service.acquire(command);
                if (replay == null) service.complete(command, result(command));
                else { assertTrue(replay.replayed()); assertEquals(Map.of("name", "value"), replay.record()); }
                h.mapper(ItemMapper.class).insert("worker-" + index, "kept");
                return replay == null;
            }));
            assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
            assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + receipts + " WHERE status='SUCCESS'", Integer.class));
            assertEquals(6, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + work, Integer.class));
            assertThrows(BusinessConflictException.class, () -> h.tx.execute(status -> service.acquire(command("same", "changed"))));
        }
    }

    @Test void receiptAndBusinessWorkRollbackTogetherAndCanBeRetried() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String receipts = currentTable(f, "entity_mutation_receipt");
            String work = f.table("write_attempt_item", "id VARCHAR(64) PRIMARY KEY, required_value VARCHAR(10) NOT NULL");
            var h = new Harness(f, EntityMutationReceiptMapper.class, ItemMapper.class);
            var service = new EntityMutationReceiptService(h.mapper(EntityMutationReceiptMapper.class), h.json, h.attempt);
            var command = command("rollback", "value");
            assertThrows(IllegalStateException.class, () -> h.tx.execute(status -> {
                assertNull(service.acquire(command));
                h.mapper(ItemMapper.class).insert("business", "gone");
                service.complete(command, result(command));
                throw new IllegalStateException("business failed");
            }));
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + receipts, Integer.class));
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + work, Integer.class));
            h.tx.executeWithoutResult(status -> {
                assertNull(service.acquire(command));
                service.complete(command, result(command));
            });
            assertTrue(h.tx.execute(status -> service.acquire(command)).replayed());
        }
    }

    @Test void outboxDuplicateRequeueKeepsIdentityAndParticipatesInOuterTransaction() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String outbox = currentTable(f, "workflow_outbox_event");
            String work = f.table("write_attempt_item", "id VARCHAR(64) PRIMARY KEY, required_value VARCHAR(10) NOT NULL");
            var h = new Harness(f, OutboxRecordMapper.class, ItemMapper.class);
            var publisher = h.transactional(new DatabaseOutboxPublisher(h.mapper(OutboxRecordMapper.class), h.json, h.attempt, new com.workflow.core.database.JdbcLockedRow(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL))));
            var original = event("same", "original");
            publisher.publish(original);
            String id = f.jdbc.queryForObject("SELECT id FROM " + outbox, String.class);
            assertNotNull(id); // 使用生产 MyBatis-Plus 的 ASSIGN_ID，保存点包装不替代 Mapper。
            h.tx.executeWithoutResult(status -> {
                publisher.publish(event("same", "ignored"));
                h.mapper(ItemMapper.class).insert("after-duplicate", "kept");
            });
            assertTrue(f.jdbc.queryForObject("SELECT payload_document FROM " + outbox, String.class).contains("original"));
            f.jdbc.update("UPDATE " + outbox + " SET status='DEAD', retry_count=20, error_message='old'");
            h.tx.executeWithoutResult(status -> publisher.publishOrRequeueFailed(event("same", "requeued")));
            assertEquals(id, f.jdbc.queryForObject("SELECT id FROM " + outbox, String.class));
            assertEquals("PENDING", f.jdbc.queryForObject("SELECT status FROM " + outbox, String.class));
            assertEquals(0, f.jdbc.queryForObject("SELECT retry_count FROM " + outbox, Integer.class));
            assertTrue(f.jdbc.queryForObject("SELECT payload_document FROM " + outbox, String.class).contains("requeued"));
            assertThrows(IllegalStateException.class, () -> h.tx.execute(status -> {
                publisher.publish(event("new", "rollback"));
                publisher.publish(original);
                throw new IllegalStateException("outer failure");
            }));
            assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + outbox, Integer.class));
            assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + work, Integer.class));
        }
    }

    @Test void concurrentOutboxRequeueAndFirstCreationHaveOneIdentityWithoutLockUpgrade() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String outbox = currentTable(f, "workflow_outbox_event");
            String work = f.table("write_attempt_item", "id VARCHAR(64) PRIMARY KEY, required_value VARCHAR(10) NOT NULL");
            var h = new Harness(f, OutboxRecordMapper.class, ItemMapper.class);
            var publisher = h.transactional(new DatabaseOutboxPublisher(h.mapper(OutboxRecordMapper.class), h.json,
                    h.attempt, new com.workflow.core.database.JdbcLockedRow(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL))));
            publisher.publish(event("existing", "old"));
            String id = f.jdbc.queryForObject("SELECT id FROM " + outbox, String.class);
            f.jdbc.update("UPDATE " + outbox + " SET status='DEAD'");
            concurrent(6, index -> h.tx.execute(status -> {
                publisher.publishOrRequeueFailed(event("existing", "retry-" + index));
                h.mapper(ItemMapper.class).insert("retry-" + index, "kept");
                return true;
            }));
            assertEquals(id, f.jdbc.queryForObject("SELECT id FROM " + outbox, String.class));
            assertEquals("PENDING", f.jdbc.queryForObject("SELECT status FROM " + outbox, String.class));
            concurrent(6, index -> h.tx.execute(status -> {
                publisher.publishOrRequeueFailed(event("new", "create-" + index));
                h.mapper(ItemMapper.class).insert("create-" + index, "kept");
                return true;
            }));
            assertEquals(2, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + outbox, Integer.class));
            assertEquals(12, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + work, Integer.class));
        }
    }

    @Test void auditDuplicateDoesNotAbortConsumerAndRequiresNewFailureLogSurvivesOuterRollback() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String logs = currentTable(f, "system_operation_log");
            String work = f.table("write_attempt_item", "id VARCHAR(64) PRIMARY KEY, required_value VARCHAR(10) NOT NULL");
            var h = new Harness(f, SystemOperationLogMapper.class, ItemMapper.class);
            var mapper = h.mapper(SystemOperationLogMapper.class);
            var writer = h.transactional(new SystemAuditFailureWriter(mapper, h.attempt));
            var handler = new SystemAuditOutboxHandler(mapper, h.json, h.attempt);
            var payload = payload("audit-event");
            writer.persist(payload);
            h.tx.executeWithoutResult(status -> {
                try {
                    handler.handle(new OutboxEvent("outbox", "SYSTEM_AUDIT", "audit-event", "TEST", "target",
                            h.json.writeValueAsString(payload), 0, LocalDateTime.now()));
                } catch (Exception error) { throw new IllegalStateException(error); }
                h.mapper(ItemMapper.class).insert("after-audit", "kept");
            });
            h.tx.executeWithoutResult(status -> {
                writer.persist(payload); // REQUIRES_NEW 重复回执仍正常提交自身事务。
                writer.persist(payload("independent"));
                h.mapper(ItemMapper.class).insert("outer", "gone");
                status.setRollbackOnly();
            });
            assertEquals(2, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + logs, Integer.class));
            assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + work, Integer.class));
        }
    }

    private static EntityMutationCommand command(String key, String value) {
        var context = EntityMutationContext.builder(EntityMutationSourceType.FORM, "UPDATE", "修改")
                .operator("user", "用户").trace("trace", key).build();
        return new EntityMutationCommand("operation", "asset", "record", EntityMutationOperationType.UPDATE, Map.of("name", value), context);
    }

    private static EntityMutationResult result(EntityMutationCommand command) {
        return new EntityMutationResult(command.operationId(), command.entityCode(), command.recordId(), command.operationType(),
                command.payload(), 1, "UPDATE", true, false);
    }

    private static OutboxPublishRequest event(String key, String value) {
        return new OutboxPublishRequest("SAVEPOINT_TEST", key, "TEST", "record", Map.of("value", value), 20);
    }

    private static AuditLogPayload payload(String event) {
        return new AuditLogPayload(event, "operation", "trace", null, "SYSTEM", "TEST", "1", null,
                "SYSTEM", "UPDATE", "修改", "HIGH", "FAILURE", "user", "用户", "127.0.0.1", "JUnit", "POST", "/test",
                "TEST", "1", "名称", "摘要", null, null, null, false, null, null, 1L, LocalDateTime.now());
    }

    /** 只复制指定表的建表及已知增量结构到随机表，不执行业务表迁移、UPDATE 或种子数据。 */
    static String currentTable(MySqlRuntimePaginationDatabaseTest.Fixture f, String table) throws Exception {
        String ddl = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration/V001__business_schema.sql"));
        var matcher = Pattern.compile("CREATE TABLE `" + Pattern.quote(table) + "` \\(\\n(.*?)\\n\\) ENGINE", Pattern.DOTALL).matcher(ddl);
        assertTrue(matcher.find(), table);
        String actual = f.table(table, matcher.group(1));
        String delta = switch (table) {
            case "workflow_outbox_event" -> "V004__outbox_leases.sql";
            case "system_operation_log" -> "V064__unified_audit_operation_context.sql";
            default -> null;
        };
        if (delta != null) {
            String migration = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration", delta));
            var alteration = Pattern.compile("ALTER TABLE `" + Pattern.quote(table) + "`[^;]*;").matcher(migration);
            assertTrue(alteration.find(), table);
            f.jdbc.execute(alteration.group().replace("`" + table + "`", "`" + actual + "`"));
        }
        return actual;
    }

    static final class Harness {
        final JdbcTemplate jdbc;
        final JdbcWriteAttempt attempt;
        final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        final DataSourceTransactionManager manager;
        final TransactionTemplate tx;
        final SqlSessionTemplate session;

        Harness(MySqlRuntimePaginationDatabaseTest.Fixture f, Class<?>... mappers) {
            var source = f.isolatedDataSource();
            jdbc = new JdbcTemplate(source);
            attempt = new JdbcWriteAttempt(jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL));
            manager = new DataSourceTransactionManager(source);
            tx = new TransactionTemplate(manager); tx.setTimeout(20);
            var config = new MybatisConfiguration(); config.setDatabaseId("MYSQL"); config.setMapUnderscoreToCamelCase(true);
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.getGlobalConfig(config)
                    .getDbConfig().setLogicDeleteField("deleted");
            // Mapper 默认方法复用 BaseMapper 分页，夹具须安装与应用相同的官方分页插件。
            config.addInterceptor(new com.workflow.config.database.DatabaseMybatisConfiguration()
                    .mybatisPlusInterceptor(new com.workflow.integration.database.dialect.MySqlSchemaDdlDialect()));
            for (var mapper : mappers) config.addMapper(mapper);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
            try { session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }

        <T> T mapper(Class<T> type) { return session.getMapper(type); }

        @SuppressWarnings("unchecked")
        <T> T transactional(T target) {
            var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(manager);
            interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor);
            return (T) proxy.getProxy();
        }
    }
}
