package com.workflow.entity.data;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.version.application.EntityVersionConfigurationService;
import com.workflow.entity.version.application.EntityVersionConfigurationValidator;
import com.workflow.entity.version.application.EntityVersionScopeFreezer;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionRolloutBridgeMapper;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.AdditionalAnswers;
import org.springframework.transaction.TransactionDefinition;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 真实配置 INSERT/CAS 与 MySQL 可重复读；在首次查询后由独立连接提交赢家，证明冲突提示不读旧快照。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlVersionConflictReadDatabaseTest {
    @Test void currentConfigInsertConflictReportsCommittedRevision() { race(false, true); }
    @Test void currentConfigCasFailureReportsCommittedRevision() { race(false, false); }
    @Test void legacyDraftInsertConflictReportsCommittedRevision() { race(true, true); }
    @Test void legacyDraftCasFailureReportsCommittedRevision() { race(true, false); }

    private void race(boolean legacy, boolean creating) {
        try (var f = new Fixture()) {
            String table = tables(f);
            var h = harness(f); var realConfig = h.mapper(EntityVersionConfigMapper.class);
            var realBridge = h.mapper(EntityVersionRolloutBridgeMapper.class);
            if (!creating) insertWinner(f, table, 7);
            var config = mock(EntityVersionConfigMapper.class, AdditionalAnswers.delegatesTo(realConfig));
            var bridge = mock(EntityVersionRolloutBridgeMapper.class, AdditionalAnswers.delegatesTo(realBridge));
            Runnable commitCompetitor = () -> {
                // f.jdbc 使用原始数据源，未加入 h 的事务；此处是真实独立连接提交，不模拟 Mapper 的写入结果。
                if (creating) insertWinner(f, table, 9);
                else f.jdbc.update("UPDATE " + table + " SET revision=9,config_document=?,draft_document=? WHERE id='winner'",
                        "{\"winner\":true}", "{\"winner\":true}");
            };
            if (legacy) {
                doAnswer(call -> {
                    var before = realBridge.findStateByEntityCode("asset");
                    commitCompetitor.run(); return before;
                }).when(bridge).findStateByEntityCode("asset");
            } else {
                doAnswer(call -> {
                    var before = realConfig.findByEntityCode("asset");
                    commitCompetitor.run(); return before;
                }).when(config).findByEntityCode("asset");
            }
            var service = service(h, config, bridge);
            BusinessConflictException failure = assertThrows(BusinessConflictException.class, () -> h.tx.execute(status -> {
                if (legacy) return service.saveLegacyDraft("asset", request(), creating ? 0 : 7);
                return service.save("asset", request(), creating ? 0 : 7);
            }));
            assertEquals("ENTITY_VERSION_CONFIG_REVISION_CONFLICT", failure.getErrorCode());
            assertTrue(failure.getMessage().contains("currentRevision=9"), failure.getMessage());
            assertTrue(failure.getMessage().contains("expectedRevision=" + (creating ? 0 : 7)));
            verify(config).findCurrentRevisionForConflict("asset");
            assertEquals(9, f.jdbc.queryForObject("SELECT revision FROM " + table, Integer.class));
            assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            assertEquals("{\"winner\":true}", f.jdbc.queryForObject("SELECT config_document FROM " + table, String.class));
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_version_config_release", Integer.class));
        }
    }

    @Test void concurrentDuplicateInsertsReadRevisionWithoutSharedToExclusiveLockUpgrade() throws Exception {
        try (var f = new Fixture()) {
            String table = tables(f); var h = harness(f); var real = h.mapper(EntityVersionConfigMapper.class);
            var config = mock(EntityVersionConfigMapper.class, AdditionalAnswers.delegatesTo(real));
            var snapshots = new CyclicBarrier(6); var committed = new CountDownLatch(1); var ordinal = new AtomicInteger();
            doAnswer(call -> {
                assertNull(real.findByEntityCode("asset"));
                int index = ordinal.getAndIncrement();
                snapshots.await(10, TimeUnit.SECONDS);
                if (index == 0) {
                    try { insertWinner(f, table, 9); }
                    finally { committed.countDown(); }
                }
                assertTrue(committed.await(10, TimeUnit.SECONDS)); return null;
            }).when(config).findByEntityCode("asset");
            var service = service(h, config, h.mapper(EntityVersionRolloutBridgeMapper.class));
            var messages = concurrent(6, index -> {
                var conflict = assertThrows(BusinessConflictException.class,
                        () -> h.tx.execute(status -> service.save("asset", request(), 0)));
                assertEquals("ENTITY_VERSION_CONFIG_REVISION_CONFLICT", conflict.getErrorCode());
                return conflict.getMessage();
            });
            assertTrue(messages.stream().allMatch(message -> message.contains("currentRevision=9")));
            assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
    }

    @Test void conflictRevisionReadBypassesCachesAndHonorsLogicalDeletion() {
        try (var f = new Fixture()) {
            String table = tables(f); var h = harness(f); var mapper = h.mapper(EntityVersionConfigMapper.class);
            insertWinner(f, table, 7);
            h.tx.executeWithoutResult(status -> {
                assertEquals(7, mapper.findByEntityCode("asset").getRevision());
                f.jdbc.update("UPDATE " + table + " SET revision=9,deleted=1 WHERE id='winner'");
                assertNull(mapper.findCurrentRevisionForConflict("asset"));
                assertNull(mapper.findCurrentRevisionForConflict("asset' OR 1=1 --"));
                assertNull(mapper.findCurrentRevisionForConflict(null));
            });
        }
    }

    private static String tables(Fixture f) {
        String actual = f.table("entity_version_config", "id VARCHAR(64) PRIMARY KEY,entity_id VARCHAR(64),entity_code VARCHAR(100),"
                + "enabled TINYINT,config_document LONGTEXT,active_release_id VARCHAR(64),revision INT,create_by VARCHAR(64),"
                + "create_time DATETIME,update_by VARCHAR(64),update_time DATETIME,deleted INT,contract_version INT,"
                + "draft_document LONGTEXT,status VARCHAR(32),migration_state VARCHAR(32),UNIQUE KEY uk_entity(entity_code,deleted)");
        f.table("entity_version_config_release", "id VARCHAR(64) PRIMARY KEY,config_id VARCHAR(64),config_document LONGTEXT,contract_version INT");
        return actual;
    }
    private static void insertWinner(Fixture f, String table, int revision) {
        f.jdbc.update("INSERT INTO " + table + " (id,entity_id,entity_code,enabled,config_document,revision,deleted,draft_document,status)"
                + " VALUES ('winner','entity','asset',1,?, ?,0,?,'DRAFT')", "{\"winner\":true}", revision, "{\"winner\":true}");
    }
    private static Harness harness(Fixture f) {
        var h = new Harness(f, EntityVersionConfigMapper.class, EntityVersionRolloutBridgeMapper.class);
        h.tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); return h;
    }
    private static EntityVersionConfiguration request() {
        var request = new EntityVersionConfiguration(); request.setEnabled(true); request.setSchemaVersion(2); return request;
    }
    private static EntityVersionConfigurationService service(Harness h, EntityVersionConfigMapper configs, EntityVersionRolloutBridgeMapper bridge) {
        var definitions = mock(EntityDefinitionMapper.class); var definition = new EntityDefinition();
        definition.setId("entity"); definition.setEntityCode("asset"); definition.setEntityName("资产");
        when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(definition));
        var freezer = mock(EntityVersionScopeFreezer.class);
        when(freezer.freeze(any())).thenAnswer(call -> call.getArgument(0));
        return h.transactional(new EntityVersionConfigurationService(configs, bridge, mock(EntityRecordVersionMapper.class),
                definitions, h.json, mock(EntityVersionConfigurationValidator.class), freezer, h.attempt));
    }
}
