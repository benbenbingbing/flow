package com.workflow.entity.data;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionAssignmentMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeDelegationMapper;
import com.workflow.process.assignment.application.*;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaEventMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.*;
import com.workflow.storage.application.StoredFileAccessService;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import static com.workflow.entity.data.MySqlRemainingConflictDatabaseTest.table;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 日期适配的 MySQL 实库回归；会话时区和 UTC 分开验证，表名全部随机隔离。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlRuntimeTimeDatabaseTest {
    private static final DatabaseQueryDialect QUERY = DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL);

    @Test void clockExpressionsMatchLegacyMysqlAcrossTimezonesAndSignedOffsets() {
        try (var f = new Fixture()) {
            var h = new Harness(f);
            var dialect = DatabaseDialects.runtime(DatabaseVendor.MYSQL);
            h.tx.executeWithoutResult(status -> {
                for (String zone : List.of("+00:00", "+09:00", "-07:00")) {
                    h.jdbc.execute("SET time_zone = '" + zone + "'");
                    assertTrue(h.jdbc.queryForObject("SELECT " + dialect.currentTimestampExpression() + " = NOW()", Boolean.class));
                    assertTrue(h.jdbc.queryForObject("SELECT " + dialect.utcTimestampExpression() + " = UTC_TIMESTAMP(6)", Boolean.class));
                    for (long seconds : new long[]{-2592000, -1, 0, 1, 86400, 2592000}) {
                        assertTrue(h.jdbc.queryForObject("SELECT " + dialect.currentAfterSeconds("?")
                                + " = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND)", Boolean.class, seconds, seconds));
                        assertTrue(h.jdbc.queryForObject("SELECT " + dialect.utcAfterSeconds("?")
                                + " = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? SECOND)", Boolean.class, seconds, seconds));
                    }
                    assertTrue(h.jdbc.queryForObject("SELECT " + dialect.currentAfterSeconds("-2592000")
                            + " = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 DAY)", Boolean.class));
                }
            });
        }
    }

    @Test void slaClaimRetryRecoveryAndCancellationKeepStateAndTokenGuards() throws Exception {
        try (var f = new Fixture()) {
            slaEventTable(f);
            var h = new Harness(f, ProcessTaskSlaEventMapper.class);
            var mapper = h.mapper(ProcessTaskSlaEventMapper.class);
            for (String id : List.of("a", "b", "future", "later-retry")) {
                h.jdbc.update("INSERT INTO process_task_sla_event(id,sla_id,metric_type,status,trigger_at,create_time,next_retry_time) "
                        + "VALUES (?,'sla','RESPONSE','PENDING',TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)), '2026-01-01', ?)", id,
                        id.equals("future") ? 86400 : -86400,
                        id.equals("later-retry") ? h.jdbc.queryForObject("SELECT TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(6))", LocalDateTime.class) : null);
            }
            assertEquals(List.of("a", "b"), mapper.findReady(2).stream().map(row -> row.getId()).toList());
            assertEquals(0, mapper.claim("future", "worker", 120));
            assertEquals(0, mapper.claim("later-retry", "worker", 120));
            var results = concurrent(6, index -> mapper.claim("a", "worker-" + index, 120));
            assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
            String owner = h.jdbc.queryForObject("SELECT owner_id FROM process_task_sla_event WHERE id='a'", String.class);
            assertEquals(0, mapper.markSuccess("a", "wrong", 1, "{}"));
            assertEquals(0, mapper.markFailure("a", owner, 0, "DEAD", 0, "wrong token"));
            assertEquals(0, mapper.recoverExpiredLease("a"));
            h.jdbc.update("UPDATE process_task_sla_event SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE id='a'");
            assertNull(mapper.selectClaimed("a", owner));
            assertEquals(List.of("a"), mapper.findExpiredLeaseIds());
            assertEquals(1, mapper.recoverExpiredLeases());
            assertEquals(1, mapper.claim("a", "retry-owner", 120));
            assertEquals(0, mapper.markSuccess("a", owner, 1, "old"));
            assertEquals(1, mapper.markFailure("a", "retry-owner", 2, "FAILED", 60, "retry"));
            assertEquals(1, h.jdbc.queryForObject("SELECT attempts FROM process_task_sla_event WHERE id='a'", Integer.class));
            assertTrue(h.jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,update_time,next_retry_time)=60 FROM process_task_sla_event WHERE id='a'", Boolean.class));
            assertEquals(0, mapper.claim("a", "early", 120));
            h.jdbc.update("UPDATE process_task_sla_event SET next_retry_time=NULL WHERE id='a'");
            assertEquals(1, mapper.claim("a", "dead-owner", 120));
            assertEquals(1, mapper.markFailure("a", "dead-owner", 3, "DEAD", 60, "terminal"));
            assertNull(h.jdbc.queryForObject("SELECT next_retry_time FROM process_task_sla_event WHERE id='a'", LocalDateTime.class));
            assertNotNull(h.jdbc.queryForObject("SELECT finished_at FROM process_task_sla_event WHERE id='a'", LocalDateTime.class));
            assertEquals(1, mapper.claim("b", "success", 120));
            assertEquals(2, mapper.cancelPendingByMetric("sla", "RESPONSE"));
            assertEquals(1, mapper.markSuccess("b", "success", 1, "{}"));
            assertEquals(0, mapper.markSuccess("b", "success", 1, "{}"));
            assertEquals(0, mapper.cancelPendingBySlaId("sla"));
            assertEquals(Set.of("DEAD", "SUCCEEDED", "CANCELLED"), Set.copyOf(h.jdbc.queryForList("SELECT status FROM process_task_sla_event", String.class)));
        }
    }

    @Test void positionWritesKeepUtcTimestampsOptimisticRevisionAndRevocationGuards() {
        try (var f = new Fixture()) {
            f.table("sys_position", "id VARCHAR(64) PRIMARY KEY, position_name VARCHAR(100), applicable_unit_type VARCHAR(32), holder_mode VARCHAR(32), sort_order INT, description TEXT, updated_by VARCHAR(64), revision INT, deleted INT, status VARCHAR(32), update_time DATETIME(6)");
            f.table("sys_position_assignment", "id VARCHAR(64) PRIMARY KEY, effective_from DATETIME(6), effective_to DATETIME(6), revoked_at DATETIME(6), revoked_by VARCHAR(64), revoke_reason TEXT, updated_by VARCHAR(64), revision INT, update_time DATETIME(6)");
            f.table("sys_organization", "id VARCHAR(64) PRIMARY KEY, leader_id VARCHAR(64), leader_name VARCHAR(100), deleted INT, update_time DATETIME(6)");
            var h = new Harness(f, SysPositionMapper.class, SysPositionAssignmentMapper.class, SysOrganizationMapper.class);
            h.jdbc.update("INSERT INTO sys_position(id,revision,deleted,status) VALUES ('p',1,0,'ENABLED')");
            h.jdbc.update("INSERT INTO sys_position_assignment(id,revision) VALUES ('a',1)");
            h.jdbc.update("INSERT INTO sys_organization(id,deleted) VALUES ('o',0),('deleted',1)");
            h.tx.executeWithoutResult(status -> {
                h.jdbc.execute("SET time_zone = '+09:00'");
                var positions = h.mapper(SysPositionMapper.class); var assignments = h.mapper(SysPositionAssignmentMapper.class);
                assertEquals(1, positions.updateDefinition("p", "Position", "TEAM", "SINGLE", 1, "notes", "actor", 1));
                assertEquals(0, positions.updateStatus("p", "DISABLED", "stale", 1));
                assertEquals(1, positions.updateStatus("p", "DISABLED", "actor", 2));
                assertEquals(1, positions.softDelete("p", "actor", 3));
                assertEquals(0, positions.updateStatus("p", "ENABLED", "actor", 4));
                LocalDateTime time = LocalDateTime.of(2026, 9, 22, 0, 0);
                assertEquals(1, assignments.updatePeriod("a", time, time.plusDays(2), "actor", 1));
                assertEquals(0, assignments.closeAt("a", time, "stale", 1));
                assertEquals(1, assignments.closeAt("a", time.plusDays(1), "actor", 2));
                assertEquals(1, assignments.revoke("a", time, time, "reason", "actor", 3));
                assertEquals(0, assignments.updatePeriod("a", time, time.plusDays(2), "actor", 4));
                assertEquals(1, h.mapper(SysOrganizationMapper.class).updateLeaderProjection("o", "user", "User"));
                assertEquals(0, h.mapper(SysOrganizationMapper.class).updateLeaderProjection("deleted", "user", "User"));
                utcUpdated(h, "sys_position", "p"); utcUpdated(h, "sys_position_assignment", "a"); utcUpdated(h, "sys_organization", "o");
            });
        }
    }

    @Test void taskSlaAndProcessLinkUpdatesKeepUtcClockAndLifecycleGuards() {
        try (var f = new Fixture()) {
            f.table("process_task_sla", "id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64) UNIQUE, current_assignee_id VARCHAR(64), version INT, overall_status VARCHAR(30), update_time DATETIME(6)");
            f.table("process_task", "id BIGINT PRIMARY KEY, task_id VARCHAR(64) UNIQUE, due_time DATETIME(6), response_due_time DATETIME(6), sla_status VARCHAR(30), deleted INT, update_time DATETIME(6), status VARCHAR(30), action VARCHAR(30), comment TEXT, end_time DATETIME(6), duration BIGINT");
            f.table("entity_process_link", "id VARCHAR(64) PRIMARY KEY, request_id VARCHAR(64), process_instance_id VARCHAR(64), state VARCHAR(30), entity_status VARCHAR(30), version INT, update_time DATETIME(6), ended_at DATETIME(6)");
            var h = new Harness(f, ProcessTaskSlaMapper.class, ProcessTaskMapper.class, EntityProcessLinkMapper.class);
            h.jdbc.update("INSERT INTO process_task_sla VALUES ('s','task','old',1,'RUNNING',NULL),('closed','closed-task','old',1,'COMPLETED',NULL)");
            h.jdbc.update("INSERT INTO process_task(id,task_id,deleted) VALUES (1,'task',0),(2,'deleted-task',1)");
            h.jdbc.update("INSERT INTO entity_process_link(id,request_id,state,version) VALUES ('l','request','PENDING',1)");
            h.tx.executeWithoutResult(status -> {
                h.jdbc.execute("SET time_zone = '+09:00'");
                assertEquals(1, h.mapper(ProcessTaskSlaMapper.class).updateAssignee("task", "new"));
                assertEquals(0, h.mapper(ProcessTaskSlaMapper.class).updateAssignee("closed-task", "new"));
                var due = LocalDateTime.of(2026, 9, 22, 8, 0);
                assertEquals(1, h.mapper(ProcessTaskMapper.class).updateSlaSummary("task", due, due.plusHours(1), "RUNNING"));
                assertEquals(0, h.mapper(ProcessTaskMapper.class).updateSlaSummary("deleted-task", due, due, "RUNNING"));
                var links = h.mapper(EntityProcessLinkMapper.class);
                assertEquals(0, links.activate("l", "wrong-request", "instance"));
                assertEquals(1, links.activate("l", "request", "instance"));
                assertEquals(1, links.updateActiveStatus("instance", "APPROVED"));
                assertEquals(1, links.closeActive("instance", null));
                assertEquals(0, links.updateActiveStatus("instance", "REJECTED"));
                assertEquals("APPROVED", h.jdbc.queryForObject("SELECT entity_status FROM entity_process_link", String.class));
                utcUpdated(h, "process_task_sla", "s"); utcUpdated(h, "process_task", "1"); utcUpdated(h, "entity_process_link", "l");
                // 同一任务的业务完成时间沿用会话时区，不能被 UTC SLA 汇总时钟覆盖。
                assertEquals(1, h.mapper(ProcessTaskMapper.class).completeTask(1L, "done", "APPROVE", "done", 10L));
                assertTrue(h.jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,end_time,CURRENT_TIMESTAMP)) < 2 FROM process_task WHERE id=1", Boolean.class));
            });
        }
    }

    @Test void localTimeDelegationReadAndSequenceWritesKeepTheirOriginalTimezone() {
        try (var f = new Fixture()) {
            f.table("process_cc_record", "id VARCHAR(64) PRIMARY KEY, cc_user_id VARCHAR(64), read_status VARCHAR(16), read_time DATETIME(6), deleted INT");
            f.table("entity_code_rule", "entity_code VARCHAR(64) PRIMARY KEY, current_seq INT, seq_date VARCHAR(32), update_time DATETIME(6)");
            f.table("process_task_add_sign_user", "id VARCHAR(64) PRIMARY KEY, generated_task_id VARCHAR(64) UNIQUE, status VARCHAR(32), complete_time DATETIME(6)");
            f.table("process_task_add_sign", "id VARCHAR(64) PRIMARY KEY, status VARCHAR(32), complete_time DATETIME(6)");
            MySqlScopeWrapperDatabaseTest.delegationTable(f);
            var h = new Harness(f, ProcessCcRecordMapper.class, EntityCodeRuleMapper.class, ProcessTaskAddSignUserMapper.class, ProcessTaskAddSignMapper.class, EntityListScopeDelegationMapper.class);
            h.tx.executeWithoutResult(status -> {
                h.jdbc.execute("SET time_zone = '+09:00'");
                h.jdbc.update("INSERT INTO process_cc_record VALUES ('cc','user','UNREAD',NULL,0),('other','other','UNREAD',NULL,0),('deleted','user','UNREAD',NULL,1)");
                h.jdbc.update("INSERT INTO entity_code_rule VALUES ('asset',1,'20260921',NULL)");
                h.jdbc.update("INSERT INTO process_task_add_sign_user VALUES ('su','task','TODO',NULL)");
                h.jdbc.update("INSERT INTO process_task_add_sign VALUES ('sign','ACTIVE',NULL)");
                h.jdbc.update("INSERT INTO entity_list_scope_delegation (id,to_user_id,entity_code,enabled,deleted,start_time,end_time) VALUES ('active','user','asset',1,0,DATE_SUB(NOW(),INTERVAL 1 HOUR),DATE_ADD(NOW(),INTERVAL 1 HOUR)),"
                        + "('expired','user','asset',1,0,NULL,DATE_SUB(NOW(),INTERVAL 1 HOUR)),('future','user','asset',1,0,DATE_ADD(NOW(),INTERVAL 1 HOUR),NULL)");
                assertEquals(List.of("active"), h.mapper(EntityListScopeDelegationMapper.class).findActiveByToUserId("user", "asset").stream().map(row -> row.getId()).toList());
                assertEquals(0, h.mapper(ProcessCcRecordMapper.class).markAsRead("cc", "wrong"));
                assertEquals(1, h.mapper(ProcessCcRecordMapper.class).markAsRead("cc", "user"));
                assertEquals(0, h.mapper(ProcessCcRecordMapper.class).markAllAsRead("user"));
                var rules = h.mapper(EntityCodeRuleMapper.class);
                assertEquals(1, rules.updateSeqWithDate("asset", "20260921", "20260922", 1));
                assertEquals(0, rules.updateSeq("asset", "20260922", 0, 2));
                assertEquals(1, rules.updateSeq("asset", "20260922", 1, 2));
                assertEquals(1, h.mapper(ProcessTaskAddSignUserMapper.class).completeByGeneratedTaskId("task"));
                assertEquals(0, h.mapper(ProcessTaskAddSignUserMapper.class).completeByGeneratedTaskId("task"));
                assertEquals(1, h.mapper(ProcessTaskAddSignMapper.class).cancel("sign"));
                assertEquals(0, h.mapper(ProcessTaskAddSignMapper.class).cancel("sign"));
                for (String column : List.of("SELECT read_time FROM process_cc_record WHERE id='cc'", "SELECT update_time FROM entity_code_rule", "SELECT complete_time FROM process_task_add_sign_user", "SELECT complete_time FROM process_task_add_sign")) {
                    assertTrue(h.jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,(" + column + "),CURRENT_TIMESTAMP)) < 2", Boolean.class));
                    assertTrue(h.jdbc.queryForObject("SELECT TIMESTAMPDIFF(HOUR,UTC_TIMESTAMP(),(" + column + ")) >= 8", Boolean.class));
                }
            });
        }
    }

    @Test void incidentMetricsAndBothRetryBranchesMatchDatabaseLocalTime() throws Exception {
        try (var f = new Fixture()) {
            table(f, "process_assignee_incident", "V054__empty_assignee_policy_incident.sql");
            table(f, "process_assignee_incident_action", "V054__empty_assignee_policy_incident.sql");
            var h = new Harness(f);
            var tasks = mock(TaskService.class); var runtime = mock(RuntimeService.class); var resolver = mock(AssigneeResolutionService.class);
            var task = mock(Task.class); when(task.getId()).thenReturn("task");
            var query = mock(TaskQuery.class); when(tasks.createTaskQuery()).thenReturn(query); when(query.taskId("task")).thenReturn(query); when(query.singleResult()).thenReturn(task);
            when(runtime.getVariables(anyString())).thenReturn(Map.of());
            when(resolver.resolveConfigured(anyString(), any())).thenReturn(AssigneeResolutionResult.empty("EMPTY", "No candidate", "resolver"));
            var service = new AssigneeIncidentService(h.jdbc, h.json, tasks, runtime, resolver, QUERY, h.attempt);
            h.tx.executeWithoutResult(status -> {
                h.jdbc.execute("SET time_zone = '+09:00'");
                for (String id : List.of("task", "multi", "old")) {
                    h.jdbc.update("INSERT INTO process_assignee_incident(id,process_instance_id,task_id,node_id,policy,status,empty_reason_code,responsibility_owner,resolver_code,initial_delay_seconds,backoff_multiplier,max_retries,create_time) "
                            + "VALUES (?,?,?,?,?,'OPEN','EMPTY','ops','resolver',30,2,3,TIMESTAMPADD(DAY,?,CURRENT_TIMESTAMP))", id, "instance-"+id,
                            id.equals("task") ? id : null, "node-"+id, id.equals("old") ? "OLD_POLICY" : "WAIT_AND_RETRY", id.equals("old") ? -31 : -1);
                }
                for (String id : List.of("task", "multi")) {
                    var request = new AssigneeIncidentHandleRequest(); request.setAction("RETRY_RESOLVER"); request.setRequestId("first");
                    service.handle(id, request);
                    assertEquals(1, h.jdbc.queryForObject("SELECT retry_count FROM process_assignee_incident WHERE id=?", Integer.class, id));
                    assertTrue(h.jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,update_time,next_retry_at)=60 FROM process_assignee_incident WHERE id=?", Boolean.class, id));
                    assertEquals("RETRY_SCHEDULED", h.jdbc.queryForObject("SELECT status FROM process_assignee_incident WHERE id=?", String.class, id));
                    service.handle(id, request); // 同一请求不重新延长时间或再递增重试次数。
                    assertEquals(1, h.jdbc.queryForObject("SELECT retry_count FROM process_assignee_incident WHERE id=?", Integer.class, id));
                }
                var metrics = service.metrics();
                assertEquals(0L, metrics.get("overdueRetries"));
                assertFalse(metrics.get("policyCounts").toString().contains("OLD_POLICY"));
                assertTrue(metrics.get("policyCounts").toString().contains("WAIT_AND_RETRY"));
            });
        }
    }

    @Test void fileDeletionUsesUtcAndLeavesOtherRowsAndOuterRollbackIntact() {
        try (var f = new Fixture()) {
            f.table("storage_file_object", "id VARCHAR(64) PRIMARY KEY, storage_url VARCHAR(200) UNIQUE, deleted INT, update_time DATETIME(6)");
            var h = new Harness(f);
            h.jdbc.update("INSERT INTO storage_file_object VALUES ('a','url-a',0,NULL),('b','url-b',0,NULL)");
            var service = new StoredFileAccessService(h.jdbc, mock(CurrentUserRoleService.class), h.attempt, QUERY);
            h.tx.executeWithoutResult(status -> {
                h.jdbc.execute("SET time_zone = '+09:00'"); service.markDeleted("url-a"); utcUpdated(h,"storage_file_object","a");
                assertEquals(0, h.jdbc.queryForObject("SELECT deleted FROM storage_file_object WHERE id='b'", Integer.class));
                status.setRollbackOnly();
            });
            assertEquals(0, h.jdbc.queryForObject("SELECT deleted FROM storage_file_object WHERE id='a'", Integer.class));
            service.markDeleted("url-a");
            assertEquals(1, h.jdbc.queryForObject("SELECT deleted FROM storage_file_object WHERE id='a'", Integer.class));
        }
    }

    @Test void embedApplicationAndCredentialExpiryUseUtcEvenInAnotherSessionTimezone() throws Exception {
        try (var f = new Fixture()) {
            f.table("integration_application", "id VARCHAR(64) PRIMARY KEY, application_name VARCHAR(100), client_id VARCHAR(64), status VARCHAR(32), expires_at DATETIME(6)");
            f.table("integration_application_credential", "application_id VARCHAR(64) PRIMARY KEY, status VARCHAR(32), expires_at DATETIME(6)");
            // 通过真实 Mapper 代理访问包内接口，确保默认方法创建的 MP 分页参数不会被绕过。
            String namespace = "com.workflow.embed.management.infrastructure.persistence.EmbedManagementMapper";
            var h = new Harness(f, Class.forName(namespace));
            h.tx.executeWithoutResult(status -> {
                h.jdbc.execute("SET time_zone = '+09:00'");
                for (String id : List.of("active", "expired", "disabled", "credential-expired", "credential-missing")) {
                    h.jdbc.update("INSERT INTO integration_application VALUES (?,?,?, ?,TIMESTAMPADD(HOUR,?,UTC_TIMESTAMP(6)))", id, id, id,
                            id.equals("disabled") ? "DISABLED" : "ACTIVE", id.equals("expired") ? -1 : 1);
                    if (!id.equals("credential-missing")) h.jdbc.update("INSERT INTO integration_application_credential VALUES (?,'ACTIVE',TIMESTAMPADD(HOUR,?,UTC_TIMESTAMP(6)))",
                            id, id.equals("credential-expired") ? -1 : 1);
                }
                assertEquals(Boolean.TRUE, MapperMethodCalls.<Boolean>call(h.session, namespace + ".applicationExistsAndEnabled", Map.of("applicationId", "active")));
                assertEquals(Boolean.FALSE, MapperMethodCalls.<Boolean>call(h.session, namespace + ".applicationExistsAndEnabled", Map.of("applicationId", "expired")));
                assertEquals(Boolean.FALSE, MapperMethodCalls.<Boolean>call(h.session, namespace + ".applicationExistsAndEnabled", Map.of("applicationId", "disabled")));
                List<?> rows = MapperMethodCalls.call(h.session, namespace + ".findApplicationOptions", Map.of("keyword", "", "status", "", "limit", 20, "offset", 0));
                assertEquals(5, rows.size());
                for (Object row : rows) {
                    var json = h.json.valueToTree(row);
                    assertEquals(json.get("id").asText().equals("active"), json.get("embedLaunchReady").asBoolean());
                }
            });
        }
    }

    private static void utcUpdated(Harness h, String table, String id) {
        assertTrue(h.jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,update_time,UTC_TIMESTAMP(6))) < 3 FROM " + table + " WHERE id=?", Boolean.class, id));
    }

    private static void slaEventTable(Fixture f) {
        f.table("process_task_sla_event", "id VARCHAR(64) PRIMARY KEY, sla_id VARCHAR(64), metric_type VARCHAR(32), status VARCHAR(32), trigger_at DATETIME(6), create_time DATETIME(6), update_time DATETIME(6), "
                + "owner_id VARCHAR(100), lease_token BIGINT NOT NULL DEFAULT 0, lease_until DATETIME(6), next_retry_time DATETIME(6), started_at DATETIME(6), finished_at DATETIME(6), "
                + "attempts INT NOT NULL DEFAULT 0, error_message TEXT, result_json TEXT, KEY(status,trigger_at,id)");
    }
}
