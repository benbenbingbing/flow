package com.workflow.process.task;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.process.task.api.response.TaskHandoverTask;
import com.workflow.process.task.infrastructure.persistence.mapper.TaskHandoverMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 在独立 H2 中执行真实交接 Mapper SQL，验证身份范围、加签存活性与数据库分页。
 * 引擎表只构造查询所需的状态行；实际 Flowable 写入与批量回滚由交接事务测试负责。
 */
class TaskHandoverQueryDatabaseTest {
    private JdbcTemplate jdbc;
    private TaskHandoverMapper handover;
    private TransactionTemplate transactions;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:handover_query_" + UUID.randomUUID()
                + ";MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        createTables();
        var config = new MybatisConfiguration();
        config.setDatabaseId("MYSQL");
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(TaskHandoverMapper.class);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(pagination);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        handover = new SqlSessionTemplate(factory.getObject()).getMapper(TaskHandoverMapper.class);
    }

    @AfterEach
    void cleanup() {
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void disabledAndDeletedSourcesRetainAssignedTasksWithIdAndUsernameCompatibility() {
        engineTask("assigned-id", "alice-id");
        engineTask("assigned-name", "alice");
        engineTask("other", "bob");
        // 故意留下与引擎相反的旧投影，交接授权不能信任镜像办理人。
        jdbc.update("UPDATE process_task SET assignee_id='alice' WHERE task_id='other'");
        for (String state : List.of("0", "1", "unexpected")) {
            jdbc.update("UPDATE sys_user SET status=? WHERE id='alice-id'", state);
            assertEquals(List.of("assigned-id", "assigned-name"), handover.selectAllTaskIds("alice-id"));
        }
        jdbc.update("UPDATE sys_user SET deleted=1 WHERE id='alice-id'");
        assertEquals(2, handover.count("alice-id"));
        assertEquals("ASSIGNED", handover.findEligible("alice-id", "assigned-id").getAssignmentType());
        assertNull(handover.findEligible("alice-id", "other"));
        // API 的来源键必须是确切用户 ID；历史任务内部才兼容用户名。
        assertEquals(0, handover.count("alice"));
        assertEquals(0, handover.count("missing-id"));
    }

    @Test
    void candidateUsersGroupsAndRolesSupportIdsAndCodesWithoutDuplicateTasks() {
        for (String id : List.of("direct-id", "direct-name", "group-id-task", "group-code-task", "role-id-task", "role-code-task", "mixed")) {
            engineTask(id, null);
        }
        candidate("direct-id", "alice-id", null, "candidate");
        candidate("direct-name", "alice", null, "candidate");
        candidate("group-id-task", null, "group-id", "candidate");
        candidate("group-code-task", null, "reviewers", "candidate");
        candidate("role-id-task", null, "ROLE_role-id", "candidate");
        candidate("role-code-task", null, "ROLE_auditors", "candidate");
        candidate("mixed", "alice", null, "candidate");
        candidate("mixed", null, "reviewers", "candidate");
        candidate("mixed", null, "ROLE_auditors", "candidate");
        jdbc.update("UPDATE sys_user SET status='1',deleted=1 WHERE id='alice-id'");
        assertEquals(7, handover.count("alice-id"));
        assertEquals(7, handover.selectAllTaskIds("alice-id").size());
        assertEquals("CANDIDATE", handover.findEligible("alice-id", "mixed").getAssignmentType());

        jdbc.update("UPDATE sys_group SET status='1'");
        assertEquals(5, handover.count("alice-id"));
        jdbc.update("UPDATE sys_role SET deleted=1");
        assertEquals(List.of("direct-id", "direct-name", "mixed"), handover.selectAllTaskIds("alice-id"));
    }

    @Test
    void claimedByOthersNonCandidateLinksAndInvalidMirrorsAreExcluded() {
        engineTask("claimed", "bob");
        engineTask("participant", null);
        engineTask("mismatch", "alice");
        engineTask("completed", "alice");
        engineTask("deleted", "alice");
        engineTask("engine-gone", "alice");
        engineTask("role-prefix-group", null);
        candidate("claimed", "alice", null, "candidate");
        candidate("participant", "alice", null, "participant");
        jdbc.update("UPDATE ACT_RU_TASK SET PROC_INST_ID_='wrong-process' WHERE ID_='mismatch'");
        jdbc.update("UPDATE process_task SET status='done' WHERE task_id='completed'");
        jdbc.update("UPDATE process_task SET deleted=1 WHERE task_id='deleted'");
        jdbc.update("DELETE FROM ACT_RU_TASK WHERE ID_='engine-gone'");
        // ROLE_ 命名空间只代表角色，不能被碰巧同名的普通用户组授权。
        jdbc.update("INSERT INTO sys_group VALUES ('fake-role-group','ROLE_unknown','0',0)");
        jdbc.update("INSERT INTO sys_user_group VALUES ('alice-id','fake-role-group')");
        candidate("role-prefix-group", null, "ROLE_unknown", "candidate");
        assertEquals(0, handover.count("alice-id"));
        assertTrue(handover.selectAllTaskIds("alice-id").isEmpty());
        assertNull(handover.findEligible("alice-id", "claimed"));
    }

    @Test
    void localAddSignTodoAndHoldRemainEligibleWhileSourceWaits() {
        addSignFixture();
        jdbc.update("UPDATE sys_user SET status='1',deleted=1 WHERE id='alice-id'");
        jdbc.update("UPDATE process_task SET status='waiting' WHERE task_id='source'");
        assertEquals(List.of("child-hold", "child-todo"), handover.selectAllTaskIds("alice-id"));
        assertEquals("ADD_SIGN", handover.findEligible("alice-id", "child-todo").getAssignmentType());
        assertEquals("todo", handover.findEligible("alice-id", "child-todo").getStatus());
        assertEquals("hold", handover.findEligible("alice-id", "child-hold").getStatus());
        jdbc.update("UPDATE ACT_RU_TASK SET ASSIGNEE_='alice-id' WHERE ID_='source'");
        assertEquals(3, handover.count("alice-id"));
        assertEquals("waiting", handover.findEligible("alice-id", "source").getStatus());
    }

    @Test
    void staleAddSignChildrenCannotBeRecoveredByHandover() {
        addSignFixture();
        // 每个条件分别破坏再恢复，确保必须同时满足父编排、源任务和业务坐标存活。
        for (String[] statements : List.of(
                new String[]{"UPDATE process_task_add_sign SET status='WAITING_SOURCE'", "UPDATE process_task_add_sign SET status='ACTIVE'"},
                new String[]{"UPDATE process_task_add_sign SET process_instance_id='wrong'", "UPDATE process_task_add_sign SET process_instance_id='process'"},
                new String[]{"UPDATE process_task_add_sign_user SET status='DONE'", "UPDATE process_task_add_sign_user SET status=CASE WHEN generated_task_id='child-hold' THEN 'HOLD' ELSE 'TODO' END"},
                new String[]{"UPDATE process_task_add_sign_user SET user_id='bob'", "UPDATE process_task_add_sign_user SET user_id='alice'"},
                new String[]{"UPDATE process_task SET deleted=1 WHERE task_id='source'", "UPDATE process_task SET deleted=0 WHERE task_id='source'"},
                new String[]{"UPDATE process_task SET status='done' WHERE task_id='source'", "UPDATE process_task SET status='todo' WHERE task_id='source'"},
                new String[]{"UPDATE process_task SET entity_data_id='wrong' WHERE task_id='source'", "UPDATE process_task SET entity_data_id='record' WHERE task_id='source'"},
                new String[]{"UPDATE ACT_RU_TASK SET PROC_INST_ID_='wrong'", "UPDATE ACT_RU_TASK SET PROC_INST_ID_='process'"}
        )) {
            jdbc.update(statements[0]);
            assertEquals(0, handover.count("alice-id"), statements[0]);
            jdbc.update(statements[1]);
            assertEquals(2, handover.count("alice-id"), statements[1]);
        }
        jdbc.update("DELETE FROM ACT_RU_TASK WHERE ID_='source'");
        assertTrue(handover.selectAllTaskIds("alice-id").isEmpty());
    }

    @Test
    void paginationCountAndAllUseSameScopeAndRevalidationBypassesSessionCache() {
        for (int i = 1; i <= 5; i++) engineTask("task-" + i, "alice");
        assertEquals(5, handover.count("alice-id"));
        assertEquals(List.of("task-5", "task-4"), pageIds(1));
        assertEquals(List.of("task-3", "task-2"), pageIds(2));
        assertEquals(List.of("task-1"), pageIds(3));
        assertTrue(pageIds(4).isEmpty());
        assertEquals(List.of("task-1", "task-2", "task-3", "task-4", "task-5"), handover.selectAllTaskIds("alice-id"));
        transactions.executeWithoutResult(status -> {
            assertNotNull(handover.findEligible("alice-id", "task-1"));
            // 绕过 MyBatis 直接改变引擎行，验证同一 SqlSession 的前次结果不会用于交接复验。
            jdbc.update("UPDATE ACT_RU_TASK SET ASSIGNEE_='bob' WHERE ID_='task-1'");
            assertNull(handover.findEligible("alice-id", "task-1"));
        });
        assertEquals(4, handover.count("alice-id"));
    }

    private List<String> pageIds(long page) {
        return handover.selectPage(new Page<>(page, 2, false), "alice-id")
                .stream().map(TaskHandoverTask::getTaskId).toList();
    }

    private void engineTask(String id, String assignee) {
        mirror(id, assignee, "todo", null);
        jdbc.update("INSERT INTO ACT_RU_TASK VALUES (?,?,'process')", id, assignee);
    }

    private void mirror(String id, String assignee, String status, String nodeType) {
        jdbc.update("""
                INSERT INTO process_task(task_id,node_name,process_instance_id,process_name,
                    entity_code,entity_data_id,business_name,business_code,create_time,
                    assignee_id,assignee_name,assignee_type,node_type,status,deleted)
                VALUES (?,'审核','process','交接测试','invoice','record','事项','B-1',
                    TIMESTAMP '2026-09-27 08:00:00',?,?,'user',?,?,0)
                """, id, assignee, assignee, nodeType, status);
    }

    private void candidate(String taskId, String user, String group, String type) {
        jdbc.update("INSERT INTO ACT_RU_IDENTITYLINK VALUES (?,?,?,?,?)", UUID.randomUUID().toString(), taskId, user, group, type);
    }

    /** 源任务属于其他人，来源只持有本地加签，用于隔离父任务与子任务的查询口径。 */
    private void addSignFixture() {
        engineTask("source", "bob");
        jdbc.update("INSERT INTO process_task_add_sign VALUES ('add-sign','source','ACTIVE','process')");
        mirror("child-todo", "alice", "todo", "ADD_SIGN");
        mirror("child-hold", "alice-id", "hold", "ADD_SIGN");
        jdbc.update("INSERT INTO process_task_add_sign_user VALUES ('add-sign','child-todo','TODO','alice')");
        jdbc.update("INSERT INTO process_task_add_sign_user VALUES ('add-sign','child-hold','HOLD','alice-id')");
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE process_task(id BIGINT AUTO_INCREMENT PRIMARY KEY,task_id VARCHAR(64) UNIQUE,
                    node_name VARCHAR(100),process_instance_id VARCHAR(64),process_name VARCHAR(100),
                    entity_code VARCHAR(64),entity_data_id VARCHAR(64),business_name VARCHAR(100),business_code VARCHAR(64),
                    create_time TIMESTAMP,assignee_id VARCHAR(64),assignee_name VARCHAR(100),assignee_type VARCHAR(32),
                    node_type VARCHAR(32),status VARCHAR(32),deleted INT)
                """);
        jdbc.execute("CREATE TABLE ACT_RU_TASK(ID_ VARCHAR(64) PRIMARY KEY,ASSIGNEE_ VARCHAR(64),PROC_INST_ID_ VARCHAR(64))");
        jdbc.execute("CREATE TABLE ACT_RU_IDENTITYLINK(ID_ VARCHAR(64) PRIMARY KEY,TASK_ID_ VARCHAR(64),USER_ID_ VARCHAR(64),GROUP_ID_ VARCHAR(64),TYPE_ VARCHAR(32))");
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(64) PRIMARY KEY,username VARCHAR(64),status VARCHAR(32),deleted INT)");
        jdbc.update("INSERT INTO sys_user VALUES ('alice-id','alice','0',0),('bob-id','bob','0',0)");
        jdbc.execute("CREATE TABLE sys_group(id VARCHAR(64),group_code VARCHAR(64),status VARCHAR(1),deleted INT)");
        jdbc.execute("CREATE TABLE sys_role(id VARCHAR(64),role_code VARCHAR(64),status VARCHAR(1),deleted INT)");
        jdbc.execute("CREATE TABLE sys_user_group(user_id VARCHAR(64),group_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_user_role(user_id VARCHAR(64),role_id VARCHAR(64))");
        jdbc.update("INSERT INTO sys_group VALUES ('group-id','reviewers','0',0)");
        jdbc.update("INSERT INTO sys_role VALUES ('role-id','auditors','0',0)");
        jdbc.update("INSERT INTO sys_user_group VALUES ('alice-id','group-id')");
        jdbc.update("INSERT INTO sys_user_role VALUES ('alice-id','role-id')");
        jdbc.execute("CREATE TABLE process_task_add_sign(id VARCHAR(64),source_task_id VARCHAR(64),status VARCHAR(32),process_instance_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE process_task_add_sign_user(add_sign_id VARCHAR(64),generated_task_id VARCHAR(64),status VARCHAR(32),user_id VARCHAR(64))");
    }
}
