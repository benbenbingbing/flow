package com.workflow.service.permission;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.application.CurrentProcessTaskAssigneeLookup;
import com.workflow.entity.permission.application.PermissionSqlBuilder;
import com.workflow.process.task.application.ProcessTaskAccessAdapter;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 通过契约和真实 Mapper SQL 验证实体审批绑定，并隔离普通办理人权限。 */
class CurrentProcessTaskAssigneeLookupTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private CurrentProcessTaskAssigneeLookup lookup;
    private ProcessTaskAccessPort taskAccess;
    private PermissionSqlBuilder permissionSqlBuilder;
    private SysUser alice;
    private EntityDataDTO row;

    @BeforeEach
    void setUp() throws Exception {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("record-task-access-" + UUID.randomUUID() + ";MODE=MySQL")
                .build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE process_task (id BIGINT AUTO_INCREMENT PRIMARY KEY, task_id VARCHAR(64), "
                + "entity_code VARCHAR(64), entity_data_id VARCHAR(64), process_instance_id VARCHAR(64), "
                + "assignee_id VARCHAR(200), assignee_type VARCHAR(16), status VARCHAR(16), deleted INT, create_time TIMESTAMP, "
                + "node_type VARCHAR(32) DEFAULT 'USER_TASK')");
        jdbc.execute("CREATE TABLE ACT_RU_TASK (ID_ VARCHAR(64), ASSIGNEE_ VARCHAR(64), PROC_INST_ID_ VARCHAR(64))");
        jdbc.execute("CREATE TABLE ACT_RU_IDENTITYLINK (TASK_ID_ VARCHAR(64), TYPE_ VARCHAR(32), "
                + "USER_ID_ VARCHAR(64), GROUP_ID_ VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(64), username VARCHAR(64), status CHAR(1), deleted INT)");
        jdbc.execute("CREATE TABLE sys_group (id VARCHAR(64), group_code VARCHAR(64), status CHAR(1), deleted INT)");
        jdbc.execute("CREATE TABLE sys_role (id VARCHAR(64), role_code VARCHAR(64), status CHAR(1), deleted INT)");
        jdbc.execute("CREATE TABLE sys_user_group (user_id VARCHAR(64), group_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_user_role (user_id VARCHAR(64), role_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE process_task_add_sign (id VARCHAR(64), source_task_id VARCHAR(64), "
                + "process_instance_id VARCHAR(64), status VARCHAR(32))");
        jdbc.execute("CREATE TABLE process_task_add_sign_user (add_sign_id VARCHAR(64), generated_task_id VARCHAR(64), "
                + "user_id VARCHAR(64), status VARCHAR(32))");
        jdbc.execute("CREATE TABLE wf_expense (id VARCHAR(256), current_task_assignee VARCHAR(64), status VARCHAR(32))");
        jdbc.update("INSERT INTO wf_expense VALUES ('record-1', 'bob', 'PENDING'), ('record-2', 'bob', 'PENDING')");
        jdbc.update("INSERT INTO sys_user VALUES ('user-1', 'alice', '0', 0), ('user-2', 'bob', '0', 0)");
        jdbc.update("INSERT INTO sys_group VALUES ('group-1', 'finance', '0', 0)");
        jdbc.update("INSERT INTO sys_role VALUES ('role-1', 'manager', '0', 0)");
        jdbc.update("INSERT INTO sys_user_group VALUES ('user-1', 'group-1')");
        jdbc.update("INSERT INTO sys_user_role VALUES ('user-1', 'role-1')");

        // 由 MyBatis 解析生产动态 SQL；H2 仅移除其不支持的 MySQL 排序规则声明。
        Select select = ProcessTaskMapper.class.getMethod("selectActionableTaskId",
                String.class, String.class, String.class, String.class, boolean.class).getAnnotation(Select.class);
        SqlSource sqlSource = new XMLLanguageDriver().createSqlSource(new Configuration(),
                String.join("", select.value()).replace(" COLLATE utf8mb4_unicode_ci", ""), Map.class);
        ProcessTaskMapper mapper = mock(ProcessTaskMapper.class);
        when(mapper.selectActionableTaskId(anyString(), nullable(String.class), nullable(String.class),
                nullable(String.class), anyBoolean())).thenAnswer(invocation -> {
                    Map<String, Object> params = new HashMap<>();
                    String[] names = {"userId", "entityCode", "entityDataId", "processInstanceId", "assignedOnly"};
                    for (int index = 0; index < names.length; index++) {
                        params.put(names[index], invocation.getArgument(index));
                    }
                    return executeMapperSql(sqlSource, params).stream().findFirst().orElse(null);
                });
        Select recordSelect = ProcessTaskMapper.class.getMethod("selectActionableEntityDataIds",
                String.class, String.class).getAnnotation(Select.class);
        SqlSource recordSqlSource = new XMLLanguageDriver().createSqlSource(new Configuration(),
                String.join("", recordSelect.value()).replace(" COLLATE utf8mb4_unicode_ci", ""), Map.class);
        when(mapper.selectActionableEntityDataIds(anyString(), anyString())).thenAnswer(invocation ->
                executeMapperSql(recordSqlSource, Map.of(
                        "userId", invocation.getArgument(0), "entityCode", invocation.getArgument(1))));
        taskAccess = new ProcessTaskAccessAdapter(
                mapper,
                mock(com.workflow.process.publish.application
                        .ProcessPublishedSnapshotService.class));
        lookup = new CurrentProcessTaskAssigneeLookup(taskAccess);
        EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
        when(tableResolver.resolve("EXPENSE")).thenReturn("wf_expense");
        permissionSqlBuilder = new PermissionSqlBuilder(
                null, null, null, List.of(), null, tableResolver, null, taskAccess);
        alice = new SysUser();
        alice.setId("user-1");
        alice.setUsername("alice");
        row = new EntityDataDTO();
        row.setId("record-1");
        row.setEntityCode("EXPENSE");
        row.setProcessInstanceId("process-1");
        row.setCurrentTaskId("task-bob");
        row.setCurrentTaskAssignee("bob");
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @ParameterizedTest
    @CsvSource({"alice", "user-1"})
    void matchesActualAssigneeDespiteSiblingSummaryAndStaleLocalProjection(String assignee) {
        addTask("task-alice", assignee, "record-1", "process-1");
        addTask("task-bob", "bob", "record-1", "process-1");
        assertEquals(Optional.of("task-alice"), lookup.findActionableTaskId(row, alice));
        assertTrue(lookup.isCurrentAssignee(row, alice));
        assertEquals("task-bob", row.getCurrentTaskId());
        assertHasTodoRecords(List.of("record-1"));
    }

    @ParameterizedTest
    @CsvSource({"alice,", "user-1,", ",group-1", ",finance", ",ROLE_role-1", ",ROLE_manager"})
    void candidatesMayApproveButDoNotBecomeAssignedUsers(String userId, String groupId) {
        addTask("candidate-task", null, "record-1", "process-1");
        addCandidate("candidate-task", "candidate", userId, groupId);
        assertEquals(Optional.of("candidate-task"), lookup.findActionableTaskId(row, alice));
        assertFalse(lookup.isCurrentAssignee(row, alice));
        assertHasTodoRecords(List.of("record-1"));
    }

    @Test
    void taskClaimedByAnotherUserIsNeitherActionableNorAssigned() {
        addTask("claimed", "bob", "record-1", "process-1");
        addCandidate("claimed", "candidate", "alice", "finance");
        assertEquals(Optional.empty(), lookup.findActionableTaskId(row, alice));
        assertFalse(lookup.isCurrentAssignee(row, alice));
        assertHasTodoRecords(List.of());
    }

    @Test
    void entityAndProcessCoordinatesMustMatchTogether() {
        addTask("wrong-record", "alice", "record-2", "process-1");
        addTask("wrong-instance", "alice", "record-1", "process-2");
        addTask("wrong-entity", "alice", "record-1", "process-1");
        jdbc.update("UPDATE process_task SET entity_code = 'OTHER' WHERE task_id = 'wrong-entity'");
        assertEquals(Optional.empty(), lookup.findActionableTaskId(row, alice));
        addTask("correct", "alice", "record-1", "process-1");
        assertEquals(Optional.of("correct"), lookup.findActionableTaskId(row, alice));
    }

    @Test
    void supportsCompleteEntityOrProcessCoordinatesWithoutTrustingTaskSummary() {
        addTask("correct", "alice", "record-1", "process-1");
        row.setProcessInstanceId(null);
        assertEquals(Optional.of("correct"), lookup.findActionableTaskId(row, alice));
        row.setId(null);
        row.setEntityCode(null);
        row.setProcessInstanceId("process-1");
        assertEquals(Optional.of("correct"), lookup.findActionableTaskId(row, alice));
    }

    @ParameterizedTest
    @CsvSource({"sys_group,status", "sys_group,deleted", "sys_role,status", "sys_role,deleted"})
    void disabledOrDeletedGroupAndRoleCannotApprove(String table, String field) {
        addTask("candidate", null, "record-1", "process-1");
        addCandidate("candidate", "candidate", null, table.equals("sys_group") ? "finance" : "ROLE_manager");
        jdbc.update("UPDATE " + table + " SET " + field + " = 1");
        assertEquals(Optional.empty(), lookup.findActionableTaskId(row, alice));
        assertHasTodoRecords(List.of());
    }

    @Test
    void nonCandidateLinksAndCompletedTasksCannotApprove() {
        addTask("participant", null, "record-1", "process-1");
        addCandidate("participant", "participant", "alice", null);
        addCandidate("participant", "owner", null, "finance");
        addTask("completed", "alice", "record-1", "process-1");
        jdbc.update("DELETE FROM ACT_RU_TASK WHERE ID_ = 'completed'");
        assertEquals(Optional.empty(), lookup.findActionableTaskId(row, alice));
        assertHasTodoRecords(List.of());
    }

    @Test
    void hasTodoDoesNotExposeMatchingRecordIdsFromAnotherEntity() {
        addTask("other-entity", "alice", "record-2", "process-1");
        jdbc.update("UPDATE process_task SET entity_code = 'OTHER' WHERE task_id = 'other-entity'");

        assertHasTodoRecords(List.of());
        assertEquals(List.of("record-2"), taskAccess.findActionableEntityDataIds("user-1", "OTHER"));
        assertEquals(List.of(), taskAccess.findActionableEntityDataIds("user-1", null));
    }

    @Test
    void hasTodoReflectsClaimAndCompletionWithoutRepairingStaleProjection() {
        addTask("candidate", null, "record-1", "process-1");
        addCandidate("candidate", "candidate", null, "finance");
        assertHasTodoRecords(List.of("record-1"));

        jdbc.update("UPDATE ACT_RU_TASK SET ASSIGNEE_ = 'bob' WHERE ID_ = 'candidate'");
        assertHasTodoRecords(List.of());

        jdbc.update("UPDATE ACT_RU_TASK SET ASSIGNEE_ = NULL WHERE ID_ = 'candidate'");
        assertHasTodoRecords(List.of("record-1"));
        jdbc.update("DELETE FROM ACT_RU_TASK WHERE ID_ = 'candidate'");
        assertHasTodoRecords(List.of());
        assertEquals("todo", jdbc.queryForObject("SELECT status FROM process_task WHERE task_id = 'candidate'", String.class));
    }

    @Test
    void hasTodoDeduplicatesParallelTasksWithoutChangingCurrentAssigneeOrTeamScope() {
        addTask("first", null, "record-1", "process-1");
        addTask("second", null, "record-1", "process-1");
        addCandidate("first", "candidate", "alice", null);
        addCandidate("second", "candidate", null, "finance");
        assertEquals(List.of("record-1"), taskAccess.findActionableEntityDataIds("user-1", "EXPENSE"));
        assertHasTodoRecords(List.of("record-1"));

        FilterConfigDTO currentAssignee = new FilterConfigDTO();
        currentAssignee.setType("CURRENT_ASSIGNEE");
        String assignedSql = permissionSqlBuilder.buildFilterSql("EXPENSE", currentAssignee, alice);
        assertEquals(List.of(), jdbc.queryForList("SELECT id FROM wf_expense WHERE " + assignedSql, String.class));
        FilterConfigDTO team = new FilterConfigDTO();
        team.setType("TEAM");
        assertEquals("1=0", permissionSqlBuilder.buildFilterSql("EXPENSE", team, alice));
    }

    @Test
    void hasTodoTreatsSqlLookingAndUnicodeRecordIdsAsData() {
        String unusualId = "凭证\\' OR 1=1 --";
        jdbc.update("INSERT INTO wf_expense VALUES (?, 'bob', 'PENDING')", unusualId);
        addTask("unusual-record", "alice", unusualId, "process-1");

        assertHasTodoRecords(List.of(unusualId));
    }

    @Test
    void localAddSignIsActionableAssignedAndVisibleInHasTodoWhileItsSourceRemainsActive() {
        addTask("source", "bob", "record-1", "process-1");
        jdbc.update("UPDATE process_task SET status = 'waiting' WHERE task_id = 'source'");
        jdbc.update("UPDATE ACT_RU_TASK SET PROC_INST_ID_ = 'process-1' WHERE ID_ = 'source'");
        addTask("addsign-child", "alice", "record-1", "process-1");
        jdbc.update("DELETE FROM ACT_RU_TASK WHERE ID_ = 'addsign-child'");
        jdbc.update("UPDATE process_task SET node_type = 'ADD_SIGN', assignee_type = 'user', assignee_id = 'alice' "
                + "WHERE task_id = 'addsign-child'");
        jdbc.update("INSERT INTO process_task_add_sign VALUES ('add-sign-1', 'source', 'process-1', 'ACTIVE')");
        jdbc.update("INSERT INTO process_task_add_sign_user VALUES ('add-sign-1', 'addsign-child', 'alice', 'TODO')");

        assertEquals(Optional.of("addsign-child"), lookup.findActionableTaskId(row, alice));
        assertTrue(lookup.isCurrentAssignee(row, alice));
        assertHasTodoRecords(List.of("record-1"));

        jdbc.update("UPDATE process_task_add_sign SET status = 'COMPLETED'");
        assertEquals(Optional.empty(), lookup.findActionableTaskId(row, alice));
        assertFalse(lookup.isCurrentAssignee(row, alice));
        assertHasTodoRecords(List.of());
    }

    @Test
    void failsClosedWhenRecordOrUserHasNoIdentity() {
        ProcessTaskAccessPort port = mock(ProcessTaskAccessPort.class);
        CurrentProcessTaskAssigneeLookup guardedLookup = new CurrentProcessTaskAssigneeLookup(port);
        assertFalse(guardedLookup.isCurrentAssignee(new EntityDataDTO(), alice));
        assertEquals(Optional.empty(), guardedLookup.findActionableTaskId(new EntityDataDTO(), alice));
        assertEquals(Optional.empty(), guardedLookup.findActionableTaskId(row, new SysUser()));
        assertEquals(Optional.empty(), taskAccess.findActionableTaskId("alice", null, null, null));
        verifyNoInteractions(port);
    }

    private void addTask(String taskId, String assignee, String recordId, String processId) {
        jdbc.update("INSERT INTO process_task(task_id, entity_code, entity_data_id, process_instance_id, "
                + "assignee_id, assignee_type, status, deleted, create_time) "
                + "VALUES (?, 'EXPENSE', ?, ?, 'legacy-group', 'group', 'todo', 0, CURRENT_TIMESTAMP)",
                taskId, recordId, processId);
        jdbc.update("INSERT INTO ACT_RU_TASK(ID_, ASSIGNEE_) VALUES (?, ?)", taskId, assignee);
    }

    private void addCandidate(String taskId, String type, String userId, String groupId) {
        jdbc.update("INSERT INTO ACT_RU_IDENTITYLINK VALUES (?, ?, ?, ?)", taskId, type, userId, groupId);
    }

    /** 执行 MyBatis 生成的绑定 SQL，保留实际候选和实体范围判断。 */
    private List<String> executeMapperSql(SqlSource source, Map<String, Object> params) {
        BoundSql boundSql = source.getBoundSql(params);
        Object[] args = boundSql.getParameterMappings().stream()
                .map(parameter -> params.get(parameter.getProperty())).toArray();
        return jdbc.queryForList(boundSql.getSql(), String.class, args);
    }

    /** 执行 HAS_TODO 最终动态表 SQL；H2 使用等价的 UTF8TOSTRING 解码 MySQL 十六进制字面量。 */
    private void assertHasTodoRecords(List<String> expected) {
        FilterConfigDTO filter = new FilterConfigDTO();
        filter.setType("HAS_TODO");
        String permissionSql = permissionSqlBuilder.buildFilterSql("EXPENSE", filter, alice);
        String h2Sql = permissionSql.replaceAll(
                "CONVERT\\((X'[0-9a-f]+') USING utf8mb4\\)", "UTF8TOSTRING($1)");
        assertEquals(expected, jdbc.queryForList("SELECT id FROM wf_expense WHERE " + h2Sql + " ORDER BY id", String.class));
    }
}
