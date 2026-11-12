package com.workflow.process.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.contracts.entity.mutation.model.*;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.entity.definition.application.*;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.process.action.application.*;
import com.workflow.process.action.infrastructure.flowable.*;
import com.workflow.process.action.infrastructure.persistence.record.*;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.engine.infrastructure.flowable.EntityTransitionStatusListener;
import com.workflow.process.status.application.ProcessEntityStatusPolicy;
import org.flowable.engine.*;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实 Flowable -> 事件分发 -> 动作执行 -> JDBC 事务，验证命中分支、循环、发布版本和共同回滚。 */
class EntityFieldAssignmentFlowableTest {
    static ProcessEngine engine;
    static JdbcTemplate jdbc;
    static TransactionTemplate transactions;
    static StaticApplicationContext beans;
    static final Map<String, Map<String, List<FlowAction>>> published = new HashMap<>();
    static boolean failWrite;

    @BeforeAll static void setup() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:assignment_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var tm = new DataSourceTransactionManager(dataSource);
        transactions = new TransactionTemplate(tm); jdbc = new JdbcTemplate(dataSource);
        var cfg = new SpringProcessEngineConfiguration(); cfg.setDataSource(dataSource); cfg.setTransactionManager(tm);
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE); cfg.setHistory("full"); cfg.setAsyncExecutorActivate(false);
        engine = cfg.buildProcessEngine();
        jdbc.execute("CREATE TABLE business_record(id VARCHAR(50) PRIMARY KEY, flag BOOLEAN, note VARCHAR(100), other_value VARCHAR(100), status VARCHAR(30))");
        jdbc.execute("CREATE TABLE mutation_call(id VARCHAR(200) PRIMARY KEY)");
        var mutations = mock(EntityMutationPort.class);
        when(mutations.execute(any())).thenAnswer(call -> {
            EntityMutationCommand command = call.getArgument(0);
            jdbc.update("INSERT INTO mutation_call VALUES (?)", command.operationId());
            if (command.operationType() == EntityMutationOperationType.STATUS_CHANGE) {
                jdbc.update("UPDATE business_record SET status=? WHERE id=?", command.payload().get("status"), command.recordId());
            } else {
                Map<?, ?> patch = (Map<?, ?>) command.payload().get("data");
                if (patch.containsKey("flag")) jdbc.update("UPDATE business_record SET flag=? WHERE id=?", patch.get("flag"), command.recordId());
                if (patch.containsKey("note")) jdbc.update("UPDATE business_record SET note=? WHERE id=?", patch.get("note"), command.recordId());
                if (failWrite) throw new IllegalStateException("模拟字段写入失败");
            }
            return null;
        });
        var snapshots = mock(EntityPublishedSnapshotService.class);
        var snapshot = new EntityPublishedSnapshot();
        snapshot.setFields(List.of(field("flag", EntityField.FieldType.BOOLEAN), field("note", EntityField.FieldType.STRING)));
        when(snapshots.getLatestByEntityCode("expense")).thenReturn(snapshot);
        var json = new ObjectMapper();
        var handler = new EntityFieldAssignmentFlowActionHandler(mock(EntityCodeCatalogPort.class), snapshots,
                new EntityFieldValidationRuleService(json), mock(SysDictItemService.class), mutations, json);
        beans = new StaticApplicationContext(); beans.getBeanFactory().registerSingleton(EntityFieldAssignmentFlowActionHandler.BEAN_NAME, handler);
        var helper = mock(FlowActionRuntimeAdapter.class);
        var executions = mock(FlowActionExecutionService.class);
        when(executions.createInTransactionAudit(any(), any(), anyString(), any())).thenAnswer(call -> {
            var record = new FlowActionExecution(); record.setId(UUID.randomUUID().toString()); return record;
        });
        var actions = mock(FlowActionService.class);
        when(actions.findPublishedActionsByBinding(anyString(), eq("SEQUENCE_FLOW"), anyString(), eq("TRANSITION_TAKEN")))
                .thenAnswer(call -> published.getOrDefault(call.getArgument(0), Map.of()).getOrDefault(call.getArgument(2), List.of()));
        var histories = mock(ProcessVersionHistoryMapper.class);
        when(histories.findByDeploymentId(anyString())).thenAnswer(call -> {
            var history = new ProcessVersionHistory(); history.setId(call.getArgument(0)); return Optional.of(history);
        });
        var executor = new FlowActionExecutor(beans, helper, executions, json);
        var dispatcher = new FlowActionEventDispatcher(actions, executor, executions, new FlowActionTimingCatalog(List.of()),
                histories, engine.getRepositoryService(), mock(FlowActionFailureCoordinator.class));
        engine.getRuntimeService().addEventListener(new EntityTransitionStatusListener(engine.getRuntimeService(),
                engine.getRepositoryService(), new ProcessEntityStatusPolicy(engine.getRepositoryService()), mutations));
        engine.getRuntimeService().addEventListener(new FlowActionEngineEventListener(dispatcher, engine.getRuntimeService(), helper));
    }
    @BeforeEach void reset() {
        failWrite = false; jdbc.update("DELETE FROM mutation_call"); jdbc.update("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record VALUES ('record',NULL,'before','keep','DRAFT')");
    }
    @AfterAll static void close() { if (engine != null) engine.close(); if (beans != null) beans.close(); }

    @Test void writesOnlyTakenBranchPreservesOtherFieldsAndLoopGetsNewExecutionKeys() {
        deploy("loopAssignment", "entry");
        var instance = engine.getRuntimeService().startProcessInstanceByKey("loopAssignment", vars());
        assertEquals("entry", note()); assertEquals(1, count());
        complete(instance.getId(), false);
        assertEquals(false, jdbc.queryForObject("SELECT flag FROM business_record", Boolean.class)); assertNull(note());
        assertEquals(2, count());
        complete(instance.getId(), false); assertEquals(3, count());
        complete(instance.getId(), true);
        assertEquals("${literal}", note()); assertEquals(5, count());
        assertEquals("APPROVED", status());
        assertEquals("keep", jdbc.queryForObject("SELECT other_value FROM business_record", String.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM mutation_call", Integer.class));
    }

    @Test void mutationFailureRollsBackTaskStatusAndAllFieldWritesTogether() {
        deploy("rollbackAssignment", "entry");
        var instance = engine.getRuntimeService().startProcessInstanceByKey("rollbackAssignment", vars());
        String task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult().getId();
        failWrite = true;
        assertThrows(RuntimeException.class, () -> complete(instance.getId(), true));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task).singleResult());
        assertEquals("DRAFT", status()); assertEquals("entry", note()); assertEquals(1, count());
        failWrite = false; complete(instance.getId(), true); assertEquals("APPROVED", status());
    }

    @Test void oldInstanceKeepsItsPublishedActionParametersAfterNewDeployment() {
        deploy("pinnedAssignment", "old", "old-version-result");
        var old = engine.getRuntimeService().startProcessInstanceByKey("pinnedAssignment", vars());
        deploy("pinnedAssignment", "new");
        var fresh = engine.getRuntimeService().startProcessInstanceByKey("pinnedAssignment", vars());
        assertEquals("new", note());
        // 新部署不修改旧版本的参数；旧实例继续通过原部署查到自己的动作。
        complete(old.getId(), true); assertEquals("old-version-result", note());
        complete(fresh.getId(), true); assertEquals("${literal}", note());
    }

    static void deploy(String key, String entry) {
        deploy(key, entry, "${literal}");
    }
    static void deploy(String key, String entry, String passValue) {
        String xml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"test\">"
                + "<process id=\"" + key + "\" isExecutable=\"true\"><extensionElements><flowable:properties><flowable:property name=\"entityStatusPolicy\" value=\"TRANSITION_V1\"/></flowable:properties></extensionElements>"
                + "<startEvent id=\"s\"/><userTask id=\"task\"/><exclusiveGateway id=\"g\"/><endEvent id=\"e\"/>"
                + "<sequenceFlow id=\"entry\" sourceRef=\"s\" targetRef=\"task\"/><sequenceFlow id=\"toGateway\" sourceRef=\"task\" targetRef=\"g\"/>"
                + "<sequenceFlow id=\"pass\" sourceRef=\"g\" targetRef=\"e\"><extensionElements><flowable:properties><flowable:property name=\"entityStatusCode\" value=\"APPROVED\"/></flowable:properties></extensionElements><conditionExpression>${ok}</conditionExpression></sequenceFlow>"
                + "<sequenceFlow id=\"back\" sourceRef=\"g\" targetRef=\"task\"><conditionExpression>${!ok}</conditionExpression></sequenceFlow></process></definitions>";
        var deployment = engine.getRepositoryService().createDeployment().addString(key + ".bpmn20.xml", xml).deploy();
        published.put(deployment.getId(), Map.of("entry", List.of(action("entry", params(entry))),
                "pass", List.of(action("pass", params(passValue))), "back", List.of(action("back",
                        "{\"assignments\":[{\"fieldCode\":\"flag\",\"operation\":\"SET\",\"value\":false},{\"fieldCode\":\"note\",\"operation\":\"CLEAR\"}]}"))));
    }
    static FlowAction action(String id, String params) {
        var action = new FlowAction(); action.setId(id); action.setEnabled(true); action.setScopeType("SEQUENCE_FLOW");
        action.setElementId(id); action.setInterfaceName(EntityFieldAssignmentFlowActionHandler.BEAN_NAME);
        action.setExecutionMode("IN_TRANSACTION"); action.setFailurePolicy("ROLLBACK"); action.setParamsJson(params); return action;
    }
    static String params(String note) { return "{\"assignments\":[{\"fieldCode\":\"note\",\"operation\":\"SET\",\"value\":\"" + note + "\"}]}"; }
    static EntityField field(String code, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setFieldName(code); field.setFieldType(type); return field;
    }
    static void complete(String instance, boolean ok) {
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instance).singleResult();
        transactions.executeWithoutResult(ignored -> engine.getTaskService().complete(task.getId(), Map.of("ok", ok)));
    }
    static Map<String, Object> vars() { return Map.of("entityCode", "expense", "entityDataId", "record"); }
    static String note() { return jdbc.queryForObject("SELECT note FROM business_record", String.class); }
    static String status() { return jdbc.queryForObject("SELECT status FROM business_record", String.class); }
    static int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM mutation_call", Integer.class); }
}
