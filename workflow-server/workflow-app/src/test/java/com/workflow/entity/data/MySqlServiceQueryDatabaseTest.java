package com.workflow.entity.data;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.JdbcWriteAttempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.audit.api.SystemAuditQuery;
import com.workflow.admin.audit.application.SystemAuditQueryService;
import com.workflow.admin.audit.domain.SystemOperationLog;
import com.workflow.admin.audit.infrastructure.SystemOperationLogMapper;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseQueryDialect;
import com.workflow.contracts.entity.port.EntityUserReferencePort.EntityUserReferenceException;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.application.SchemaDdlExecutor;
import com.workflow.entity.data.infrastructure.adapter.EntityUserReferenceAdapter;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.integration.database.dialect.MySqlSchemaDdlDialect;
import com.workflow.core.database.schema.JdbcSchemaMetadata;
import com.workflow.process.assignment.application.AssigneeIncidentService;
import com.workflow.process.assignment.application.AssigneeResolutionService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 执行服务入口及真实 MyBatis/JDBC，验证上限、过滤和原生值展示；所有表随机隔离并清理。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlServiceQueryDatabaseTest {
    private static final DatabaseQueryDialect QUERY = DatabaseQueryDialects.forDatabaseId("MYSQL");

    @Test
    void uniqueConflictScanKeepsFiveSamplesPerFieldAndReadsNativeDecimalValues() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String table = f.table("biz_conflicts", "id VARCHAR(64) PRIMARY KEY, deleted INT DEFAULT 0, sample_code VARCHAR(100), amount DECIMAL(10,2)");
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 2; j++) f.jdbc.update("INSERT INTO " + table + " VALUES (?,0,?,12.30)", i + "-" + j, i + "-中文 O'Reilly");
            }
            f.jdbc.update("INSERT INTO " + table + " VALUES ('deleted',1,'0-中文 O''Reilly',12.30),('null',0,NULL,NULL)");
            var entity = definition("e1", "conflicts");
            var resolver = mock(EntityPhysicalTableResolver.class);
            when(resolver.resolve(entity)).thenReturn(table);
            var schema = new MySqlSchemaDdlDialect();
            var service = new DynamicTableService(f.jdbc, mock(EntityFieldMapper.class), resolver, mock(SchemaDdlExecutor.class),
                    schema, new JdbcSchemaMetadata(f.jdbc, schema), QUERY);
            var order = field("sample_code", EntityField.FieldType.STRING); order.setIsUnique(true); order.setFieldName("编号");
            var amount = field("amount", EntityField.FieldType.DECIMAL); amount.setIsUnique(true); amount.setFieldName("金额");
            var conflicts = service.scanUniqueConflicts(entity, List.of(order, amount));
            assertEquals(6, conflicts.size());
            assertEquals("编号=0-中文 O'Reilly（2条）", conflicts.get(0));
            assertEquals("编号=4-中文 O'Reilly（2条）", conflicts.get(4));
            assertEquals("金额=12.30（12条）", conflicts.get(conflicts.size() - 1));
        }
    }

    @Test
    void multiUserReadKeepsScopeOrderingAndRejectsTheTwoHundredAndFirstUser() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String multi = f.table("biz_review_multi", "id VARCHAR(64) PRIMARY KEY, record_id VARCHAR(64), field_code VARCHAR(64),"
                    + " target_entity_id VARCHAR(64), target_record_id VARCHAR(64), sort_order INT, deleted INT");
            String users = f.table("sys_user", "id VARCHAR(64) PRIMARY KEY, username VARCHAR(64), deleted INT");
            for (int i = 1; i <= 201; i++) {
                String key = String.format("u%03d", i);
                f.jdbc.update("INSERT INTO " + multi + " VALUES (?,'record','reviewers','users',?,?,?)", key, key, i, i == 201 ? 1 : 0);
                f.jdbc.update("INSERT INTO " + users + " VALUES (?,?,0)", key, "name-" + key);
            }
            // 低排序的另一条业务记录和旧目标关系不得占用当前查询的前 201 个名额。
            f.jdbc.update("INSERT INTO " + multi + " VALUES ('other-record','other','reviewers','users','u201',-2,0),"
                    + "('old-target','record','reviewers','old-users','u201',-1,0)");
            var definitions = mock(EntityDefinitionMapper.class);
            var fields = mock(EntityFieldMapper.class);
            var data = mock(EntityDataDynamicMapper.class);
            var resolver = mock(EntityPhysicalTableResolver.class);
            var tables = mock(DynamicTableService.class);
            var entity = definition("review", "review");
            when(definitions.findByEntityCode("review")).thenReturn(Optional.of(entity));
            when(definitions.selectById("users")).thenReturn(definition("users", "sys_user"));
            var reviewers = field("reviewers", EntityField.FieldType.MULTI_REFERENCE);
            reviewers.setRefEntityId("users"); reviewers.setIsPublished(true);
            when(fields.findByEntityIdAndFieldCode("review", "reviewers")).thenReturn(reviewers);
            when(resolver.resolve(entity)).thenReturn("biz_review");
            when(data.selectById("biz_review", "record")).thenReturn(Map.of("id", "record"));
            when(tables.getMultiValueTableName("review")).thenReturn(multi);
            var service = new EntityUserReferenceAdapter(definitions, fields, data, resolver, tables,
                    new JdbcTemplate(f.isolatedDataSource()), QUERY);
            var names = service.readUserKeys("review", "record", "reviewers");
            assertEquals(200, names.size());
            assertEquals("name-u001", names.get(0));
            assertEquals("name-u200", names.get(names.size() - 1));
            f.jdbc.update("UPDATE " + multi + " SET deleted=0 WHERE id='u201'");
            assertThrows(EntityUserReferenceException.class, () -> service.readUserKeys("review", "record", "reviewers"));
        }
    }

    @Test
    void incidentListPreservesStatusPriorityAndCapsTheFilteredSetAtFiveHundred() {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String columns = "id VARCHAR(64) PRIMARY KEY, retry_count INT, max_retries INT, next_retry_at DATETIME,"
                    + " resolved_at DATETIME, create_time DATETIME, update_time DATETIME";
            for (String name : List.of("process_config_id", "process_definition_id", "process_instance_id", "task_id", "node_id", "node_name",
                    "policy", "status", "empty_reason_code", "empty_reason_message", "resolver_code", "fallback_user", "fallback_group",
                    "responsibility_owner", "resolution_action", "resolved_by")) columns += ", " + name + " VARCHAR(100)";
            String table = f.table("process_assignee_incident", columns);
            for (int i = 0; i < 502; i++) f.jdbc.update("INSERT INTO " + table + " (id,status,update_time) VALUES (?,'OPEN','2026-01-01')", String.format("i%04d", i));
            for (String status : List.of("UNKNOWN", "RETRY_SCHEDULED", "MANUAL_REQUIRED", "RESOLVED", "TERMINATED"))
                f.jdbc.update("INSERT INTO " + table + " (id,status,update_time) VALUES (?,?,'2026-01-01')", status, status);
            var service = new AssigneeIncidentService(new JdbcTemplate(f.isolatedDataSource()), new ObjectMapper(),
                    mock(TaskService.class), mock(RuntimeService.class), mock(AssigneeResolutionService.class), QUERY, new JdbcWriteAttempt(new JdbcTemplate(), DatabaseDialects.insert(DatabaseVendor.MYSQL)));
            var all = service.list(null);
            assertEquals(500, all.size());
            assertEquals("UNKNOWN", all.get(0).get("id"));
            assertEquals("i0501", all.get(1).get("id"));
            var open = service.list(" OPEN ");
            assertEquals(500, open.size());
            assertEquals("i0501", open.get(0).get("id"));
            assertEquals("i0002", open.get(open.size() - 1).get("id"));
            assertEquals(List.of("RESOLVED"), service.list("RESOLVED").stream().map(row -> row.get("id")).toList());
            assertTrue(service.list("' OR 1=1 --").isEmpty());
        }
    }

    @Test
    void auditExportAndTimelineKeepCapsStableTiesAndLegacyOperationFiltering() throws Exception {
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            List<String> columns = new ArrayList<>();
            // 模型字段仅用来搭建隔离表；断言独立校验业务上限及筛选，不把 SQL 模板当预期结果。
            for (var field : SystemOperationLog.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String name = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
                String type = field.getType() == LocalDateTime.class ? "DATETIME" : Number.class.isAssignableFrom(field.getType()) ? "BIGINT" : "VARCHAR(100)";
                columns.add(name + " " + type + (name.equals("id") ? " PRIMARY KEY" : ""));
            }
            String table = f.table("system_operation_log", String.join(",", columns));
            // 单次多行写入避免测试为一万条日志建立一万次远程往返。
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < 10002; i++) { values.add(String.format("log-%05d", i)); values.add("op"); }
            f.jdbc.update("INSERT INTO " + table + " (id,operation_id,create_time) VALUES "
                    + String.join(",", Collections.nCopies(10002, "(?,?,'2026-01-01')")), values.toArray());
            f.jdbc.update("INSERT INTO " + table + " (id,event_id,operation_id,create_time) VALUES "
                    + "('a-legacy-null','op',NULL,'2026-01-01'),('b-legacy-empty','op','','2026-01-01'),"
                    + "('a-excluded','op','different','2026-01-01')");
            var config = new DatabaseMybatisConfiguration(); var schema = new MySqlSchemaDdlDialect();
            var bean = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
            bean.setDataSource(f.isolatedDataSource()); bean.setDatabaseIdProvider(config.databaseIdProvider(schema));
            bean.setPlugins(config.mybatisPlusInterceptor(schema));
            var factory = Objects.requireNonNull(bean.getObject());
            factory.getConfiguration().addMapper(SystemOperationLogMapper.class);
            try (var session = factory.openSession(true)) {
                var service = new SystemAuditQueryService(session.getMapper(SystemOperationLogMapper.class), QUERY);
                var exported = service.export(new SystemAuditQuery());
                assertEquals(10000, exported.size());
                assertEquals("log-10001", exported.get(0).getId());
                assertEquals("log-00002", exported.get(exported.size() - 1).getId());
                var timeline = service.operationTimeline("op");
                assertEquals(500, timeline.size());
                assertEquals("a-legacy-null", timeline.get(0).id());
                assertEquals("b-legacy-empty", timeline.get(1).id());
                assertEquals("log-00497", timeline.get(timeline.size() - 1).id());
            }
        }
    }

    @Test
    void queueMetricsUseDatabaseUtcBoundariesAndReturnZeroForEmptyOrFutureItems() {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        try (var f = new MySqlRuntimePaginationDatabaseTest.Fixture()) {
            String columns = "id VARCHAR(64) PRIMARY KEY, status VARCHAR(32), next_retry_time DATETIME(6), create_time DATETIME(6)";
            String outbox = f.table("workflow_outbox_event", columns);
            String action = f.table("process_action_execution", columns);
            LocalDateTime now = new com.workflow.core.database.JdbcDatabaseClock(f.jdbc,
                    com.workflow.integration.database.api.DatabaseVendor.MYSQL).utcNow();
            f.jdbc.update("INSERT INTO " + outbox + " VALUES ('ready','PENDING',NULL,?),('retry','FAILED',?,?),"
                            + "('later','FAILED',?,?),('running','PROCESSING',NULL,?),('dead','DEAD',NULL,?)",
                    now.minusNanos(45900000000L), now, now.minusSeconds(12),
                    now.plusSeconds(1), now.minusSeconds(90), now, now);
            f.jdbc.update("INSERT INTO " + action + " VALUES ('future-created','PENDING',NULL,?),('running','RUNNING',NULL,?)",
                    now.plusSeconds(5), now);
            // 固定从数据库取得的 UTC 边界，避免断言依赖测试线程执行速度。
            var metrics = new com.workflow.config.AsyncQueueMetrics(new JdbcTemplate(f.isolatedDataSource()), registry, () -> now);
            metrics.refresh();
            assertEquals(2, registry.get("workflow.queue.items").tag("queue", "outbox").tag("state", "ready").gauge().value());
            assertEquals(1, registry.get("workflow.queue.items").tag("queue", "outbox").tag("state", "running").gauge().value());
            assertEquals(1, registry.get("workflow.queue.items").tag("queue", "outbox").tag("state", "dead").gauge().value());
            assertEquals(45, registry.get("workflow.queue.oldest.ready.seconds").tag("queue", "outbox").gauge().value());
            assertEquals(1, registry.get("workflow.queue.items").tag("queue", "flow_action").tag("state", "running").gauge().value());
            assertEquals(0, registry.get("workflow.queue.oldest.ready.seconds").tag("queue", "flow_action").gauge().value());
            f.jdbc.update("DELETE FROM " + action);
            metrics.refresh();
            assertEquals(0, registry.get("workflow.queue.items").tag("queue", "flow_action").tag("state", "ready").gauge().value());
            assertEquals(0, registry.get("workflow.queue.items").tag("queue", "flow_action").tag("state", "running").gauge().value());
            assertEquals(0, registry.get("workflow.queue.oldest.ready.seconds").tag("queue", "flow_action").gauge().value());
        } finally {
            registry.close();
        }
    }

    private static EntityDefinition definition(String id, String code) {
        var entity = new EntityDefinition(); entity.setId(id); entity.setEntityCode(code); return entity;
    }

    private static EntityField field(String code, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setDbColumnName(code); field.setFieldType(type); return field;
    }
}
