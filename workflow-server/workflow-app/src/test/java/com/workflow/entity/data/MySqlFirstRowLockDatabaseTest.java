package com.workflow.entity.data;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaPauseMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;

/** 四个首行锁 Provider 与两个唯一键锁查询的 MySQL 实库验证；不以 H2 代替 MySQL 锁语义。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlFirstRowLockDatabaseTest {
    @Test
    void definitionReadGuardAllowsAnotherReaderAndBlocksPublicationUntilCommit() {
        try (var f = new Fixture()) {
            String table = f.table("entity_definition", "id VARCHAR(64) PRIMARY KEY, entity_code VARCHAR(64) UNIQUE, entity_name VARCHAR(64), description TEXT, table_name VARCHAR(64), "
                    + "process_definition_id VARCHAR(64), lifecycle_mode VARCHAR(30), storage_mode VARCHAR(30), team_visibility_enabled INT, team_visibility_level VARCHAR(30), "
                    + "status VARCHAR(30), create_time DATETIME, update_time DATETIME, created_by VARCHAR(64)");
            var h = new Harness(f, EntityDefinitionMapper.class);
            h.jdbc.update("INSERT INTO entity_definition (id,entity_code) VALUES ('definition','asset'),('other','other')");
            h.tx.executeWithoutResult(status -> {
                assertEquals("definition", h.mapper(EntityDefinitionMapper.class).findByEntityCodeForShare("asset").orElseThrow().getId());
                try (var reader = f.source.getConnection(); var statement = reader.createStatement()) {
                    reader.setAutoCommit(false);
                    statement.execute("SET SESSION innodb_lock_wait_timeout=1");
                    try (var row = statement.executeQuery("SELECT id FROM " + table + " WHERE entity_code='asset' FOR SHARE")) {
                        assertTrue(row.next()); assertEquals("definition", row.getString(1));
                    }
                    assertRowLocked(f, table, "definition", "other");
                    reader.rollback();
                } catch (SQLException error) { throw new IllegalStateException(error); }
            });
            assertEquals(1, h.jdbc.update("UPDATE entity_definition SET entity_name='changed' WHERE id='definition'"));
        }
    }

    @Test
    void fourProvidersChooseOnlyTheirScopeAndStableLatestEligibleRow() {
        try (var f = new Fixture()) {
            f.table("process_task_sla_pause", "id VARCHAR(64) PRIMARY KEY, sla_id VARCHAR(64), started_at DATETIME, resumed_at DATETIME, KEY(sla_id,started_at,id)");
            f.table("process_task_add_sign", "id VARCHAR(64) PRIMARY KEY, source_task_id VARCHAR(64), status VARCHAR(30), create_time DATETIME, KEY(source_task_id,create_time,id)");
            f.table("entity_publish_history", "id VARCHAR(64) PRIMARY KEY, entity_id VARCHAR(64), version INT, UNIQUE(entity_id,version)");
            f.table("entity_process_link", "id VARCHAR(64) PRIMARY KEY, entity_code VARCHAR(64), entity_record_id VARCHAR(64), generation INT, UNIQUE(entity_code,entity_record_id,generation)");
            var h = new Harness(f, ProcessTaskSlaPauseMapper.class, ProcessTaskAddSignMapper.class, EntityPublishHistoryMapper.class, EntityProcessLinkMapper.class);
            for (int i = 1; i <= 4; i++) {
                String scope = i == 4 ? "other" : "target";
                var time = LocalDateTime.of(2026, 9, 22, 4, 0).plusSeconds(i == 2 ? 1 : i);
                h.jdbc.update("INSERT INTO process_task_sla_pause VALUES (?,?,?,?)", "p" + i, scope, time, i == 3 ? time : null);
                h.jdbc.update("INSERT INTO process_task_add_sign VALUES (?,?,?,?)", "a" + i, scope, i == 3 ? "COMPLETED" : "ACTIVE", time);
                h.jdbc.update("INSERT INTO entity_publish_history VALUES (?,?,?)", "h" + i, scope, i);
                h.jdbc.update("INSERT INTO entity_process_link VALUES (?,'asset',?,?)", "l" + i, scope, i);
            }
            h.tx.executeWithoutResult(status -> {
                assertEquals("p2", h.mapper(ProcessTaskSlaPauseMapper.class).findOpenForUpdate("target").getId());
                assertEquals("a2", h.mapper(ProcessTaskAddSignMapper.class).findOpenBySourceTaskIdForUpdate("target").getId());
                assertEquals("h3", h.mapper(EntityPublishHistoryMapper.class).findLatestByEntityIdForUpdate("target").getId());
                assertEquals("l3", h.mapper(EntityProcessLinkMapper.class).selectLatestForUpdate("asset", "target").getId());
                assertNull(h.mapper(ProcessTaskSlaPauseMapper.class).findOpenForUpdate("target' OR 1=1 --"));
                assertNull(h.mapper(ProcessTaskAddSignMapper.class).findOpenBySourceTaskIdForUpdate("absent"));
                assertNull(h.mapper(EntityPublishHistoryMapper.class).findLatestByEntityIdForUpdate("absent"));
                assertNull(h.mapper(EntityProcessLinkMapper.class).selectLatestForUpdate("other", "target"));
                // JDBC 写入不会自动清理 MyBatis 一级缓存；每次锁定读取必须真正访问数据库。
                assertEquals("p2", h.mapper(ProcessTaskSlaPauseMapper.class).findOpenForUpdate("target").getId());
                h.jdbc.update("UPDATE process_task_sla_pause SET resumed_at=CURRENT_TIMESTAMP WHERE id='p2'");
                assertEquals("p1", h.mapper(ProcessTaskSlaPauseMapper.class).findOpenForUpdate("target").getId());
                assertEquals("a2", h.mapper(ProcessTaskAddSignMapper.class).findOpenBySourceTaskIdForUpdate("target").getId());
                h.jdbc.update("UPDATE process_task_add_sign SET status='COMPLETED' WHERE id='a2'");
                assertEquals("a1", h.mapper(ProcessTaskAddSignMapper.class).findOpenBySourceTaskIdForUpdate("target").getId());
                assertEquals("h3", h.mapper(EntityPublishHistoryMapper.class).findLatestByEntityIdForUpdate("target").getId());
                h.jdbc.update("UPDATE entity_publish_history SET version=0 WHERE id='h3'");
                assertEquals("h2", h.mapper(EntityPublishHistoryMapper.class).findLatestByEntityIdForUpdate("target").getId());
                assertEquals("l3", h.mapper(EntityProcessLinkMapper.class).selectLatestForUpdate("asset", "target").getId());
                h.jdbc.update("UPDATE entity_process_link SET generation=0 WHERE id='l3'");
                assertEquals("l2", h.mapper(EntityProcessLinkMapper.class).selectLatestForUpdate("asset", "target").getId());
            });
        }
    }

    @Test
    void firstRowLockIsHeldOnTheSelectedBaseRowUntilTheCallingTransactionEnds() {
        try (var f = new Fixture()) {
            var table = f.table("entity_publish_history", "id VARCHAR(64) PRIMARY KEY, entity_id VARCHAR(64), version INT, UNIQUE(entity_id,version)");
            var h = new Harness(f, EntityPublishHistoryMapper.class);
            h.jdbc.update("INSERT INTO entity_publish_history VALUES ('old','target',1),('latest','target',2),('other','other',1)");
            h.tx.executeWithoutResult(status -> {
                assertEquals("latest", h.mapper(EntityPublishHistoryMapper.class).findLatestByEntityIdForUpdate("target").getId());
                assertRowLocked(f, table, "latest", "other");
                status.setRollbackOnly();
            });
            assertEquals(1, h.jdbc.update("UPDATE entity_publish_history SET version=3 WHERE id='latest'"));
        }
    }

    @Test
    void globallyUniqueVersionRequestIsLockedWithoutAPaginationClause() {
        try (var f = new Fixture()) {
            // 普通幂等读取已走 BaseMapper 全列投影；隔离表提供完整的版本列，
            // 唯一键与竞争事务仍按生产的实体/记录/幂等键范围验证。
            var table = f.table("entity_record_version", "id VARCHAR(64) PRIMARY KEY, entity_code VARCHAR(64), record_id VARCHAR(64), "
                    + "version_no INT,version_title VARCHAR(128),scenario_code VARCHAR(64),scenario_name VARCHAR(128),"
                    + "operation_type VARCHAR(64),source_type VARCHAR(64),source_id VARCHAR(64),business_intent_code VARCHAR(64),business_intent_name VARCHAR(128),"
                    + "source_entity_code VARCHAR(64),source_record_id VARCHAR(64),process_definition_id VARCHAR(64),process_instance_id VARCHAR(64),task_id VARCHAR(64),"
                    + "operator_id VARCHAR(64),operator_name VARCHAR(128),business_trace_key VARCHAR(128),idempotency_key VARCHAR(64),"
                    + "entity_release_id VARCHAR(64),entity_release_version INT,schema_version INT,data_hash VARCHAR(64),presentation_hash VARCHAR(64),"
                    + "scope_hash VARCHAR(64),request_hash VARCHAR(64),dataset_count INT,snapshot_row_count INT,snapshot_size_bytes BIGINT,"
                    + "completeness VARCHAR(32),snapshot_hash VARCHAR(64),snapshot_document TEXT,create_time DATETIME,"
                    + "UNIQUE(entity_code,record_id,idempotency_key)");
            var h = new Harness(f, EntityRecordVersionMapper.class);
            h.jdbc.update("INSERT INTO entity_record_version (id,entity_code,record_id,scenario_code,idempotency_key) "
                    + "VALUES ('v1','asset','record','ONE','request'),('v2','asset','other','TWO','request')");
            assertThrows(DuplicateKeyException.class, () -> h.jdbc.update("INSERT INTO entity_record_version "
                    + "(id,entity_code,record_id,scenario_code,idempotency_key) VALUES ('v3','asset','record','TWO','request')"));
            h.tx.executeWithoutResult(status -> {
                assertEquals("v1", h.mapper(EntityRecordVersionMapper.class).findIdempotent("asset", "record", "request").getId());
                assertEquals("v1", h.mapper(EntityRecordVersionMapper.class).findIdempotentForUpdate("asset", "record", "request").getId());
                assertNull(h.mapper(EntityRecordVersionMapper.class).findIdempotentForUpdate("asset", "record", "absent"));
                assertRowLocked(f, table, "v1", "v2");
            });
        }
    }

    @Test
    void nullableIssuerAndTypeConstraintKeepProviderLockLookupUnique() throws Exception {
        try (var f = new Fixture()) {
            // 管理 Mapper 有意保持包内可见，通过生产 MappedStatement 执行，避免为测试扩大 API。
            String mapperName = "com.workflow.embed.management.infrastructure.persistence.EmbedManagementMapper";
            providerTable(f); var h = new Harness(f, Class.forName(mapperName));
            h.jdbc.update("INSERT INTO embed_identity_provider (id,type,issuer,subject_namespace) VALUES ('jwt','SIGNED_JWT','issuer','ns'),('trusted','TRUSTED_EXTERNAL_ID',NULL,'ns')");
            assertThrows(DuplicateKeyException.class, () -> h.jdbc.update("INSERT INTO embed_identity_provider (id,type,issuer,subject_namespace) VALUES ('duplicate','TRUSTED_EXTERNAL_ID',NULL,'ns')"));
            h.tx.executeWithoutResult(status -> {
                var params = new java.util.HashMap<String, Object>(); params.put("issuer", null); params.put("namespace", "ns");
                Object trusted = h.session.selectOne(mapperName + ".lockProviderByIssuerAndNamespace", params);
                assertEquals("trusted", org.springframework.test.util.ReflectionTestUtils.getField(trusted, "id"));
                Object jwt = h.session.selectOne(mapperName + ".lockProviderByIssuerAndNamespace", Map.of("issuer", "issuer", "namespace", "ns"));
                assertEquals("jwt", org.springframework.test.util.ReflectionTestUtils.getField(jwt, "id"));
                assertNull(h.session.selectOne(mapperName + ".lockProviderByIssuerAndNamespace", Map.of("issuer", "issuer", "namespace", "other")));
            });
        }
    }

    private static void assertRowLocked(Fixture f, String table, String locked, String other) {
        try (var connection = f.source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SET SESSION innodb_lock_wait_timeout=1");
            try (var update = connection.prepareStatement("UPDATE " + table + " SET id=id WHERE id=?")) {
                update.setString(1, other); update.executeUpdate();
                update.setString(1, locked);
                assertEquals(1205, assertThrows(SQLException.class, update::executeUpdate).getErrorCode());
            }
        } catch (SQLException error) { throw new IllegalStateException(error); }
    }

    private static void providerTable(Fixture f) {
        f.table("embed_identity_provider", "id VARCHAR(64) PRIMARY KEY, name VARCHAR(128), type VARCHAR(32), status VARCHAR(32), issuer VARCHAR(500), "
                + "issuer_uniqueness_key VARCHAR(500) GENERATED ALWAYS AS (COALESCE(issuer,'<trusted-external-id>')) STORED, subject_namespace VARCHAR(128), "
                + "audiences_json TEXT, algorithms_json TEXT, jwks_mode VARCHAR(32), jwks_json TEXT, jwks_url VARCHAR(500), "
                + "clock_skew_seconds INT DEFAULT 30, max_assertion_lifetime_seconds INT DEFAULT 60, key_version BIGINT DEFAULT 1, lock_version BIGINT DEFAULT 1, security_version BIGINT DEFAULT 1, "
                + "create_by VARCHAR(64), create_time DATETIME, update_by VARCHAR(64), update_time DATETIME, revoked_by VARCHAR(64), revoked_at DATETIME, "
                + "UNIQUE(type,issuer_uniqueness_key,subject_namespace), CHECK((type='SIGNED_JWT' AND issuer IS NOT NULL) OR (type='TRUSTED_EXTERNAL_ID' AND issuer IS NULL))");
    }
}
