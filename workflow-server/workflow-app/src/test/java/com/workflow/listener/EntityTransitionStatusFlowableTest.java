package com.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect;
import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;
import com.workflow.process.engine.infrastructure.flowable.EntityTransitionStatusListener;
import com.workflow.process.status.application.ProcessEntityStatusPolicy;
import org.flowable.engine.*;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 真实引擎覆盖选路、循环、部署版本与同事务回滚，避免仅验证手造事件。 */
class EntityTransitionStatusFlowableTest {
    static javax.sql.DataSource dataSource;
    static ProcessEngine engine;
    static JdbcTemplate jdbc;
    static TransactionTemplate transactions;
    static final EntityMutationPort mutations = mock(EntityMutationPort.class);
    static final ProcessBpmnPublishSanitizer sanitizer = new ProcessBpmnPublishSanitizer(new ObjectMapper());
    static boolean rejectWrite;
    static final com.workflow.process.status.application.ProcessStatusSyncPublisher ends = mock(com.workflow.process.status.application.ProcessStatusSyncPublisher.class);

    @BeforeAll static void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:transition_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        dataSource = ds;
        var tm = new DataSourceTransactionManager(ds);
        transactions = new TransactionTemplate(tm);
        jdbc = new JdbcTemplate(ds);
        var cfg = new SpringProcessEngineConfiguration();
        cfg.setDataSource(ds); cfg.setTransactionManager(tm);
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        cfg.setHistory("full"); cfg.setAsyncExecutorActivate(false);
        engine = cfg.buildProcessEngine();
        jdbc.execute("CREATE TABLE status_write(id VARCHAR(100), status VARCHAR(30))");
        when(mutations.execute(any())).thenAnswer(call -> {
            var command = (com.workflow.contracts.entity.mutation.model.EntityMutationCommand) call.getArgument(0);
            jdbc.update("INSERT INTO status_write VALUES (?,?)", command.operationId(), command.payload().get("status"));
            if (rejectWrite) throw new IllegalStateException("模拟业务写入失败");
            return null;
        });
        var policy = new ProcessEntityStatusPolicy(engine.getRepositoryService());
        var endListener = new com.workflow.process.engine.infrastructure.flowable.ProcessEndListener(engine.getHistoryService(), ends);
        org.springframework.test.util.ReflectionTestUtils.setField(endListener, "statusPolicy", policy);
        engine.getRuntimeService().addEventListener(endListener);
        engine.getRuntimeService().addEventListener(new EntityTransitionStatusListener(
                engine.getRuntimeService(), engine.getRepositoryService(), policy, mutations));
    }
    @BeforeEach void clear() { jdbc.update("DELETE FROM status_write"); rejectWrite = false; clearInvocations(ends); }
    @AfterAll static void close() { if (engine != null) engine.close(); }

    @Test void actualBranchAndLoopWritesEachOccurrenceAndNoImplicitEndWrite() {
        deploy("route", graph("REVIEW"), true);
        var instance = engine.getRuntimeService().startProcessInstanceByKey("route", vars());
        assertEquals(List.of("REVIEW"), states());
        complete(instance.getId(), false);
        assertEquals(List.of("REVIEW", "REJECTED", "REVIEW"), states());
        complete(instance.getId(), true);
        assertEquals(List.of("REVIEW", "REJECTED", "REVIEW", "ACCEPTED"), states());
        verify(ends).publishProcessEnd(instance.getId(), "expense", "record-1", "COMPLETED", null);
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM status_write", Integer.class));
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance.getId()).singleResult());
    }

    @Test void existingInstanceUsesOwnDeployedConfiguration() {
        deploy("pinned", graph("V1"), true);
        var old = engine.getRuntimeService().startProcessInstanceByKey("pinned", vars());
        deploy("pinned", graph("V2"), true);
        complete(old.getId(), false);
        assertEquals(List.of("V1", "REJECTED", "V1"), states());
        engine.getRuntimeService().startProcessInstanceByKey("pinned", vars());
        assertEquals("V2", states().get(3));
    }

    @Test void mutationFailureRollsBackEngineAndBusinessWriteTogether() {
        deploy("rollback", graph("REVIEW"), true);
        var instance = engine.getRuntimeService().startProcessInstanceByKey("rollback", vars());
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        rejectWrite = true;
        assertThrows(RuntimeException.class, () -> transactions.executeWithoutResult(ignored ->
                engine.getTaskService().complete(task.getId(), Map.of("ok", true))));
        assertEquals(List.of("REVIEW"), states());
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @Test void manualTerminationPreservesSpecialBusinessHandlingWhileBpmnEndDoesNot() {
        deploy("manual", graph("REVIEW"), true);
        var manual = engine.getRuntimeService().startProcessInstanceByKey("manual", vars());
        engine.getRuntimeService().deleteProcessInstance(manual.getId(), "主动终止");
        verify(ends).publishProcessEnd(manual.getId(), "expense", "record-1", "TERMINATED", "TERMINATED");
        deploy("terminateEnd", "<startEvent id=\"s\"/><endEvent id=\"e\"><terminateEventDefinition/></endEvent>" + line("f", "s", "e", "CUSTOM_END"), true);
        var bpmnEnd = engine.getRuntimeService().startProcessInstanceByKey("terminateEnd", vars());
        verify(ends).publishProcessEnd(bpmnEnd.getId(), "expense", "record-1", "TERMINATED", null);
        assertEquals("TERMINATED", new ProcessEntityStatusPolicy(engine.getRepositoryService()).endCategory(
                engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(bpmnEnd.getId()).singleResult()));
    }

    @Test void noConfiguredLineAndUnmarkedLegacyDeploymentAreIgnored() {
        deploy("empty", "<startEvent id=\"s\"/><endEvent id=\"e\"/>" + line("f", "s", "e", ""), true);
        var empty = engine.getRuntimeService().startProcessInstanceByKey("empty", vars());
        verify(ends).publishProcessEnd(empty.getId(), "expense", "record-1", "COMPLETED", null);
        deploy("legacy", graph("SHOULD_NOT_WRITE"), false);
        engine.getRuntimeService().startProcessInstanceByKey("legacy", vars());
        assertTrue(states().isEmpty());
    }

    @Test void rejectsConflictingParallelStatesButAllowsSameAndPostJoinStates() {
        assertThrows(IllegalArgumentException.class, () -> sanitizer.sanitize(xml("conflict", parallel("A", "B")), "conflict"));
        assertDoesNotThrow(() -> sanitizer.sanitize(xml("parallel", parallel("A", "A")), "parallel"));
        assertDoesNotThrow(() -> sanitizer.sanitize(xml("sequential", parallel("", "")), "sequential"));
    }

    @Test void nameFilteredHistoryPageRestrictsDefinitionAndInitiatorInDatabase() throws Exception {
        deploy("paged", graph(""), true);
        var definition = engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("paged").singleResult();
        engine.getIdentityService().setAuthenticatedUserId("page-user");
        for (int i = 0; i < 3; i++) engine.getRuntimeService().startProcessInstanceByKey("paged", vars());
        engine.getIdentityService().setAuthenticatedUserId("other-user");
        engine.getRuntimeService().startProcessInstanceByKey("paged", vars());
        engine.getIdentityService().setAuthenticatedUserId(null);
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setDatabaseId("MYSQL");
        // 测试单独建立会话工厂，需要注册生产分页插件才能验证实际页大小和偏移。
        configuration.addInterceptor(new com.workflow.config.database.DatabaseMybatisConfiguration()
                .mybatisPlusInterceptor(new MySqlSchemaDdlDialect()));
        configuration.addMapper(com.workflow.process.instance.infrastructure.persistence.mapper.StartedProcessPageMapper.class);
        var factory = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        var mapper = new org.mybatis.spring.SqlSessionTemplate(factory.getObject())
                .getMapper(com.workflow.process.instance.infrastructure.persistence.mapper.StartedProcessPageMapper.class);
        var ids = Set.of(definition.getId());
        assertEquals(3, mapper.count("page-user", ids, null, null));
        var first = mapper.page("page-user", ids, null, null, 0, 2);
        var last = mapper.page("page-user", ids, null, null, 2, 2);
        assertEquals(2, first.size()); assertEquals(1, last.size());
        assertFalse(first.contains(last.get(0)));
        assertEquals(0, mapper.count("page-user", ids, new Date(System.currentTimeMillis() + 10000), null));
    }

    private static Map<String,Object> vars() { return Map.of("entityCode", "expense", "entityDataId", "record-1"); }
    private void complete(String id, boolean ok) {
        engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult().getId(), Map.of("ok", ok));
    }
    private List<String> states() { return jdbc.queryForList("SELECT status FROM status_write", String.class); }
    private void deploy(String key, String body, boolean tagged) {
        String xml = xml(key, body);
        engine.getRepositoryService().createDeployment().addString(key + ".bpmn20.xml", tagged ? sanitizer.sanitize(xml, key) : xml).deploy();
    }
    private static String xml(String key, String body) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" targetNamespace=\"test\"><process id=\"" + key + "\" isExecutable=\"true\">" + body + "</process></definitions>";
    }
    private static String graph(String entry) {
        return "<startEvent id=\"s\"/><userTask id=\"u\"/><exclusiveGateway id=\"g\"/><endEvent id=\"e\"/>"
                + line("first", "s", "u", entry) + line("review", "u", "g", "")
                + conditional("yes", "g", "e", "ACCEPTED", "${ok}")
                + conditional("no", "g", "s2", "REJECTED", "${!ok}")
                + "<exclusiveGateway id=\"s2\"/>" + line("again", "s2", "u", entry);
    }
    private static String parallel(String a, String b) {
        return "<startEvent id=\"s\"/><parallelGateway id=\"split\"/><userTask id=\"a\"/><userTask id=\"b\"/><parallelGateway id=\"join\"/><endEvent id=\"e\"/>"
                + line("begin","s","split", "") + line("left","split","a",a) + line("right","split","b",b)
                + line("aj","a","join", "") + line("bj","b","join", "") + line("finish","join","e", "FINISHED");
    }
    private static String conditional(String id, String from, String to, String status, String condition) {
        return line(id, from, to, status).replace("</sequenceFlow>", "<conditionExpression xsi:type=\"tFormalExpression\">" + condition + "</conditionExpression></sequenceFlow>");
    }
    private static String line(String id, String from, String to, String status) {
        return "<sequenceFlow id=\"" + id + "\" sourceRef=\"" + from + "\" targetRef=\"" + to + "\"><extensionElements><flowable:properties><flowable:property name=\"entityStatusCode\" value=\"" + status + "\"/></flowable:properties></extensionElements></sequenceFlow>";
    }
}
