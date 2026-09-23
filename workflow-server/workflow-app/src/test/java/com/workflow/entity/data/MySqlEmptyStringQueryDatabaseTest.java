package com.workflow.entity.data;

import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedMaintenanceMapper;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedTrafficControlMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterObservationRow;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.application.PermissionSqlBuilder;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 MySQL 对照旧空串条件，验证权限范围、流程状态及复合游标没有随可移植改写变化。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlEmptyStringQueryDatabaseTest {
    @Test void characterEmptinessKeepsColumnCollationAndPermissionMembership() {
        try (var f = new Fixture()) {
            f.table("entity_definition", "entity_code VARCHAR(64)");
            f.table("sys_menu", "id VARCHAR(64),perm VARCHAR(100),entity_code VARCHAR(64),menu_type CHAR(1),status CHAR(1),deleted INT");
            f.table("sys_role", "id VARCHAR(64),role_code VARCHAR(64),status CHAR(1),deleted INT");
            f.table("sys_user_role", "user_id VARCHAR(64),role_id VARCHAR(64)");
            f.table("sys_role_menu", "role_id VARCHAR(64),menu_id VARCHAR(64)");
            var h = new Harness(f, EntityDefinitionMapper.class, SysMenuMapper.class);
            h.jdbc.update("INSERT INTO sys_role VALUES ('r','normal','0',0),('off','disabled','1',0)");
            h.jdbc.update("INSERT INTO sys_user_role VALUES ('u','r'),('u','off')");
            for (String collation : List.of("utf8mb4_unicode_ci", "utf8mb4_0900_ai_ci")) {
                h.jdbc.execute("ALTER TABLE entity_definition MODIFY entity_code VARCHAR(64) COLLATE " + collation);
                h.jdbc.execute("ALTER TABLE sys_menu MODIFY perm VARCHAR(100) COLLATE " + collation);
                h.jdbc.update("DELETE FROM entity_definition"); h.jdbc.update("DELETE FROM sys_menu"); h.jdbc.update("DELETE FROM sys_role_menu");
                int i = 0;
                for (String value : new String[]{null, "", "   ", "\t", "0", "expense:read", "中文", "O'Neil"}) {
                    String id = Integer.toString(++i);
                    h.jdbc.update("INSERT INTO entity_definition VALUES (?)", value);
                    h.jdbc.update("INSERT INTO sys_menu VALUES (?,?,'expense','F','0',0)", id, value);
                    h.jdbc.update("INSERT INTO sys_role_menu VALUES ('r',?)", id);
                }
                h.jdbc.update("INSERT INTO sys_menu VALUES ('off','disabled:read','expense','F','0',0),('deleted','deleted:read','expense','F','0',1),('other','other:read','other','F','0',0)");
                h.jdbc.update("INSERT INTO sys_role_menu VALUES ('off','off'),('r','deleted')");
                Set<String> expected = new HashSet<>(h.jdbc.queryForList("SELECT entity_code FROM entity_definition WHERE entity_code IS NOT NULL AND entity_code <> ''", String.class));
                assertEquals(expected, new HashSet<>(h.mapper(EntityDefinitionMapper.class).findAllEntityCodes()), collation);
                assertEquals(expected, h.mapper(SysMenuMapper.class).selectPermsByUserId("u"), collation);
                var entityExpected = new HashSet<>(expected); entityExpected.add("disabled:read");
                assertEquals(entityExpected, h.mapper(SysMenuMapper.class).selectPermsByEntityCode("expense"));
                assertTrue(h.mapper(SysMenuMapper.class).selectPermsByUserId("unknown' OR 1=1 --").isEmpty());
            }
        }
    }

    @Test void legacyProcessStateRulesAndInstanceCountKeepNullEmptyAndEndStateScopes() {
        try (var f = new Fixture()) {
            f.table("biz_state", "id VARCHAR(64),process_instance_id VARCHAR(64),process_end_time DATETIME,status VARCHAR(64),deleted INT");
            var h = new Harness(f, EntityDataDynamicMapper.class);
            h.jdbc.update("INSERT INTO biz_state VALUES ('null',NULL,NULL,'NEW',0),('empty','',NULL,'NEW',0),('spaces','   ',NULL,'NEW',0),('running','p',NULL,'RUN',0),('completed','p','2026-01-01','DONE',0),('withdrawn','p','2026-01-01','BACK',0),('terminated','p','2026-01-01','STOP',0),('deleted','p',NULL,'RUN',1)");
            var statuses = mock(EntityStatusMapper.class);
            var withdrawn = new EntityStatus(); withdrawn.setStatusCode("BACK");
            var terminated = new EntityStatus(); terminated.setStatusCode("STOP");
            when(statuses.findByCategory("expense", "WITHDRAWN")).thenReturn(List.of(withdrawn));
            when(statuses.findByCategory("expense", "TERMINATED")).thenReturn(List.of(terminated));
            var builder = new PermissionSqlBuilder(null, null, statuses, List.of(), DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
            var user = new SysUser(); user.setId("u");
            var legacy = Map.of(
                    "NOT_STARTED", "(process_instance_id IS NULL OR process_instance_id = '')",
                    "RUNNING", "(process_instance_id IS NOT NULL AND process_instance_id <> '' AND process_end_time IS NULL)",
                    "COMPLETED", "(process_instance_id IS NOT NULL AND process_instance_id <> '' AND process_end_time IS NOT NULL AND status NOT IN ('BACK','STOP'))");
            for (var entry : legacy.entrySet()) {
                var rule = new EntityActionRuleDTO.RuleNode(); rule.setType("PROCESS_STATE"); rule.setOperator("EQ"); rule.setValue(entry.getKey());
                var filter = new FilterConfigDTO(); filter.setType("RULE"); filter.setRoot(rule);
                var parameters = new java.util.LinkedHashMap<String, Object>();
                String actual = builder.buildFilterSql("expense", filter, user, parameters);
                assertEquals(ids(h, entry.getValue()), boundIds(h, actual, parameters), entry.getKey());
                rule.setOperator("NE");
                parameters.clear();
                assertEquals(ids(h, "NOT (" + entry.getValue() + ")"),
                        boundIds(h, builder.buildFilterSql("expense", filter, user, parameters), parameters));
            }
            assertEquals(4, h.mapper(EntityDataDynamicMapper.class).countProcessInstances("biz_state"));
        }
    }

    @Test void todoScopeKeepsNullNodeTypesCandidateRolesClaimsAndAssignedOnlyRules() {
        try (var f = new Fixture()) {
            taskTables(f); var h = new Harness(f, ProcessTaskMapper.class); var mapper = h.mapper(ProcessTaskMapper.class);
            h.jdbc.update("INSERT INTO sys_user VALUES ('u','alice','0',0),('other','bob','0',0)");
            h.jdbc.update("INSERT INTO sys_group VALUES ('g','finance','0',0),('shadow','ROLE_manager','0',0)");
            h.jdbc.update("INSERT INTO sys_role VALUES ('r','manager','0',0)");
            h.jdbc.update("INSERT INTO sys_user_group VALUES ('u','g'),('u','shadow')");
            h.jdbc.update("INSERT INTO sys_user_role VALUES ('u','r')");
            String[] assignees = {null, "", "   ", "alice", "bob"};
            for (int i = 0; i < assignees.length; i++) {
                String id = "t" + i;
                h.jdbc.update("INSERT INTO process_task(id,task_id,node_type,process_instance_id,entity_code,entity_data_id,status,deleted,create_time) VALUES (?,?,NULL,'p','expense',?,'todo',0,'2026-01-01')", id, id, "record" + i);
                h.jdbc.update("INSERT INTO ACT_RU_TASK VALUES (?,?,'p')", id, assignees[i]);
                h.jdbc.update("INSERT INTO ACT_RU_IDENTITYLINK VALUES (?,'candidate',NULL,?)", id, i % 2 == 0 ? "ROLE_manager" : "finance");
            }
            assertEquals(List.of("record0", "record1", "record2", "record3"), mapper.selectActionableEntityDataIds("alice", "expense"));
            assertEquals(4, mapper.countTodoByUser("u"));
            assertNull(mapper.selectActionableTaskId("u", "expense", "record0", "p", true));
            assertEquals("t3", mapper.selectActionableTaskId("u", "expense", "record3", "p", true));
            assertEquals("t0", mapper.selectActionableTaskId("u", "expense", "record0", "p", false));
            assertTrue(mapper.selectActionableEntityDataIds("u", "other-entity").isEmpty());
            // ROLE_ 开头的组必须经角色关系授权，不能借同名普通组扩大范围。
            h.jdbc.update("DELETE FROM sys_user_role");
            assertEquals(List.of("record1", "record3"), mapper.selectActionableEntityDataIds("u", "expense"));
            h.jdbc.update("UPDATE ACT_RU_TASK SET ASSIGNEE_='bob' WHERE ID_='t1'");
            h.jdbc.update("UPDATE process_task SET entity_data_id='' WHERE task_id='t3'");
            assertTrue(mapper.selectActionableEntityDataIds("u", "expense").isEmpty());
            assertEquals(1, mapper.countTodoByUser("u"));
            h.jdbc.update("UPDATE sys_user SET status='1' WHERE id='u'");
            assertEquals(0, mapper.countTodoByUser("u"));
        }
    }

    @Test void counterInspectionStartsAtEmptyCursorAndAdvancesWithinCompositeKeys() {
        try (var f = new Fixture()) {
            f.table("embed_session_counter", "grant_id VARCHAR(64),flow_user_id VARCHAR(64),active_count INT,PRIMARY KEY(grant_id,flow_user_id)");
            f.table("embed_session", "id VARCHAR(64) PRIMARY KEY,grant_id VARCHAR(64),flow_user_id VARCHAR(64),status VARCHAR(32),slot_released INT");
            var h = new Harness(f, EmbedMaintenanceMapper.class); var mapper = h.mapper(EmbedMaintenanceMapper.class);
            h.jdbc.update("INSERT INTO embed_session_counter VALUES ('g1','u1',2),('g1','u2',9),('g3','u1',2)");
            h.jdbc.update("INSERT INTO embed_session VALUES ('1','g1','u1','ACTIVE',0),('2','g1','u1','ACTIVE',0),('3','g1','u2','ACTIVE',0),('4','g2','u1','ACTIVE',0),('5','g0','u0','EXPIRED',0),('6','g0','u0','ACTIVE',1)");
            for (String first : new String[]{null, ""}) {
                assertEquals(List.of(new EmbedSessionCounterObservationRow("g1", "u1", 2, 2)), mapper.inspectStoredCounterPage(first, "", 1));
                assertEquals(List.of(new EmbedSessionCounterObservationRow("g1", "u1", 2, 2)), mapper.inspectActiveSessionPairPage(first, "", 1));
            }
            assertEquals(List.of(new EmbedSessionCounterObservationRow("g1", "u2", 9, 1), new EmbedSessionCounterObservationRow("g3", "u1", 2, 0)), mapper.inspectStoredCounterPage("g1", "u1", 10));
            assertEquals(List.of(new EmbedSessionCounterObservationRow("g1", "u2", 9, 1), new EmbedSessionCounterObservationRow("g2", "u1", null, 1)), mapper.inspectActiveSessionPairPage("g1", "u1", 10));
            assertTrue(mapper.inspectStoredCounterPage("g3", "u1", 10).isEmpty());
            assertTrue(mapper.inspectActiveSessionPairPage("g2", "u1", 10).isEmpty());
            assertTrue(mapper.inspectStoredCounterPage("z' OR 1=1 --", "", 10).isEmpty());
            assertEquals(3, h.jdbc.queryForObject("SELECT COUNT(*) FROM embed_session_counter", Integer.class));
            assertEquals(6, h.jdbc.queryForObject("SELECT COUNT(*) FROM embed_session", Integer.class));
        }
    }

    @Test void runtimeLeaseReleaseKeepsEmptyOpenApiScopeAndTransactionRollback() {
        try (var f = new Fixture()) {
            f.table("integration_api_request_lease", "lease_id VARCHAR(64) PRIMARY KEY,scope_key VARCHAR(200)");
            var h = new Harness(f, EmbedTrafficControlMapper.class); var mapper = h.mapper(EmbedTrafficControlMapper.class);
            h.jdbc.update("INSERT INTO integration_api_request_lease VALUES ('empty',''),('null',NULL),('spaces','   '),('embed','embed-runtime-grant-v1:g')");
            for (String id : List.of("empty", "null", "spaces", "x' OR 1=1 --")) assertEquals(0, mapper.releaseRuntimeLease(id));
            h.tx.executeWithoutResult(status -> { assertEquals(1, mapper.releaseRuntimeLease("embed")); status.setRollbackOnly(); });
            assertEquals(4, h.jdbc.queryForObject("SELECT COUNT(*) FROM integration_api_request_lease", Integer.class));
            assertEquals(1, mapper.releaseRuntimeLease("embed"));
            assertEquals(0, mapper.releaseRuntimeLease("embed"));
        }
    }

    @Test void positionTypeOptionalFilterKeepsEnabledDeletedAndAnyTypeRules() {
        try (var f = new Fixture()) {
            // Wrapper 按实体映射读取全部列，夹具保留完整结构，数据仍只填写本场景所需字段。
            f.table("sys_position", "id VARCHAR(64),position_code VARCHAR(64),position_name VARCHAR(128),status VARCHAR(32),deleted INT,applicable_unit_type VARCHAR(64),sort_order INT,holder_mode VARCHAR(32),built_in INT,description TEXT,revision INT,created_by VARCHAR(64),updated_by VARCHAR(64),create_time DATETIME,update_time DATETIME");
            var h = new Harness(f, SysPositionMapper.class); var mapper = h.mapper(SysPositionMapper.class);
            h.jdbc.update("INSERT INTO sys_position(id,position_code,status,deleted,applicable_unit_type,sort_order) VALUES ('a','ANY','ENABLED',0,'ANY',0),('b','ORG','ENABLED',0,'ORG',1),('c','TEAM','ENABLED',0,'TEAM',2),('off','OFF','DISABLED',0,'ANY',3),('deleted','DEL','ENABLED',1,'ANY',4)");
            for (String type : new String[]{null, "", "   "}) assertEquals(3, mapper.selectEnabled(type).size());
            assertEquals(List.of("a"), mapper.selectEnabled("\t").stream().map(row -> row.getId()).toList());
            assertEquals(List.of("a", "b"), mapper.selectEnabled("ORG").stream().map(row -> row.getId()).toList());
            assertEquals(List.of("a"), mapper.selectEnabled("x' OR 1=1 --").stream().map(row -> row.getId()).toList());
        }
    }

    private static List<String> ids(Harness h, String predicate) {
        return h.jdbc.queryForList("SELECT id FROM biz_state WHERE deleted=0 AND (" + predicate + ") ORDER BY id", String.class);
    }

    /** 执行权限编译器返回的参数 SQL，不在测试中把值还原为字面量。 */
    private static List<String> boundIds(Harness h, String predicate, Map<String, Object> parameters) {
        var config = h.session.getConfiguration();
        var source = new org.apache.ibatis.scripting.xmltags.XMLLanguageDriver().createSqlSource(config,
                "SELECT id FROM biz_state WHERE deleted=0 AND (" + predicate + ") ORDER BY id", Map.class);
        var values = Map.of("permissionParameters", parameters);
        var bound = source.getBoundSql(values);
        Object[] args = bound.getParameterMappings().stream()
                .map(parameter -> config.newMetaObject(values).getValue(parameter.getProperty())).toArray();
        return h.jdbc.queryForList(bound.getSql(), String.class, args);
    }

    /** 所有 JOIN 目标均注册为随机表，防止待办测试读取真实引擎或业务记录。 */
    private static void taskTables(Fixture f) {
        f.table("process_task", "id VARCHAR(64),task_id VARCHAR(64),assignee_id VARCHAR(200),assignee_type VARCHAR(16),status VARCHAR(16),deleted INT,create_time DATETIME,node_type VARCHAR(32),process_instance_id VARCHAR(64),entity_code VARCHAR(64),entity_data_id VARCHAR(64)");
        f.table("ACT_RU_TASK", "ID_ VARCHAR(64),ASSIGNEE_ VARCHAR(64),PROC_INST_ID_ VARCHAR(64)");
        f.table("ACT_RU_IDENTITYLINK", "TASK_ID_ VARCHAR(64),TYPE_ VARCHAR(32),USER_ID_ VARCHAR(64),GROUP_ID_ VARCHAR(64)");
        f.table("sys_user", "id VARCHAR(64),username VARCHAR(64),status CHAR(1),deleted INT");
        f.table("sys_group", "id VARCHAR(64),group_code VARCHAR(64),status CHAR(1),deleted INT");
        f.table("sys_role", "id VARCHAR(64),role_code VARCHAR(64),status CHAR(1),deleted INT");
        f.table("sys_user_group", "user_id VARCHAR(64),group_id VARCHAR(64)");
        f.table("sys_user_role", "user_id VARCHAR(64),role_id VARCHAR(64)");
        f.table("process_task_add_sign", "id VARCHAR(64),source_task_id VARCHAR(64),process_instance_id VARCHAR(64),status VARCHAR(32)");
        f.table("process_task_add_sign_user", "add_sign_id VARCHAR(64),generated_task_id VARCHAR(64),user_id VARCHAR(64),status VARCHAR(32)");
    }
}
