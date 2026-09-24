package com.workflow.mapper;

import com.workflow.contracts.process.port.ProcessTaskAccessPort.RecordCoordinates;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.task.application.ProcessTaskAccessAdapter;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import java.util.List;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 执行真实批量 SQL，验证身份别名、候选/办理人区分以及实体和流程坐标的联合约束。 */
class TaskCapabilityBatchDatabaseTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private SqlSessionFactory factory;

    @BeforeEach void setup() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(64), username VARCHAR(64), status VARCHAR(8), deleted INT)");
        jdbc.execute("CREATE TABLE sys_group(id VARCHAR(64), group_code VARCHAR(64), status VARCHAR(8), deleted INT)");
        jdbc.execute("CREATE TABLE sys_role(id VARCHAR(64), role_code VARCHAR(64), status VARCHAR(8), deleted INT)");
        jdbc.execute("CREATE TABLE sys_user_group(user_id VARCHAR(64), group_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_user_role(user_id VARCHAR(64), role_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE ACT_RU_TASK(ID_ VARCHAR(64), ASSIGNEE_ VARCHAR(64), PROC_INST_ID_ VARCHAR(64))");
        jdbc.execute("CREATE TABLE ACT_RU_IDENTITYLINK(TASK_ID_ VARCHAR(64), TYPE_ VARCHAR(32), USER_ID_ VARCHAR(64), GROUP_ID_ VARCHAR(64))");
        jdbc.execute("CREATE TABLE process_task(id VARCHAR(64), task_id VARCHAR(64), node_name VARCHAR(64), node_type VARCHAR(32), "
                + "entity_code VARCHAR(64), entity_data_id VARCHAR(64), process_instance_id VARCHAR(64), "
                + "assignee_type VARCHAR(16), assignee_id VARCHAR(64), status VARCHAR(16), deleted INT, create_time INT)");
        jdbc.execute("CREATE TABLE process_task_add_sign_user(add_sign_id VARCHAR(64), generated_task_id VARCHAR(64), status VARCHAR(16), user_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE process_task_add_sign(id VARCHAR(64), source_task_id VARCHAR(64), status VARCHAR(16), process_instance_id VARCHAR(64))");
        jdbc.update("INSERT INTO sys_user VALUES ('u1','alice','0',0), ('u2','bob','0',0)");
        jdbc.update("INSERT INTO sys_role VALUES ('role-1','reviewers','0',0)");
        jdbc.update("INSERT INTO sys_user_role VALUES ('u1','role-1')");
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), database));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(ProcessTaskMapper.class);
        factory = new SqlSessionFactoryBuilder().build(config);
    }

    @AfterEach void cleanup() { database.shutdown(); }

    @ParameterizedTest @ValueSource(strings = {"alice", "u1"})
    void batchPreservesLatestTaskNameAndAssignedIdentityWithoutCrossingCoordinates(String identity) {
        task("old-assigned", "r1", "p1", "alice", 1);
        task("latest-candidate", "r1", "p1", null, 2);
        task("other-record", "r2", "p2", null, 3);
        task("crossed-coordinates", "r1", "p2", "u1", 4);
        jdbc.update("INSERT INTO ACT_RU_IDENTITYLINK VALUES ('latest-candidate','candidate','u1',NULL),"
                + "('other-record','candidate',NULL,'ROLE_reviewers')");
        var first = new RecordCoordinates("expense", "r1", "p1");
        var second = new RecordCoordinates("expense", "r2", "p2");
        var missing = new RecordCoordinates("expense", "missing' OR 1=1 --", "p1");
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(ProcessTaskMapper.class);
            var rows = mapper.selectActionableTaskSummaries(identity, List.of(first, second, missing));
            assertEquals(List.of("other-record", "latest-candidate", "old-assigned"),
                    rows.stream().map(row -> row.getTaskId()).toList());
            var adapter = new ProcessTaskAccessAdapter(mapper, mock(ProcessPublishedSnapshotService.class));
            var result = adapter.findCapabilities(identity, List.of(first, second, missing));
            assertEquals("latest-candidate", result.get(first).taskId());
            assertEquals("node-latest-candidate", result.get(first).taskName());
            assertTrue(result.get(first).currentAssignee(), "同一记录较早的实际指派仍构成办理人身份");
            assertFalse(result.get(second).currentAssignee(), "角色候选审批不能获得办理人权限");
            assertNull(result.get(missing).taskId());
            assertFalse(result.get(missing).currentAssignee());
        }
    }

    @Test void liveClaimAndDisabledIdentityRemoveCandidateAccess() {
        task("candidate", "r1", "p1", null, 1);
        jdbc.update("INSERT INTO ACT_RU_IDENTITYLINK VALUES ('candidate','candidate','alice',NULL)");
        var coordinate = new RecordCoordinates("expense", "r1", "p1");
        assertEquals(1, visible(coordinate));
        jdbc.update("UPDATE ACT_RU_TASK SET ASSIGNEE_='bob' WHERE ID_='candidate'");
        assertEquals(0, visible(coordinate));
        jdbc.update("UPDATE ACT_RU_TASK SET ASSIGNEE_=NULL WHERE ID_='candidate'");
        jdbc.update("UPDATE sys_user SET status='1' WHERE id='u1'");
        assertEquals(0, visible(coordinate));
    }

    @Test void emptyOrIncompleteCoordinatesNeverExpandToAllTasks() {
        task("assigned", "r1", "p1", "u1", 1);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(ProcessTaskMapper.class);
            assertTrue(mapper.selectActionableTaskSummaries("u1", List.of()).isEmpty());
            assertTrue(mapper.selectActionableTaskSummaries("u1",
                    List.of(new RecordCoordinates("expense", null, null))).isEmpty());
            var adapter = new ProcessTaskAccessAdapter(mapper, mock(ProcessPublishedSnapshotService.class));
            var processOnly = new RecordCoordinates("expense", " ", "p1");
            assertEquals("assigned", adapter.findCapabilities("u1", List.of(processOnly)).get(processOnly).taskId());
        }
    }

    private int visible(RecordCoordinates coordinate) {
        try (var session = factory.openSession()) {
            return session.getMapper(ProcessTaskMapper.class).selectActionableTaskSummaries("u1", List.of(coordinate)).size();
        }
    }
    private void task(String id, String record, String process, String assignee, int order) {
        jdbc.update("INSERT INTO process_task VALUES(?,?,?,NULL,'expense',?,?,'user','stale-projection','todo',0,?)",
                id, id, "node-" + id, record, process, order);
        jdbc.update("INSERT INTO ACT_RU_TASK VALUES (?,?,?)", id, assignee, process);
    }
}
