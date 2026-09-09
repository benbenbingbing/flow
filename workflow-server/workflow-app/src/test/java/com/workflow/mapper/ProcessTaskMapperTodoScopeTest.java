package com.workflow.mapper;

import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 执行真实待办列表/统计 SQL，覆盖旧投影、不同候选身份和认领后可见性。 */
class ProcessTaskMapperTodoScopeTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("task-scope-" + UUID.randomUUID() + ";MODE=MySQL")
                .build();
        jdbc = new JdbcTemplate(database);
        namedJdbc = new NamedParameterJdbcTemplate(database);
        jdbc.execute("CREATE TABLE process_task (task_id VARCHAR(64), assignee_id VARCHAR(200), "
                + "assignee_type VARCHAR(16), status VARCHAR(16), deleted INT, create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE ACT_RU_TASK (ID_ VARCHAR(64), ASSIGNEE_ VARCHAR(64))");
        jdbc.execute("CREATE TABLE ACT_RU_IDENTITYLINK (TASK_ID_ VARCHAR(64), "
                + "TYPE_ VARCHAR(32), USER_ID_ VARCHAR(64), GROUP_ID_ VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(64), username VARCHAR(64), status CHAR(1), deleted INT)");
        jdbc.execute("CREATE TABLE sys_group (id VARCHAR(64), group_code VARCHAR(64), status CHAR(1), deleted INT)");
        jdbc.execute("CREATE TABLE sys_role (id VARCHAR(64), role_code VARCHAR(64), status CHAR(1), deleted INT)");
        jdbc.execute("CREATE TABLE sys_user_group (user_id VARCHAR(64), group_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_user_role (user_id VARCHAR(64), role_id VARCHAR(64))");
        jdbc.update("INSERT INTO sys_user VALUES ('user-1', 'alice', '0', 0), ('user-2', 'bob', '0', 0)");
        jdbc.update("INSERT INTO sys_group VALUES ('group-1', 'finance', '0', 0)");
        jdbc.update("INSERT INTO sys_role VALUES ('role-1', 'manager', '0', 0)");
        jdbc.update("INSERT INTO sys_user_group VALUES ('user-1', 'group-1')");
        jdbc.update("INSERT INTO sys_user_role VALUES ('user-1', 'role-1')");
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @ParameterizedTest
    @CsvSource({"user-1", "alice"})
    void assignedTasksAcceptIdOrUsername(String assignee) throws Exception {
        addTask("assigned", assignee);
        assertVisible("assigned");
        assertTodo("bob", List.of());
    }

    @ParameterizedTest
    @CsvSource({"user-1", "alice"})
    void multipleCandidateUsersRemainVisibleWithLegacyCommaSeparatedProjection(String candidateUser) throws Exception {
        addTask("users", null);
        jdbc.update("UPDATE process_task SET assignee_id = 'user-2,user-1', assignee_type = 'group'");
        addCandidate("users", "candidate", "user-2", null);
        addCandidate("users", "candidate", candidateUser, null);
        assertVisible("users");
        assertTodo("bob", List.of("users"));
    }

    @ParameterizedTest
    @CsvSource({"group-1", "finance", "ROLE_role-1", "ROLE_manager"})
    void groupsAndRolesAcceptIdOrCodeForExistingTasks(String candidateGroup) throws Exception {
        addTask("candidate-group", null);
        addCandidate("candidate-group", "candidate", null, candidateGroup);
        assertVisible("candidate-group");
        assertTodo("bob", List.of());
    }

    @Test
    void mixedUsersAndGroupsDoNotLoseUsersOrDuplicateListAndCount() throws Exception {
        addTask("mixed", null);
        // 旧版本混合任务只保存组；候选用户必须仍能凭引擎关系看到它。
        jdbc.update("UPDATE process_task SET assignee_id = 'finance', assignee_type = 'group'");
        addCandidate("mixed", "candidate", "bob", null);
        addCandidate("mixed", "candidate", "alice", null);
        addCandidate("mixed", "candidate", null, "finance");
        addCandidate("mixed", "candidate", null, "ROLE_manager");
        assertVisible("mixed");
        assertTodo("bob", List.of("mixed"));
    }

    @Test
    void claimedTaskDisappearsFromOtherCandidatesDespiteStaleLocalProjection() throws Exception {
        addTask("claimed", "bob");
        jdbc.update("UPDATE process_task SET assignee_id = 'finance', assignee_type = 'group'");
        addCandidate("claimed", "candidate", "alice", null);
        addCandidate("claimed", "candidate", null, "finance");
        assertTodo("alice", List.of());
        assertTodo("bob", List.of("claimed"));
    }

    @Test
    void nonCandidateLinksAndAbsentEngineTasksDoNotGrantTodoVisibility() throws Exception {
        addTask("participant", null);
        addCandidate("participant", "participant", "alice", null);
        addCandidate("participant", "owner", null, "finance");
        addTask("stale", "alice");
        jdbc.update("DELETE FROM ACT_RU_TASK WHERE ID_ = 'stale'");
        assertTodo("alice", List.of());
    }

    @ParameterizedTest
    @CsvSource({"sys_group, status, 1", "sys_group, deleted, 1", "sys_role, status, 1", "sys_role, deleted, 1"})
    void disabledOrDeletedGroupAndRoleMembershipDoesNotGrantVisibility(
            String table, String field, String value) throws Exception {
        addTask("inactive", null);
        addCandidate("inactive", "candidate", null, table.equals("sys_group") ? "finance" : "ROLE_manager");
        jdbc.update("UPDATE " + table + " SET " + field + " = ?", value);
        assertTodo("alice", List.of());
    }

    @Test
    void membershipChangesApplyWithoutRepublishingTheExistingTask() throws Exception {
        addTask("group", null);
        addCandidate("group", "candidate", null, "finance");
        assertVisible("group");
        jdbc.update("DELETE FROM sys_user_group WHERE user_id = 'user-1'");
        assertTodo("alice", List.of());
        jdbc.update("INSERT INTO sys_user_group VALUES ('user-2', 'group-1')");
        assertTodo("bob", List.of("group"));
    }

    @Test
    void completedOrDeletedLocalTasksStayExcluded() throws Exception {
        addTask("done", "alice");
        addTask("deleted", "alice");
        jdbc.update("UPDATE process_task SET status = 'done' WHERE task_id = 'done'");
        jdbc.update("UPDATE process_task SET deleted = 1 WHERE task_id = 'deleted'");
        assertTodo("alice", List.of());
    }

    private void addTask(String taskId, String assignee) {
        jdbc.update("INSERT INTO process_task VALUES (?, 'legacy-projection', 'group', 'todo', 0, CURRENT_TIMESTAMP)", taskId);
        jdbc.update("INSERT INTO ACT_RU_TASK VALUES (?, ?)", taskId, assignee);
    }

    private void addCandidate(String taskId, String type, String userId, String groupId) {
        jdbc.update("INSERT INTO ACT_RU_IDENTITYLINK VALUES (?, ?, ?, ?)", taskId, type, userId, groupId);
    }

    private void assertVisible(String taskId) throws Exception {
        assertTodo("alice", List.of(taskId));
        assertTodo("user-1", List.of(taskId));
    }

    /** 同时执行列表和计数，防止两处授权规则产生偏差。 */
    private void assertTodo(String user, List<String> taskIds) throws Exception {
        Map<String, String> params = Map.of("userId", user);
        List<String> actual = namedJdbc.query(selectSql("selectTodoByUser"), params,
                (rs, rowNum) -> rs.getString("task_id"));
        assertEquals(taskIds, actual);
        assertEquals((long) taskIds.size(), namedJdbc.queryForObject(selectSql("countTodoByUser"), params, Long.class));
    }

    /** H2 执行同一 SQL，仅移除其不支持的 MySQL 字符集排序规则声明。 */
    private String selectSql(String method) throws Exception {
        Select select = ProcessTaskMapper.class.getMethod(method, String.class).getAnnotation(Select.class);
        return String.join("", select.value())
                .replace(" COLLATE utf8mb4_unicode_ci", "")
                .replace("#{userId}", ":userId");
    }
}
