package com.workflow.entity.data;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort;
import com.workflow.embed.infrastructure.persistence.adapter.MyBatisEmbedLaunchPersistenceAdapter;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedLaunchPersistenceMapper;
import com.workflow.embed.management.domain.EmbedManagementModel.*;
import com.workflow.embed.management.infrastructure.persistence.MyBatisEmbedManagementRepository;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import com.workflow.process.assignment.application.*;
import com.workflow.process.cc.application.*;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.storage.application.FileUploadIdempotencyException;
import com.workflow.storage.application.StoredFileAccessService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 业务冲突的真实事务边界；所有生产 Mapper/JDBC 表名都重写为随机测试表。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlRemainingConflictDatabaseTest {
    private static final String EMBED_SCHEMA = "V067__embed_views_identity_and_grants.sql";
    private static final String INCIDENT_SCHEMA = "V054__empty_assignee_policy_incident.sql";
    private static final String CC_CONFIG = """
            {"enabled":true,"timings":["TASK_COMPLETE"],"channels":["IN_APP"],
             "recipientRules":[{"type":"USER","values":["observer"]}]}
            """;

    @Test void ccDuplicateStaysInsideRequiredBoundaryAndDoesNotPublishAgain() throws Exception {
        try (var f = new Fixture()) {
            ccTables(f); effectTable(f);
            var h = new Harness(f, ProcessCcRecordMapper.class);
            var notifications = mock(ProcessCcNotificationPublisher.class);
            doAnswer(call -> { h.jdbc.update("INSERT INTO conflict_effect VALUES ('notification')"); return null; })
                    .when(notifications).enqueue(any(), anyList());
            var runtime = ccRuntime(h, notifications);
            var results = concurrent(6, index -> h.tx.execute(status -> {
                int created = runtime.trigger(context("same"), CC_CONFIG);
                h.jdbc.update("INSERT INTO conflict_effect VALUES (?)", "worker-" + index);
                return created;
            }));
            assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM process_cc_record", Integer.class));
            assertEquals(7, h.jdbc.queryForObject("SELECT COUNT(*) FROM conflict_effect", Integer.class));
            verify(notifications, times(1)).enqueue(any(), anyList());
            // 已删除历史记录仍持有幂等键，不能重复发通知，也不能让外层事务意外回滚。
            h.jdbc.update("UPDATE process_cc_record SET deleted=1");
            h.tx.executeWithoutResult(status -> {
                assertEquals(0, runtime.trigger(context("same"), CC_CONFIG));
                h.jdbc.update("INSERT INTO conflict_effect VALUES ('after-deleted-replay')");
            });
        }
    }

    @Test void notificationDuplicateIsAnActualFailureAndRollsBackTheNewCc() throws Exception {
        try (var f = new Fixture()) {
            ccTables(f); effectTable(f);
            var h = new Harness(f, ProcessCcRecordMapper.class);
            var notifications = mock(ProcessCcNotificationPublisher.class);
            doAnswer(call -> {
                h.jdbc.update("INSERT INTO conflict_effect VALUES ('notification')");
                throw new DuplicateKeyException("notification store failed");
            }).when(notifications).enqueue(any(), anyList());
            var runtime = ccRuntime(h, notifications);
            assertThrows(IllegalArgumentException.class, () -> h.tx.execute(status -> {
                h.jdbc.update("INSERT INTO conflict_effect VALUES ('business')");
                return runtime.trigger(context("failure"), CC_CONFIG);
            }));
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM process_cc_record", Integer.class));
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM conflict_effect", Integer.class));
        }
    }

    @Test void incidentCreationRaceReplaysOneIdFromAnOldSnapshot() throws Exception {
        try (var f = new Fixture()) {
            incidentTables(f);
            var h = new Harness(f);
            // 让每个独立事务先看到空快照，再同时插入，确保覆盖冲突后的当前读。
            var racing = barrierJdbc(h, "WHERE open_slot =", 6);
            var recorder = h.transactional(new AssigneeIncidentRecorder(racing, h.json,
                    DatabaseQueryDialects.forDatabaseId("MYSQL"), h.attempt));
            var ids = concurrent(6, index -> recorder.create(incident("task")));
            assertEquals(1, ids.stream().distinct().count());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM process_assignee_incident", Integer.class));
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM process_assignee_incident_action", Integer.class));
        }
    }

    @Test void incidentAuditFailureRollsBackCreationButRequiresNewEvidenceSurvivesOuterRollback() throws Exception {
        try (var f = new Fixture()) {
            incidentTables(f);
            var h = new Harness(f);
            var recorder = h.transactional(new AssigneeIncidentRecorder(h.jdbc, h.json,
                    DatabaseQueryDialects.forDatabaseId("MYSQL"), h.attempt));
            String actions = f.tables.get("process_assignee_incident_action");
            f.jdbc.execute("ALTER TABLE " + actions + " ADD CONSTRAINT guard_" + f.suffix + " CHECK (action_type='FORBIDDEN')");
            assertThrows(DataAccessException.class, () -> recorder.create(incident(null)));
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM process_assignee_incident", Integer.class));
            f.jdbc.execute("ALTER TABLE " + actions + " DROP CHECK guard_" + f.suffix);
            h.tx.executeWithoutResult(status -> { recorder.create(incident(null)); status.setRollbackOnly(); });
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM process_assignee_incident", Integer.class));
            h.tx.executeWithoutResult(status -> { recorder.resolveOpenNodeEntry("instance", "node"); status.setRollbackOnly(); });
            assertEquals("OPEN", h.jdbc.queryForObject("SELECT status FROM process_assignee_incident", String.class));
        }
    }

    @Test void competingIncidentActionsInvokeTheEngineOnceAndKeepOtherWork() throws Exception {
        try (var f = new Fixture()) {
            incidentTables(f); effectTable(f);
            var h = new Harness(f);
            var recorder = h.transactional(new AssigneeIncidentRecorder(h.jdbc, h.json,
                    DatabaseQueryDialects.forDatabaseId("MYSQL"), h.attempt));
            String incidentId = recorder.create(incident("task"));
            var engine = mock(RuntimeService.class);
            var instances = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
            when(engine.createProcessInstanceQuery()).thenReturn(instances);
            when(instances.processInstanceId("instance")).thenReturn(instances);
            when(instances.singleResult()).thenReturn(mock(org.flowable.engine.runtime.ProcessInstance.class));
            doAnswer(call -> { h.jdbc.update("INSERT INTO conflict_effect VALUES ('engine')"); return null; })
                    .when(engine).deleteProcessInstance(eq("instance"), anyString());
            var service = new AssigneeIncidentService(barrierJdbc(h, "FROM process_assignee_incident WHERE id", 6), h.json,
                    mock(TaskService.class), engine, mock(AssigneeResolutionService.class),
                    DatabaseQueryDialects.forDatabaseId("MYSQL"), h.attempt);
            concurrent(6, index -> h.tx.execute(status -> {
                var request = new AssigneeIncidentHandleRequest(); request.setRequestId("same-action");
                request.setAction("TERMINATE_INSTANCE"); request.setReason("test");
                assertEquals(incidentId, service.handle(incidentId, request).get("id"));
                h.jdbc.update("INSERT INTO conflict_effect VALUES (?)", "worker-" + index);
                return true;
            }));
            verify(engine, times(1)).deleteProcessInstance(eq("instance"), anyString());
            assertEquals("TERMINATED", h.jdbc.queryForObject("SELECT status FROM process_assignee_incident", String.class));
            assertEquals(7, h.jdbc.queryForObject("SELECT COUNT(*) FROM conflict_effect", Integer.class));
        }
    }

    @Test void fileRegistrationRacesReplayFirstObjectAndOuterRollbackRemovesOnlyNewRegistration() throws Exception {
        try (var f = new Fixture()) {
            table(f, "storage_file_object", "V011__storage_object_ownership.sql");
            alter(f, "storage_file_object", "V018__storage_upload_idempotency.sql"); effectTable(f);
            var h = new Harness(f);
            var service = h.transactional(new StoredFileAccessService(h.jdbc, mock(CurrentUserRoleService.class), h.attempt,
                    DatabaseQueryDialects.forDatabaseId("MYSQL")));
            var file = new MockMultipartFile("file", "name.txt", "text/plain", "same content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var barrier = new CyclicBarrier(6);
            var results = concurrent(6, index -> h.tx.execute(status -> {
                UserContext.setCurrentUser("owner", "owner");
                try {
                    var claim = service.prepareUpload("same-key", file);
                    await(barrier);
                    var registration = service.register(Map.of("url", "url-" + index, "filename", "key-" + index), file, claim);
                    h.jdbc.update("INSERT INTO conflict_effect VALUES (?)", "worker-" + index);
                    return registration;
                } finally { UserContext.clear(); }
            }));
            assertEquals(1, results.stream().filter(StoredFileAccessService.UploadRegistration::currentObjectRegistered).count());
            assertEquals(1, results.stream().map(value -> value.response().get("url")).distinct().count());
            assertEquals(6, h.jdbc.queryForObject("SELECT COUNT(*) FROM conflict_effect", Integer.class));
            UserContext.setCurrentUser("owner", "owner");
            try {
                assertThrows(FileUploadIdempotencyException.class, () -> service.prepareUpload("same-key",
                        new MockMultipartFile("file", "name.txt", "text/plain", new byte[]{1})));
                h.tx.executeWithoutResult(status -> {
                    var claim = service.prepareUpload("rollback", file);
                    service.register(Map.of("url", "rollback-url", "filename", "rollback-key"), file, claim);
                    status.setRollbackOnly();
                });
                assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM storage_file_object", Integer.class));
            } finally { UserContext.clear(); }
        }
    }

    @Test void assertionReplayClaimHasOneWinnerAndRollbackAllowsRetry() throws Exception {
        try (var f = new Fixture()) {
            parent(f, "embed_identity_provider", "provider");
            table(f, "embed_assertion_replay", EMBED_SCHEMA); effectTable(f);
            var h = new Harness(f, EmbedLaunchPersistenceMapper.class);
            var adapter = new MyBatisEmbedLaunchPersistenceAdapter(h.mapper(EmbedLaunchPersistenceMapper.class), h.attempt);
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            var claims = concurrent(6, index -> h.tx.execute(status -> {
                boolean won = adapter.claim("provider", "a".repeat(64), now.plusSeconds(60), now);
                h.jdbc.update("INSERT INTO conflict_effect VALUES (?)", "worker-" + index);
                return won;
            }));
            assertEquals(1, claims.stream().filter(Boolean::booleanValue).count());
            assertEquals(6, h.jdbc.queryForObject("SELECT COUNT(*) FROM conflict_effect", Integer.class));
            h.tx.executeWithoutResult(status -> { assertTrue(adapter.claim("provider", "b".repeat(64), now.plusSeconds(60), now)); status.setRollbackOnly(); });
            assertEquals(Boolean.TRUE, h.tx.execute(status -> adapter.claim("provider", "b".repeat(64), now.plusSeconds(60), now)));
        }
    }

    @Test void grantConflictRestoresTransactionBeforeReadingTheCommittedGrant() throws Exception {
        try (var f = new Fixture()) {
            parent(f, "integration_application", "application");
            parent(f, "embed_view", "view");
            parent(f, "embed_identity_provider", "provider");
            table(f, "embed_application_grant", EMBED_SCHEMA);
            table(f, "embed_allowed_origin", EMBED_SCHEMA); effectTable(f);
            Class<?> mapperType = Class.forName("com.workflow.embed.management.infrastructure.persistence.EmbedManagementMapper");
            var h = new Harness(f, mapperType);
            var repository = (MyBatisEmbedManagementRepository) MyBatisEmbedManagementRepository.class.getConstructors()[0]
                    .newInstance(h.mapper(mapperType), h.json, mock(EntityNewDataFormRuntimePort.class), h.attempt);
            h.tx.executeWithoutResult(status -> repository.insertGrant(grant("winner")));
            h.tx.executeWithoutResult(status -> {
                h.jdbc.update("INSERT INTO conflict_effect VALUES ('before')");
                assertThrows(DuplicateKeyException.class, () -> repository.insertGrant(grant("loser")));
                assertEquals("winner", repository.lockGrant("view", "application").id());
                h.jdbc.update("INSERT INTO conflict_effect VALUES ('after')");
            });
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM embed_application_grant", Integer.class));
            assertEquals(2, h.jdbc.queryForObject("SELECT COUNT(*) FROM conflict_effect", Integer.class));
        }
    }

    private static ProcessCcRuntimeService ccRuntime(Harness h, ProcessCcNotificationPublisher notifications) {
        var cc = h.transactional(new ProcessCcService(h.mapper(ProcessCcRecordMapper.class), mock(ProcessCcSnapshotService.class), h.attempt));
        var users = mock(SysUserMapper.class);
        var user = new SysUser(); user.setId("observer-id"); user.setUsername("observer");
        user.setStatus(SysUser.Status.ENABLED.getValue()); user.setDeleted(0);
        when(users.selectByUsername("observer")).thenReturn(user);
        // 本场景只触发固定用户规则；其他解析器/任务服务不会调用。
        return h.transactional(new ProcessCcRuntimeService(null, null, null, cc, notifications, null,
                users, null, null, null, null, null, h.json, List.of(), null, null));
    }

    private static CcRuntimeContext context(String instance) {
        return new CcRuntimeContext(instance, "definition", "flow", "流程", "business", "node", "节点", "TASK_COMPLETE", null, Map.of());
    }

    private static AssigneeIncidentRecorder.CreateCommand incident(String task) {
        return new AssigneeIncidentRecorder.CreateCommand("config", "definition", "instance", task, "node", "审批", "CREATE_INCIDENT",
                "OPEN", "EMPTY", "无办理人", null, Map.of(), null, null, "ops", 0, 30, 2, null, Map.of());
    }

    private static GrantState grant(String id) {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new GrantState(id, "application", "view", "provider", SecurityStatus.ACTIVE, true,
                RevisionMode.FOLLOW_ACTIVE, null, "[]", 2, 600, 60, 60, 5, null, 1L, 1L,
                List.of(), "actor", now, "actor", now, null, null);
    }

    private static void effectTable(Fixture f) { f.table("conflict_effect", "id VARCHAR(64) PRIMARY KEY"); }
    private static void parent(Fixture f, String table, String id) {
        String actual = f.table(table, "id VARCHAR(64) COLLATE utf8mb4_bin PRIMARY KEY");
        f.jdbc.update("INSERT INTO " + actual + " VALUES (?)", id);
    }
    private static void ccTables(Fixture f) throws Exception {
        table(f, "process_cc_record", "V001__business_schema.sql");
        alter(f, "process_cc_record", "V092__process_cc_name_snapshot.sql");
    }
    private static void incidentTables(Fixture f) throws Exception {
        table(f, "process_assignee_incident", INCIDENT_SCHEMA);
        table(f, "process_assignee_incident_action", INCIDENT_SCHEMA);
    }

    /** 只复制指定 CREATE/ALTER 到随机表，绝不执行迁移文件中的种子或业务数据操作。 */
    static void table(Fixture f, String name, String migration) throws Exception {
        String sql = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration", migration));
        var match = Pattern.compile("CREATE TABLE `?" + Pattern.quote(name) + "`? \\(\\n(.*?)\\n\\) ENGINE", Pattern.DOTALL).matcher(sql);
        assertTrue(match.find(), name); f.table(name, isolatedConstraints(f, match.group(1)));
    }
    private static void alter(Fixture f, String name, String migration) throws Exception {
        String sql = Files.readString(Path.of("../workflow-db-migrator/src/main/resources/db/migration", migration));
        var match = Pattern.compile("ALTER TABLE `?" + Pattern.quote(name) + "`?\\s[^;]*;", Pattern.DOTALL).matcher(sql);
        assertTrue(match.find(), name);
        f.jdbc.execute(isolatedConstraints(f, match.group()).replaceAll("`?\\b" + Pattern.quote(name) + "\\b`?", "`" + f.tables.get(name) + "`"));
    }

    /** 外键必须指向已创建的随机父表；约束名也隔离，避免与业务库的 schema 级名称相撞。 */
    private static String isolatedConstraints(Fixture f, String sql) {
        var references = Pattern.compile("REFERENCES\\s+`?([a-zA-Z0-9_]+)`?").matcher(sql);
        String isolated = references.replaceAll(match -> {
            String target = f.tables.get(match.group(1));
            assertNotNull(target, "测试不得引用业务表: " + match.group(1));
            return "REFERENCES `" + target + "`";
        });
        var number = new AtomicInteger();
        return Pattern.compile("CONSTRAINT\\s+`?[a-zA-Z0-9_]+`?").matcher(isolated)
                .replaceAll(match -> "CONSTRAINT `ct_" + f.suffix + "_" + f.tables.size() + "_" + number.incrementAndGet() + "`");
    }

    private static JdbcTemplate barrierJdbc(Harness h, String fragment, int participants) {
        var barrier = new CyclicBarrier(participants); var calls = new AtomicInteger();
        return new JdbcTemplate(h.jdbc.getDataSource()) {
            @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                var rows = super.query(sql, mapper, args);
                if (sql.contains(fragment) && !sql.contains("FOR SHARE") && calls.incrementAndGet() <= participants) await(barrier);
                return rows;
            }
        };
    }
    private static void await(CyclicBarrier barrier) {
        try { barrier.await(10, TimeUnit.SECONDS); }
        catch (Exception error) { throw new AssertionError(error); }
    }
}
