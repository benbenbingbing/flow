package com.workflow.process.action;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FlowActionTriggerTiming;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.contracts.process.action.spi.FlowActionProvider;
import com.workflow.process.action.application.*;
import com.workflow.process.action.infrastructure.flowable.FlowActionRuntimeAdapter;
import com.workflow.process.action.infrastructure.persistence.mapper.*;
import com.workflow.process.action.infrastructure.persistence.record.*;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import org.flowable.engine.*;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Modifier;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import static com.workflow.contracts.process.action.model.FlowActionTriggerTiming.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实分发器、执行器、MyBatis Outbox、Spring JDBC 和 Flowable 事务共同验证业务阶段。 */
public class FlowActionOperationExecutionTransactionTest {
    private JdbcDataSource source;
    private JdbcTemplate jdbc;
    private ProcessEngine engine;
    private TransactionTemplate transaction;
    private DataSourceTransactionManager transactions;
    private FlowActionExecutionService executions;
    private FlowActionExecutionMapper mapper;
    private FlowActionExecutionProcessor processor;
    private FlowActionOperationDispatcher operations;
    private final Map<String, FlowAction> actions = new LinkedHashMap<>();
    private final Map<String, FlowActionProvider> handlers = new HashMap<>();
    private String definitionId;

    public static Timestamp utcNow(int precision) { return Timestamp.from(Instant.now()); }

    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:business_actions_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(transactions);
        var configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(source); configuration.setTransactionManager(transactions);
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        engine = configuration.buildProcessEngine();
        var deployment = engine.getRepositoryService().createDeployment().addString("operation.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="operation-test">
                  <process id="operation" isExecutable="true"><startEvent id="start"/>
                  <sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                  <userTask id="review" flowable:assignee="alice"/>
                  <sequenceFlow id="s2" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                  </process></definitions>
                """).deploy();
        definitionId = engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
        jdbc.execute("SET MODE MySQL");
        jdbc.execute("CREATE ALIAS UTC_TIMESTAMP FOR 'com.workflow.process.action.FlowActionOperationExecutionTransactionTest.utcNow'");
        List<String> columns = new ArrayList<>();
        for (var field : FlowActionExecution.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            var annotation = field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
            String name = annotation != null && !annotation.value().isBlank() ? annotation.value()
                    : field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            String type = field.getType() == Integer.class ? "INT" : field.getType() == Long.class ? "BIGINT"
                    : field.getType() == LocalDateTime.class ? "TIMESTAMP" : "VARCHAR(1000000)";
            columns.add(name + " " + type + (name.equals("id") ? " PRIMARY KEY" : "")
                    + (name.equals("lease_token") || name.equals("attempt_no") ? " DEFAULT 0" : ""));
        }
        columns.add("attempt_lease_token BIGINT");
        jdbc.execute("CREATE TABLE process_action_execution (" + String.join(",", columns) + ")");
        jdbc.execute("CREATE UNIQUE INDEX execution_key ON process_action_execution(idempotency_key)");
        jdbc.execute("CREATE TABLE business_change(id VARCHAR(64))");
        var mybatis = new MybatisConfiguration(); mybatis.setDatabaseId("MYSQL"); mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.addMapper(FlowActionExecutionMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(mybatis);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(FlowActionExecutionMapper.class);
        var json = new ObjectMapper().findAndRegisterModules();
        var beans = mock(ApplicationContext.class);
        when(beans.getBean(anyString())).thenAnswer(call -> handlers.get(call.getArgument(0)));
        var failures = new FlowActionFailureStrategyCatalog(List.of(), json, mock(EntityCodeCatalogPort.class), beans);
        var coordinator = new FlowActionFailureCoordinator(failures);
        var actionMapper = mock(FlowActionMapper.class);
        when(actionMapper.selectById(anyString())).thenAnswer(call -> actions.get(call.getArgument(0)));
        executions = proxy(new FlowActionExecutionService(mapper, actionMapper, json, mock(FlowActionCatalogPort.class), mock(SystemAuditPort.class), failures));
        var executor = new FlowActionExecutor(beans, mock(FlowActionRuntimeAdapter.class), executions, json);
        var published = mock(FlowActionService.class);
        when(published.findPublishedActionsByBinding(any(), any(), any(), any())).thenAnswer(call -> actions.values().stream()
                .filter(action -> Objects.equals(action.getTriggerTiming(), call.getArgument(3)))
                .sorted(Comparator.comparing(FlowAction::getSortOrder)).toList());
        var versions = mock(ProcessVersionHistoryMapper.class);
        var version = new ProcessVersionHistory(); version.setId("published-v1");
        when(versions.findByDeploymentId(anyString())).thenReturn(Optional.of(version));
        var dispatcher = new FlowActionEventDispatcher(published, executor, executions, new FlowActionTimingCatalog(List.of()), versions,
                engine.getRepositoryService(), coordinator);
        operations = new FlowActionOperationDispatcher(dispatcher, engine.getRuntimeService(), engine.getHistoryService());
        var scheduler = mock(TaskScheduler.class);
        doReturn(mock(ScheduledFuture.class)).when(scheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
        processor = new FlowActionExecutionProcessor(executions, actionMapper, executor, scheduler, coordinator);
    }

    @AfterEach void close() { if (engine != null) engine.close(); }

    @Test void beforeStartExecutesWithoutAnInstanceAndPreparationFeedsTheRealEngine() {
        var seen = new ArrayList<String>();
        action(PROCESS_START_VALIDATING, "IN_TRANSACTION", ctx -> {
            assertNull(ctx.getProcessInstanceId()); assertEquals("data-1", ctx.getEntityDataId());
            assertThrows(IllegalStateException.class, () -> ctx.setProcessVariable("risk", "LOW"));
            seen.add(ctx.getTriggerTiming());
        });
        action(PROCESS_STARTING, "IN_TRANSACTION", ctx -> {
            assertNull(ctx.getProcessInstanceId()); ctx.setProcessVariable("risk", "HIGH"); seen.add(ctx.getTriggerTiming());
        });
        transaction.executeWithoutResult(status -> {
            var event = operations.process(definitionId, null, "START", "alice", Map.of("entityCode", "expense", "entityDataId", "data-1"), Map.of());
            operations.fire(event, PROCESS_START_VALIDATING);
            operations.fire(event, PROCESS_STARTING);
            String id = engine.getRuntimeService().startProcessInstanceById(definitionId, event.getVariables()).getId();
            assertEquals("HIGH", engine.getRuntimeService().getVariable(id, "risk"));
        });
        assertEquals(List.of("PROCESS_START_VALIDATING", "PROCESS_STARTING"), seen);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM process_action_execution WHERE process_instance_id IS NULL AND status='SUCCESS'", Integer.class));
    }

    @Test void rejectingPreconditionPreventsEngineCreationAndBusinessWrite() {
        action(PROCESS_START_VALIDATING, "IN_TRANSACTION", ctx -> { throw new IllegalArgumentException("业务禁止发起"); });
        assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status -> {
            operations.fire(operations.process(definitionId, null, "START", "alice", Map.of(), Map.of()), PROCESS_START_VALIDATING);
            jdbc.update("INSERT INTO business_change VALUES ('unexpected')");
            engine.getRuntimeService().startProcessInstanceById(definitionId);
        }));
        assertEquals(0L, engine.getRuntimeService().createProcessInstanceQuery().count());
        assertEquals(0, count("business_change"));
        assertEquals("DEAD", jdbc.queryForObject("SELECT status FROM process_action_execution", String.class));
    }

    @Test void postCommitNotificationIsInvisibleUntilCommitAndRetriesWithTheOriginalSnapshotAndKey() throws Exception {
        var seen = new ArrayList<FlowActionContext>();
        action(TASK_SUBMITTED, "AFTER_COMMIT", ctx -> {
            seen.add(ctx);
            assertEquals(Map.of("amount", 12), ctx.getSubmittedFormData());
            assertEquals("LOW", ctx.getVariables().get("risk"));
            assertEquals("approve", ctx.getVariables().get("approved"));
            if (seen.size() == 1) throw new IllegalStateException("外部暂不可用");
        });
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId, Map.of("risk", "LOW")).getId();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        engine.getTaskService().setVariableLocal(task.getId(), "risk", "TASK_LOCAL_VALUE");
        transaction.executeWithoutResult(status -> {
            var operation = operations.task(task, task.getId(), "SUBMIT", "alice", Map.of("amount", 12), Map.of("comment", "同意"));
            engine.getTaskService().complete(task.getId(), Map.of("approved", "approve"));
            operations.fire(operation, TASK_SUBMITTED);
            assertEquals(1, count("process_action_execution"));
            try (var connection = source.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM process_action_execution")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            } catch (java.sql.SQLException error) { throw new RuntimeException(error); }
            assertTrue(seen.isEmpty());
        });
        String executionId = jdbc.queryForObject("SELECT id FROM process_action_execution", String.class);
        process(executionId);
        assertEquals("FAILED", mapper.selectById(executionId).getStatus());
        jdbc.update("UPDATE process_action_execution SET next_retry_time=NULL WHERE id=?", executionId);
        process(executionId);
        assertEquals("SUCCESS", mapper.selectById(executionId).getStatus());
        assertEquals(2, seen.size());
        assertEquals(seen.get(0).getIdempotencyKey(), seen.get(1).getIdempotencyKey());
        assertEquals("published-v1", seen.get(1).getProcessVersionId());
    }

    @Test void outerTransactionFailureRemovesNotificationAndRestoresEngineTask() {
        var called = new ArrayList<String>();
        action(TASK_SUBMITTED, "AFTER_COMMIT", ctx -> called.add("sent"));
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            var operation = operations.task(task, task.getId(), "SUBMIT", "alice", Map.of(), Map.of());
            engine.getTaskService().complete(task.getId());
            operations.fire(operation, TASK_SUBMITTED);
            throw new IllegalStateException("外层业务失败");
        }));
        assertEquals(0, count("process_action_execution"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
        assertTrue(called.isEmpty());
    }

    @Test void businessActionsCannotRunOutsideTheCommandTransaction() {
        action(TASK_SUBMIT_VALIDATING, "IN_TRANSACTION", ctx -> fail("缺少业务事务时不能调用处理器"));
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        assertThrows(IllegalStateException.class, () -> operations.fire(
                operations.task(task, task.getId(), "SUBMIT", "alice", Map.of(), Map.of()), TASK_SUBMIT_VALIDATING));
        assertEquals(0, count("process_action_execution"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @Test void runtimeRejectsIllegallyImportedPreconditionBeforeQueueingOrInvoking() {
        action(TASK_SUBMIT_VALIDATING, "AFTER_COMMIT", ctx -> fail("不能调用处理器"));
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status ->
                operations.fire(operations.task(task, task.getId(), "SUBMIT", "alice", Map.of(), Map.of()), TASK_SUBMIT_VALIDATING)));
        assertEquals(0, count("process_action_execution"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PROCESS_TERMINATE_VALIDATING", "PROCESS_TERMINATING", "PROCESS_TERMINATE_EXECUTED", "SUCCESS"})
    void terminateStagesSurroundRealDeletionAndLateFailureRollsBack(String failureStage) {
        engine.getIdentityService().setAuthenticatedUserId("alice");
        String instanceId;
        try { instanceId = engine.getRuntimeService().startProcessInstanceById(definitionId).getId(); }
        finally { engine.getIdentityService().setAuthenticatedUserId(null); }
        var seen = new ArrayList<String>();
        for (var timing : List.of(PROCESS_TERMINATE_VALIDATING, PROCESS_TERMINATING, PROCESS_TERMINATE_EXECUTED)) {
            action(timing, "IN_TRANSACTION", ctx -> {
                seen.add(ctx.getTriggerTiming());
                assertEquals("终止原因", ctx.getOperationParameters().get("reason"));
                long count = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count();
                assertEquals(timing == PROCESS_TERMINATE_EXECUTED ? 0L : 1L, count);
                if (failureStage.equals(ctx.getTriggerTiming())) throw new IllegalArgumentException("禁止终止");
            });
        }
        var logs = mock(com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper.class);
        when(logs.insert(any(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog.class)))
                .thenAnswer(call -> jdbc.update("INSERT INTO business_change VALUES ('terminate-log')"));
        var service = proxy(new com.workflow.process.instance.application.ProcessTerminationService(operations,
                engine.getRuntimeService(), engine.getHistoryService(), logs,
                mock(com.workflow.process.task.application.ProcessTaskService.class),
                mock(com.workflow.contracts.identity.port.IdentityDirectoryPort.class),
                mock(com.workflow.contracts.entity.port.EntityRecordPort.class),
                mock(com.workflow.process.task.application.operation.NodeOperationCapabilityService.class)));
        if (failureStage.endsWith("VALIDATING") || failureStage.equals("PROCESS_TERMINATING")) {
            assertThrows(IllegalArgumentException.class, () -> service.terminateProcess(instanceId, "alice", "终止原因"));
        } else {
            assertEquals(failureStage.equals("SUCCESS") ? 200 : 500,
                    service.terminateProcess(instanceId, "alice", "终止原因").getCode());
        }
        boolean success = failureStage.equals("SUCCESS");
        assertEquals(success ? 0L : 1L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count());
        assertEquals(success ? 1 : 0, count("business_change"));
        assertEquals(success ? "PROCESS_TERMINATE_EXECUTED" : failureStage, seen.get(seen.size() - 1));
    }

    /** 本地子任务实际 JDBC 副作用与 Spring 事务共用，晚期处理器失败也必须撤销该写入。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADD_SIGN_TASK_VALIDATING", "ADD_SIGN_TASK_COMPLETING", "ADD_SIGN_TASK_COMPLETED", "SUCCESS"})
    void localAddSignStagesUseDedicatedContextAndRollbackTogether(String failureStage) {
        String instanceId = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var sourceTask = engine.getTaskService().createTaskQuery().processInstanceId(instanceId).singleResult();
        var sign = new com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSign();
        sign.setId("sign"); sign.setSourceTaskId(sourceTask.getId()); sign.setProcessInstanceId(instanceId);
        sign.setOperationType("BEFORE"); sign.setSourceCompleted(false);
        var child = new com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSignUser();
        child.setAddSignId("sign"); child.setUserId("alice"); child.setGeneratedTaskId("child"); child.setStatus("TODO");
        var mirror = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
        mirror.setId(1L); mirror.setTaskId("child"); mirror.setStatus("todo");
        var tasks = mock(com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper.class);
        when(tasks.selectByTaskId("child")).thenReturn(mirror);
        when(tasks.completeTask(any(), any(), any(), any(), anyLong()))
                .thenAnswer(call -> jdbc.update("INSERT INTO business_change VALUES ('child-completed')"));
        var signs = mock(com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper.class);
        when(signs.selectByIdForUpdate("sign")).thenReturn(sign);
        var users = mock(com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper.class);
        when(users.findByGeneratedTaskId("child")).thenReturn(child);
        when(users.findByGeneratedTaskIdForUpdate("child")).thenReturn(child);
        var seen = new ArrayList<String>();
        for (var timing : List.of(ADD_SIGN_TASK_VALIDATING, ADD_SIGN_TASK_COMPLETING, ADD_SIGN_TASK_COMPLETED)) {
            action(timing, "IN_TRANSACTION", ctx -> {
                seen.add(ctx.getTriggerTiming());
                assertEquals("child", ctx.getTaskId()); assertEquals("LOCAL_ADD_SIGN", ctx.getTaskKind());
                assertEquals(sourceTask.getId(), ctx.getSourceTaskId()); assertEquals("review", ctx.getElementId());
                assertEquals(timing == ADD_SIGN_TASK_COMPLETED ? 1 : 0, count("business_change"));
                assertNotNull(engine.getTaskService().createTaskQuery().taskId(sourceTask.getId()).singleResult());
                if (failureStage.equals(ctx.getTriggerTiming())) throw new IllegalArgumentException("禁止办理子任务");
            });
        }
        var service = proxy(new com.workflow.process.task.application.TaskAddSignService(operations,
                engine.getTaskService(), tasks, signs, users,
                mock(com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper.class),
                mock(com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper.class),
                new ObjectMapper(), mock(com.workflow.process.task.application.operation.NodeOperationCapabilityService.class),
                mock(com.workflow.process.task.application.TaskActionService.class),
                mock(com.workflow.process.task.application.TaskIdentityAccessService.class)));
        com.workflow.admin.security.context.UserContext.setCurrentUser("alice-id", "alice");
        try {
            if (failureStage.equals("SUCCESS")) service.completeAddSignTask("child", "approve", "意见");
            else assertThrows(IllegalArgumentException.class, () -> service.completeAddSignTask("child", "approve", "意见"));
        } finally { com.workflow.admin.security.context.UserContext.clear(); }
        assertEquals(failureStage.equals("SUCCESS") ? 1 : 0, count("business_change"));
        assertEquals(failureStage.equals("SUCCESS") ? "ADD_SIGN_TASK_COMPLETED" : failureStage, seen.get(seen.size() - 1));
    }

    private void action(FlowActionTriggerTiming timing, String mode, Consumer<FlowActionContext> handler) {
        String id = timing.name();
        var action = new FlowAction(); action.setId(id); action.setActionName(id); action.setInterfaceName(id);
        action.setTriggerTiming(id); action.setScopeType(timing.getScopeTypes().iterator().next().name());
        action.setExecutionMode(mode); action.setFailurePolicy(mode.equals("AFTER_COMMIT") ? "RETRY" : "ROLLBACK");
        action.setSortOrder(actions.size()); action.setEnabled(true); action.setRetryConfig("{\"maxRetries\":3}");
        actions.put(id, action);
        handlers.put(id, new FlowActionProvider() {
            @Override public void execute(FlowActionContext context) { handler.accept(context); }
            @Override public boolean retryable() { return true; }
        });
    }

    private void process(String id) {
        var claimed = executions.claim(id, "worker", 300); assertNotNull(claimed);
        processor.process(id, "worker", claimed.getLeaseToken(), 300);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        var advice = new TransactionInterceptor(); advice.setTransactionManager(transactions);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvice(advice);
        return (T) factory.getProxy();
    }
}
