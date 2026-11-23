package com.workflow.process.action;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.port.platform.SystemOperationLogPort;
import com.workflow.port.entity.EntityCodeCatalogPort;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FlowActionTriggerTiming;
import com.workflow.port.process.ProcessActionDefinitionPort;
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
        executions = proxy(new FlowActionExecutionService(mapper, actionMapper, json, mock(ProcessActionDefinitionPort.class), mock(SystemOperationLogPort.class), failures));
        var runtime = mock(FlowActionRuntimeAdapter.class);
        // 流程动作的受控变量写入在此适配成真实引擎写入，路由及回滚断言不能停留在 mock 调用。
        doAnswer(call -> { engine.getRuntimeService().setVariable(call.getArgument(0), call.getArgument(1), call.getArgument(2)); return null; })
                .when(runtime).setVariable(anyString(), anyString(), any());
        var executor = new FlowActionExecutor(beans, runtime, executions, json);
        var published = mock(FlowActionService.class);
        when(published.findPublishedActionsByBinding(any(), any(), any(), any())).thenAnswer(call -> actions.values().stream()
                .filter(action -> Objects.equals(action.getTriggerTiming(), call.getArgument(3)))
                .filter(action -> Objects.equals(action.getScopeType(), call.getArgument(1))
                        && Objects.equals(action.getElementId(), call.getArgument(2)))
                .sorted(Comparator.comparing(FlowAction::getSortOrder)).toList());
        var versions = mock(ProcessVersionHistoryMapper.class);
        var version = new ProcessVersionHistory(); version.setId("published-v1");
        when(versions.findByDeploymentId(anyString())).thenReturn(Optional.of(version));
        var dispatcher = new FlowActionEventDispatcher(published, executor, executions, new FlowActionTimingCatalog(List.of()), versions,
                engine.getRepositoryService(), coordinator);
        operations = new FlowActionOperationDispatcher(dispatcher, engine.getRuntimeService(), engine.getHistoryService());
        engine.getRuntimeService().addEventListener(new com.workflow.process.action.infrastructure.flowable.FlowActionEngineEventListener(
                dispatcher, engine.getRuntimeService(), mock(FlowActionRuntimeAdapter.class), operations));
        var scheduler = mock(TaskScheduler.class);
        doReturn(mock(ScheduledFuture.class)).when(scheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
        processor = new FlowActionExecutionProcessor(executions, actionMapper, executor, scheduler, coordinator);
    }

    @AfterEach void close() { if (engine != null) engine.close(); }

    /** 普通入口消费原生启动登记；即使流程立即结束，也须先完成发起同步再按顺序执行动作。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,SUCCESS", "true,SUCCESS", "false,START_AFTER", "true,START_AFTER", "false,OUTER", "true,OUTER"})
    void mergedStartAfterRunsOnceAfterPlatformWritesAndRollsBackWithTheStart(boolean immediateEnd, String failure) {
        if (immediateEnd) engine.getRepositoryService().createDeployment().addString("immediate-start.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="start-test">
                <process id="immediateStart" isExecutable="true"><startEvent id="start"/>
                <sequenceFlow id="f" sourceRef="start" targetRef="end"/><endEvent id="end"/>
                </process></definitions>
                """).deploy();
        var seen = new ArrayList<String>();
        action(PROCESS_START_EXECUTED, "IN_TRANSACTION", ctx -> {
            assertEquals(2, count("business_change"), "发起后必须等待关联和待办同步完成");
            assertEquals("START", ctx.getBusinessOperation()); assertEquals("ready", ctx.getVariable("prepared"));
            assertEquals(immediateEnd ? 0L : 1L, engine.getTaskService().createTaskQuery().processInstanceId(ctx.getProcessInstanceId()).count());
            seen.add("start-after");
            if (failure.equals("START_AFTER")) throw new IllegalArgumentException("发起后拒绝");
        });
        // 模拟原有发起后动作与迁入动作共用一组：依旧各执行一次，按配置顺序执行。
        action(PROCESS_START_EXECUTED, "IN_TRANSACTION", ctx -> seen.add("merged-action"));
        action(PROCESS_START_EXECUTED, "AFTER_COMMIT", ctx -> {
            assertEquals("ready", ctx.getVariable("prepared")); seen.add("notification");
        });
        action(PROCESS_COMPLETED, "IN_TRANSACTION", ctx -> {
            assertEquals(List.of("start-after", "merged-action"), seen); seen.add("end");
        });
        Runnable command = () -> transaction.executeWithoutResult(status -> {
            var instance = immediateEnd ? engine.getRuntimeService().startProcessInstanceByKey("immediateStart", Map.of("prepared", "ready"))
                    : engine.getRuntimeService().startProcessInstanceById(definitionId, Map.of("prepared", "ready"));
            assertTrue(seen.isEmpty(), "引擎启动事件不能提前分发发起后动作");
            jdbc.update("INSERT INTO business_change VALUES ('link')");
            jdbc.update("INSERT INTO business_change VALUES ('inbox')");
            var operation = operations.process(instance.getProcessDefinitionId(), instance.getId(), "START", "alice", Map.of(), Map.of());
            operations.processStarted(operation); // 同一实例重复引擎回调不能新增一次动作。
            operations.fire(operation, PROCESS_START_EXECUTED);
            operations.fire(operation, PROCESS_START_EXECUTED);
            assertEquals(List.of("start-after", "merged-action"), seen);
            if (failure.equals("OUTER")) throw new IllegalStateException("发起后外层失败");
        });
        if (failure.equals("START_AFTER")) assertThrows(IllegalArgumentException.class, command::run);
        else if (failure.equals("OUTER")) assertThrows(IllegalStateException.class, command::run);
        else command.run();
        if (failure.equals("SUCCESS")) {
            assertEquals(immediateEnd ? List.of("start-after", "merged-action", "end") : List.of("start-after", "merged-action"), seen);
            process(jdbc.queryForObject("SELECT id FROM process_action_execution WHERE status='PENDING'", String.class));
            assertEquals("notification", seen.get(seen.size() - 1));
            assertEquals(2, count("business_change"));
        } else {
            assertEquals(failure.equals("START_AFTER") ? List.of("start-after") : List.of("start-after", "merged-action"), seen);
            assertEquals(0, count("business_change"));
            assertEquals(0L, engine.getRuntimeService().createProcessInstanceQuery().count());
            assertEquals(0L, engine.getHistoryService().createHistoricProcessInstanceQuery().count());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_action_execution WHERE status='PENDING'", Integer.class));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void calledProcessUsesTheSameStartAfterOnlyAfterItsInboxProjection(boolean rejectChild) {
        var projection = mock(com.workflow.process.task.application.TaskInboxProjectionService.class);
        var provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(projection);
        engine.getRuntimeService().addEventListener(new com.workflow.process.task.infrastructure.flowable.TaskInboxProjectionListener(provider));
        doAnswer(call -> {
            String taskId = call.getArgument(0);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM business_change WHERE id=?", Integer.class, taskId) == 0) {
                jdbc.update("INSERT INTO business_change VALUES (?)", taskId);
            }
            var task = engine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
            engine.getRuntimeService().setVariable(task.getProcessInstanceId(), "projectionReady", true);
            return null;
        }).when(projection).synchronizeTask(anyString());
        engine.getRepositoryService().createDeployment().addString("called-start.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="called-start-test">
                <process id="startChild" isExecutable="true"><startEvent id="cs"/>
                <sequenceFlow id="cf" sourceRef="cs" targetRef="review"/><userTask id="review"/></process>
                <process id="startParent" isExecutable="true"><startEvent id="ps"/>
                <sequenceFlow id="pf" sourceRef="ps" targetRef="call"/><callActivity id="call" calledElement="startChild">
                  <extensionElements>
                    <flowable:in sourceExpression="${'child_entity'}" target="entityCode"/>
                    <flowable:in sourceExpression="${'child-data'}" target="entityDataId"/>
                  </extensionElements>
                </callActivity></process></definitions>
                """).deploy();
        var seen = new ArrayList<String>();
        var notified = new ArrayList<String>();
        action(PROCESS_START_EXECUTED, "IN_TRANSACTION", ctx -> {
            assertEquals(2, count("business_change"), "启动登记早于任务创建，发起后仍必须晚于实际待办投影");
            assertEquals("START", ctx.getBusinessOperation()); seen.add(ctx.getEntityCode());
            if (ctx.getProcessDefinitionId().startsWith("startChild:")) {
                assertEquals("child_entity", ctx.getEntityCode()); assertEquals("child-data", ctx.getEntityDataId());
                assertEquals(true, ctx.getVariable("projectionReady"));
                if (rejectChild) throw new IllegalArgumentException("子流程发起后拒绝");
            } else assertEquals("parent_entity", ctx.getEntityCode());
        });
        action(PROCESS_START_EXECUTED, "AFTER_COMMIT", ctx -> notified.add(ctx.getEntityCode()));
        Runnable command = () -> transaction.executeWithoutResult(status -> {
            engine.getRuntimeService().startProcessInstanceByKey("startParent", Map.of("entityCode", "parent_entity", "entityDataId", "parent-data"));
            jdbc.update("INSERT INTO business_change VALUES ('command-sync')");
            assertTrue(seen.isEmpty()); assertTrue(notified.isEmpty());
        });
        if (rejectChild) assertThrows(IllegalArgumentException.class, command::run);
        else command.run();
        assertEquals(List.of("parent_entity", "child_entity"), seen);
        assertEquals(rejectChild ? 0 : 2, count("business_change"));
        assertEquals(rejectChild ? 0L : 2L, engine.getRuntimeService().createProcessInstanceQuery().count());
        if (rejectChild) assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_action_execution WHERE status='PENDING'", Integer.class));
        else {
            jdbc.queryForList("SELECT id FROM process_action_execution WHERE status='PENDING' ORDER BY id", String.class).forEach(this::process);
            assertEquals(Set.of("parent_entity", "child_entity"), new HashSet<>(notified)); assertEquals(2, notified.size());
        }
    }

    @Test void failedFirstNodeDoesNotRunStartAfterOrLeaveNotifications() {
        action(PROCESS_START_EXECUTED, "IN_TRANSACTION", ctx -> fail("首节点执行失败不能触发发起后"));
        action(PROCESS_START_EXECUTED, "AFTER_COMMIT", ctx -> fail("失败发起不能通知"));
        action(NODE_ENTERED, "IN_TRANSACTION", ctx -> { throw new IllegalArgumentException("首节点失败"); });
        assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status ->
                engine.getRuntimeService().startProcessInstanceById(definitionId)));
        assertEquals(0L, engine.getRuntimeService().createProcessInstanceQuery().count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_action_execution WHERE status='PENDING'", Integer.class));
    }

    @Test void independentStartTransactionDoesNotConsumeTheOuterPendingStart() {
        var seen = new ArrayList<String>();
        action(PROCESS_START_EXECUTED, "IN_TRANSACTION", ctx -> seen.add((String) ctx.getVariable("marker")));
        var independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            engine.getRuntimeService().startProcessInstanceById(definitionId, Map.of("marker", "outer"));
            independent.executeWithoutResult(inner -> engine.getRuntimeService().startProcessInstanceById(definitionId, Map.of("marker", "inner")));
            assertEquals(List.of("inner"), seen);
            throw new IllegalStateException("回滚外层发起");
        }));
        assertEquals(List.of("inner"), seen);
        assertEquals(1L, engine.getRuntimeService().createProcessInstanceQuery().count());
    }

    @Test void userTaskNativeEventsFollowTheDropdownLifecycleOrder() {
        var seen = new ArrayList<String>();
        for (var timing : List.of(NODE_ENTERED, TASK_ASSIGNED, TASK_CREATED, NODE_COMPLETED)) {
            action(timing, "IN_TRANSACTION", ctx -> {
                if ("review".equals(ctx.getElementId())) seen.add(ctx.getTriggerTiming());
            });
        }
        String id = transaction.execute(status -> engine.getRuntimeService().startProcessInstanceById(definitionId).getId());
        assertEquals(List.of("NODE_ENTERED", "TASK_ASSIGNED", "TASK_CREATED"), seen);
        transaction.executeWithoutResult(status -> engine.getTaskService().complete(
                engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult().getId()));
        assertEquals(List.of("NODE_ENTERED", "TASK_ASSIGNED", "TASK_CREATED", "NODE_COMPLETED"), seen);
    }

    /** 合并后的节点时机仍能读取本次结果并决定路由；拒绝时恢复真实待办及同事务业务写入。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"approve,false", "reject,false", "approve,true", "reject,true"})
    void nodeExitReadsTheCurrentOutcomeBeforeRoutingAndRollsBackAtomically(String outcome, boolean reject) {
        engine.getRepositoryService().createDeployment().addString("node-exit.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  targetNamespace="node-exit-test">
                <process id="nodeExit" isExecutable="true"><startEvent id="start"/>
                <sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                <userTask id="review" name="审批" flowable:assignee="alice"/>
                <sequenceFlow id="s2" sourceRef="review" targetRef="route"/>
                <exclusiveGateway id="route" default="fallback"/>
                <sequenceFlow id="chosen" sourceRef="route" targetRef="selected">
                  <conditionExpression xsi:type="tFormalExpression">${routeReady}</conditionExpression>
                </sequenceFlow>
                <sequenceFlow id="fallback" sourceRef="route" targetRef="unexpected"/>
                <userTask id="selected"/><userTask id="unexpected"/>
                </process></definitions>
                """).deploy();
        String id = engine.getRuntimeService().startProcessInstanceByKey("nodeExit", Map.of("approved", "previous")).getId();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        var seen = new ArrayList<String>();
        action(NODE_COMPLETED, "IN_TRANSACTION", ctx -> {
            if (!"review".equals(ctx.getElementId())) return;
            assertEquals(task.getId(), ctx.getTaskId());
            assertEquals("审批", ctx.getTaskName()); assertEquals("alice", ctx.getTaskAssignee());
            assertEquals(outcome, ctx.getVariable("approved")); assertEquals(outcome, ctx.getApprovalAction());
            assertEquals(0L, engine.getTaskService().createTaskQuery().taskDefinitionKey("selected").count());
            jdbc.update("INSERT INTO business_change VALUES ('node-exit')");
            ctx.setProcessVariable("routeReady", true); seen.add(ctx.getTriggerTiming());
            if (reject) throw new IllegalArgumentException("拒绝离开节点");
        });
        action(NODE_COMPLETED, "AFTER_COMMIT", ctx -> {
            assertEquals(task.getId(), ctx.getTaskId()); assertEquals(outcome, ctx.getApprovalAction());
            assertEquals(outcome, ctx.getVariable("approved")); seen.add("notification");
        });
        Runnable command = () -> transaction.executeWithoutResult(status -> engine.getTaskService().complete(
                task.getId(), Map.of("approved", outcome, "action", outcome)));
        if (reject) assertThrows(IllegalArgumentException.class, command::run);
        else command.run();
        assertEquals(List.of("NODE_COMPLETED"), seen);
        assertEquals(reject ? 0 : 1, count("business_change"));
        if (reject) {
            assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
            assertEquals("previous", engine.getRuntimeService().getVariable(id, "approved"));
            assertNull(engine.getRuntimeService().getVariable(id, "routeReady"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_action_execution WHERE status='PENDING'", Integer.class));
        } else {
            assertEquals("selected", engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult().getTaskDefinitionKey());
            String execution = jdbc.queryForObject("SELECT id FROM process_action_execution WHERE status='PENDING'", String.class);
            process(execution);
            assertEquals(List.of("NODE_COMPLETED", "notification"), seen);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void multiInstanceNodeExitKeepsEachCompletedTaskIdentityAndDoesNotWaitForAllReviewers(boolean sequential, boolean rejectFirst) {
        engine.getRepositoryService().createDeployment().addString("multi-exit.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="multi-exit-test">
                <process id="multiExit" isExecutable="true"><startEvent id="start"/>
                <sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                <userTask id="review" flowable:assignee="${reviewer}">
                  <multiInstanceLoopCharacteristics isSequential="%s" flowable:collection="reviewers" flowable:elementVariable="reviewer"/>
                </userTask>
                <sequenceFlow id="s2" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                </process></definitions>
                """.formatted(sequential)).deploy();
        String id = engine.getRuntimeService().startProcessInstanceByKey("multiExit", Map.of("reviewers", List.of("alice", "bob"))).getId();
        var first = engine.getTaskService().createTaskQuery().processInstanceId(id).taskAssignee("alice").singleResult();
        var seen = new LinkedHashMap<String, String>();
        var shouldReject = new java.util.concurrent.atomic.AtomicBoolean(rejectFirst);
        action(NODE_COMPLETED, "IN_TRANSACTION", ctx -> {
            if (!"review".equals(ctx.getElementId())) return;
            assertNotNull(ctx.getTaskId());
            assertEquals(ctx.getVariable("approved"), ctx.getApprovalAction());
            jdbc.update("INSERT INTO business_change VALUES ('multi-exit')");
            assertNull(seen.put(ctx.getTaskId(), ctx.getTaskAssignee()), "每个实际完成任务只能触发一次");
            if (shouldReject.getAndSet(false)) throw new IllegalArgumentException("会签分支拒绝离开");
        });
        if (rejectFirst) {
            assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status ->
                    engine.getTaskService().complete(first.getId(), Map.of("action", "reject", "approved", "reject"))));
            assertEquals(sequential ? 1L : 2L, engine.getTaskService().createTaskQuery().processInstanceId(id).count());
            assertNull(engine.getRuntimeService().getVariable(id, "approved"));
            assertEquals(0, count("business_change")); seen.clear();
        }
        transaction.executeWithoutResult(status -> engine.getTaskService().complete(first.getId(), Map.of("action", "approve", "approved", "approve")));
        assertEquals(Map.of(first.getId(), "alice"), seen);
        assertEquals(1L, engine.getTaskService().createTaskQuery().processInstanceId(id).count());
        var second = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        transaction.executeWithoutResult(status -> engine.getTaskService().complete(second.getId(), Map.of("action", "reject", "approved", "reject")));
        assertEquals(Map.of(first.getId(), "alice", second.getId(), "bob"), seen);
        assertEquals(2, count("business_change"));
        assertEquals(0L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).count());
    }

    @Test void earlyMultiInstanceCompletionDoesNotInventNodeExitForCancelledBranches() {
        engine.getRepositoryService().createDeployment().addString("early-exit.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="early-exit-test">
                <process id="earlyExit" isExecutable="true"><startEvent id="start"/>
                <sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                <userTask id="review" flowable:assignee="${reviewer}">
                  <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="reviewers" flowable:elementVariable="reviewer">
                    <completionCondition>${nrOfCompletedInstances >= 1}</completionCondition>
                  </multiInstanceLoopCharacteristics>
                </userTask>
                <sequenceFlow id="s2" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                </process></definitions>
                """).deploy();
        String id = engine.getRuntimeService().startProcessInstanceByKey("earlyExit", Map.of("reviewers", List.of("alice", "bob", "carol"))).getId();
        var first = engine.getTaskService().createTaskQuery().processInstanceId(id).taskAssignee("alice").singleResult();
        var seen = new ArrayList<String>();
        action(NODE_COMPLETED, "IN_TRANSACTION", ctx -> seen.add(ctx.getTaskId()));
        transaction.executeWithoutResult(status -> engine.getTaskService().complete(first.getId()));
        assertEquals(List.of(first.getId()), seen);
        assertEquals(0L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).count());
        assertEquals(3L, engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(id).finished().count());
    }

    @Test void skippedUserTaskLeavesTheNodeWithoutInventingAnApprovalOrTaskIdentity() {
        engine.getRepositoryService().createDeployment().addString("skip-exit.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="skip-exit-test">
                <process id="skipExit" isExecutable="true"><startEvent id="start"/>
                <sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                <userTask id="review" flowable:skipExpression="${true}"/>
                <sequenceFlow id="s2" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                </process></definitions>
                """).deploy();
        var seen = new ArrayList<String>();
        action(NODE_COMPLETED, "IN_TRANSACTION", ctx -> { assertNull(ctx.getTaskId()); seen.add(ctx.getTriggerTiming()); });
        for (var timing : List.of(TASK_CREATED, TASK_SUBMITTING, TASK_ADVANCING, TASK_SUBMITTED)) {
            action(timing, "IN_TRANSACTION", ctx -> fail("原生跳过不能伪造审批或创建待办"));
        }
        transaction.executeWithoutResult(status -> engine.getRuntimeService().startProcessInstanceByKey("skipExit",
                Map.of("_FLOWABLE_SKIP_EXPRESSION_ENABLED", true)));
        assertEquals(List.of("NODE_COMPLETED"), seen);
        assertEquals(0L, engine.getTaskService().createTaskQuery().count());
    }

    @Test void localTerminateEndDoesNotEndTheRootProcess() {
        var seen = new ArrayList<String>();
        action(PROCESS_TERMINATED, "IN_TRANSACTION", ctx -> fail("局部终止不能触发根流程终止后"));
        action(PROCESS_COMPLETED, "IN_TRANSACTION", ctx -> seen.add(ctx.getProcessInstanceId()));
        engine.getRepositoryService().createDeployment().addString("local.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="local-test">
                <process id="local" isExecutable="true"><startEvent id="start"/>
                <sequenceFlow id="s1" sourceRef="start" targetRef="sub"/>
                <subProcess id="sub"><startEvent id="subStart"/>
                <sequenceFlow id="s2" sourceRef="subStart" targetRef="subEnd"/>
                <endEvent id="subEnd"><terminateEventDefinition/></endEvent></subProcess>
                <sequenceFlow id="s3" sourceRef="sub" targetRef="review"/><userTask id="review"/>
                <sequenceFlow id="s4" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                </process></definitions>
                """).deploy();
        String id = transaction.execute(status -> engine.getRuntimeService().startProcessInstanceByKey("local").getId());
        assertTrue(seen.isEmpty());
        transaction.executeWithoutResult(status -> engine.getTaskService().complete(
                engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult().getId()));
        assertEquals(List.of(id), seen);
    }

    @Test void independentTransactionCannotConsumeAnOuterPendingEndEvent() {
        String outerId = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        String innerId = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var seen = new ArrayList<String>();
        action(PROCESS_TERMINATED, "IN_TRANSACTION", ctx -> seen.add(ctx.getProcessInstanceId()));
        var independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            engine.getRuntimeService().deleteProcessInstance(outerId, "outer");
            independent.executeWithoutResult(inner -> engine.getRuntimeService().deleteProcessInstance(innerId, "inner"));
            assertEquals(List.of(innerId), seen);
            throw new IllegalStateException("回滚外层");
        }));
        assertEquals(List.of(innerId), seen);
        assertEquals(1L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(outerId).count());
        assertEquals(0L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(innerId).count());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"NORMAL,false", "NORMAL,true", "TERMINATE,false", "TERMINATE,true"})
    void automaticEndRunsOnceAfterSynchronousWritesAndQueuesOnlyAtCommit(String endType, boolean reject) {
        var timing = endType.equals("NORMAL") ? PROCESS_COMPLETED : PROCESS_TERMINATED;
        var seen = new ArrayList<String>();
        action(timing, "IN_TRANSACTION", ctx -> {
            assertEquals(1, count("business_change"));
            assertEquals("final", ctx.getVariable("finalValue"));
            assertEquals(endType.equals("NORMAL") ? "COMPLETE" : "TERMINATE", ctx.getBusinessOperation());
            assertEquals(0L, engine.getRuntimeService().createProcessInstanceQuery()
                    .processInstanceId(ctx.getProcessInstanceId()).count());
            seen.add(ctx.getTriggerTiming());
            if (reject) throw new IllegalArgumentException("结束后处理拒绝");
        });
        action(timing, "AFTER_COMMIT", ctx -> seen.add("notification"));
        var endDefinition = endType.equals("NORMAL") ? "" : "<terminateEventDefinition/>";
        engine.getRepositoryService().createDeployment().addString("automatic.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="auto-test">
                <process id="automatic" isExecutable="true"><startEvent id="start"/>
                <sequenceFlow id="s" sourceRef="start" targetRef="end"/>
                <endEvent id="end">%s</endEvent></process></definitions>
                """.formatted(endDefinition)).deploy();
        Runnable command = () -> transaction.executeWithoutResult(status -> {
            engine.getRuntimeService().startProcessInstanceByKey("automatic", Map.of("finalValue", "final"));
            assertTrue(seen.isEmpty(), "引擎结束回调中不能提前执行公开后置动作");
            assertEquals(0, count("process_action_execution"));
            jdbc.update("INSERT INTO business_change VALUES ('after-engine')");
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM process_action_execution WHERE status='PENDING'", Integer.class));
                            // 已进入 beforeCommit 并写入通知也仍未提交，其他连接不能提前领取。
                            try (var connection = source.getConnection(); var statement = connection.createStatement();
                                 var rows = statement.executeQuery("SELECT COUNT(*) FROM process_action_execution WHERE status='PENDING'")) {
                                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                            } catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
                        }
                    });
        });
        if (reject) assertThrows(IllegalArgumentException.class, command::run);
        else command.run();
        assertEquals(List.of(timing.name()), seen);
        assertEquals(reject ? 0 : 1, count("business_change"));
        assertEquals(reject ? 0 : 1, jdbc.queryForObject("SELECT COUNT(*) FROM process_action_execution WHERE status='PENDING'", Integer.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"WITHDRAWN", "TERMINATED"})
    void systemDeletionUsesTheSameEndTimingAndDuplicateCallbacksDoNotDuplicateActions(String category) {
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var timing = category.equals("WITHDRAWN") ? PROCESS_WITHDRAWN : PROCESS_TERMINATED;
        var seen = new ArrayList<String>();
        action(timing, "IN_TRANSACTION", ctx -> {
            assertEquals(1, count("business_change"));
            assertEquals("system reason", ctx.getEndReason());
            assertEquals("system reason", ctx.getOperationParameters().get("reason"));
            assertEquals("final", ctx.getVariable("finalValue"));
            seen.add(ctx.getTriggerTiming());
        });
        action(PROCESS_COMPLETED, "IN_TRANSACTION", ctx -> fail("撤回或终止不能触发正常完成"));
        transaction.executeWithoutResult(status -> {
            engine.getRuntimeService().setVariable(id, "finalValue", "final");
            engine.getRuntimeService().deleteProcessInstance(id,
                    com.workflow.process.status.application.ProcessEndReason.encode(category, "system reason"));
            var duplicate = operations.process(definitionId, id, null, "system", null, Map.of());
            duplicate.setTriggerTiming(timing.name()); duplicate.setEndReason("system reason");
            operations.processEnded(duplicate);
            var completed = operations.process(definitionId, id, null, "system", null, Map.of());
            completed.setTriggerTiming(PROCESS_COMPLETED.name()); operations.processEnded(completed);
            assertTrue(seen.isEmpty());
            jdbc.update("INSERT INTO business_change VALUES ('system-cleanup')");
        });
        assertEquals(List.of(timing.name()), seen);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void failureAfterDeletionSkipsEndHandlersAndOutboxAndRestoresEngine(boolean rollbackOnly) {
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        action(PROCESS_TERMINATED, "IN_TRANSACTION", ctx -> fail("外层事务已失败，不能调用后置动作"));
        action(PROCESS_TERMINATED, "AFTER_COMMIT", ctx -> fail("回滚不能通知"));
        Runnable command = () -> transaction.executeWithoutResult(status -> {
            engine.getRuntimeService().deleteProcessInstance(id, "system termination");
            if (rollbackOnly) { status.setRollbackOnly(); return; }
            throw new IllegalStateException("清理失败");
        });
        if (rollbackOnly) command.run();
        else assertThrows(IllegalStateException.class, command::run);
        assertEquals(1L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).count());
        assertEquals(0, count("process_action_execution"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PROCESS_WITHDRAW_EXECUTED", "PROCESS_TERMINATE_EXECUTED", "TASK_COMPLETING", "PROCESS_STARTED"})
    void alreadyQueuedOldTimingContinuesUsingItsOriginalSnapshot(String oldTiming) {
        var seen = new ArrayList<String>();
        action(PROCESS_TERMINATED, "AFTER_COMMIT", ctx -> seen.add(ctx.getTriggerTiming()));
        var action = actions.values().iterator().next();
        action.setTriggerTiming(oldTiming);
        var event = operations.process(definitionId, "already-ended", "TERMINATE", "alice", Map.of(), Map.of());
        event.setTriggerTiming(oldTiming); event.setVersionId("published-v1");
        transaction.executeWithoutResult(status -> executions.enqueueActions(List.of(action), event));
        var id = jdbc.queryForObject("SELECT id FROM process_action_execution", String.class);
        // 模拟配置已迁移/重新发布：消费端必须使用原入队快照，而非重新查询当前动作配置。
        action.setTriggerTiming(PROCESS_TERMINATED.name());
        process(id);
        assertEquals(List.of(oldTiming), seen);
        assertEquals("SUCCESS", mapper.selectById(id).getStatus());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void administratorTerminationIncludesIncidentAndSuccessLogInEndBoundary(boolean reject) {
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var incidents = mock(com.workflow.process.assignment.infrastructure.persistence.mapper.AssigneeIncidentMapper.class);
        var row = new org.springframework.util.LinkedCaseInsensitiveMap<Object>();
        row.put("id", "incident"); row.put("process_instance_id", id); row.put("status", "OPEN");
        when(incidents.selectRows(any())).thenAnswer(call -> {
            com.workflow.core.database.mybatis.ParameterizedSql sql = call.getArgument(0);
            return sql.sql().contains("FROM process_assignee_incident WHERE id") ? List.of(row) : List.of();
        });
        when(incidents.execute(any())).thenAnswer(call -> {
            com.workflow.core.database.mybatis.ParameterizedSql sql = call.getArgument(0);
            if (sql.sql().contains("UPDATE process_assignee_incident\n"))
                return jdbc.update("INSERT INTO business_change VALUES ('incident')");
            if (sql.sql().contains("SET status = 'SUCCESS'"))
                return jdbc.update("INSERT INTO business_change VALUES ('success-log')");
            return 1;
        });
        var called = new ArrayList<String>();
        action(PROCESS_TERMINATING, "IN_TRANSACTION", ctx -> fail("系统通道不套用人工终止前置规则"));
        action(PROCESS_TERMINATED, "IN_TRANSACTION", ctx -> {
            assertEquals(2, count("business_change"));
            called.add(ctx.getTriggerTiming());
            if (reject) throw new IllegalArgumentException("拒绝系统后处理");
        });
        var writes = new com.workflow.core.database.jdbc.JdbcWriteAttempt(jdbc,
                com.workflow.integration.database.api.DatabaseDialects.insert(com.workflow.integration.database.api.DatabaseVendor.MYSQL));
        var service = new com.workflow.process.assignment.application.AssigneeIncidentService(incidents, new ObjectMapper(),
                engine.getTaskService(), engine.getRuntimeService(), mock(com.workflow.process.assignment.application.AssigneeResolutionService.class),
                com.workflow.integration.database.api.query.DatabaseQueryDialects.forDatabaseId("MYSQL"), writes, transactions);
        var request = new com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest();
        request.setAction("TERMINATE_INSTANCE"); request.setRequestId("request"); request.setReason("admin recovery");
        if (reject) assertThrows(IllegalArgumentException.class, () -> service.handle("incident", request));
        else service.handle("incident", request);
        assertEquals(List.of("PROCESS_TERMINATED"), called);
        assertEquals(reject ? 0 : 2, count("business_change"));
        assertEquals(reject ? 1L : 0L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).count());
    }

    @Test void beforeStartExecutesWithoutAnInstanceAndPreparationFeedsTheRealEngine() {
        var seen = new ArrayList<String>();
        action(PROCESS_STARTING, "IN_TRANSACTION", ctx -> {
            assertNull(ctx.getProcessInstanceId()); assertEquals("data-1", ctx.getEntityDataId());
            ctx.setProcessVariable("risk", "LOW");
            seen.add(ctx.getTriggerTiming());
        });
        action(PROCESS_STARTING, "IN_TRANSACTION", ctx -> {
            assertNull(ctx.getProcessInstanceId()); assertEquals("LOW", ctx.getVariable("risk"));
            ctx.setProcessVariable("risk", "HIGH"); seen.add(ctx.getTriggerTiming());
        });
        transaction.executeWithoutResult(status -> {
            var event = operations.process(definitionId, null, "START", "alice", Map.of("entityCode", "expense", "entityDataId", "data-1"), Map.of());
            operations.fire(event, PROCESS_STARTING);
            String id = engine.getRuntimeService().startProcessInstanceById(definitionId, event.getVariables()).getId();
            assertEquals("HIGH", engine.getRuntimeService().getVariable(id, "risk"));
        });
        assertEquals(List.of("PROCESS_STARTING", "PROCESS_STARTING"), seen);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM process_action_execution WHERE process_instance_id IS NULL AND status='SUCCESS'", Integer.class));
    }

    @Test void rejectingPreconditionPreventsEngineCreationAndBusinessWrite() {
        action(PROCESS_STARTING, "IN_TRANSACTION", ctx -> { throw new IllegalArgumentException("业务禁止发起"); });
        action(PROCESS_STARTING, "IN_TRANSACTION", ctx -> fail("前一个处理器拒绝后不能继续执行后续动作"));
        assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status -> {
            operations.fire(operations.process(definitionId, null, "START", "alice", Map.of(), Map.of()), PROCESS_STARTING);
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
        action(TASK_FORM_SAVING, "IN_TRANSACTION", ctx -> fail("缺少业务事务时不能调用处理器"));
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        assertThrows(IllegalStateException.class, () -> operations.fire(
                operations.task(task, task.getId(), "SUBMIT", "alice", Map.of(), Map.of()), TASK_FORM_SAVING));
        assertEquals(0, count("process_action_execution"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @Test void runtimeRejectsIllegallyImportedPreconditionBeforeQueueingOrInvoking() {
        action(TASK_FORM_SAVING, "AFTER_COMMIT", ctx -> fail("不能调用处理器"));
        String id = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status ->
                operations.fire(operations.task(task, task.getId(), "SUBMIT", "alice", Map.of(), Map.of()), TASK_FORM_SAVING)));
        assertEquals(0, count("process_action_execution"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PROCESS_TERMINATING", "PROCESS_TERMINATED", "SUCCESS"})
    void terminateStagesSurroundRealDeletionAndLateFailureRollsBack(String failureStage) {
        engine.getIdentityService().setAuthenticatedUserId("alice");
        String instanceId;
        try { instanceId = engine.getRuntimeService().startProcessInstanceById(definitionId).getId(); }
        finally { engine.getIdentityService().setAuthenticatedUserId(null); }
        var seen = new ArrayList<String>();
        for (var timing : List.of(PROCESS_TERMINATING, PROCESS_TERMINATED)) {
            action(timing, "IN_TRANSACTION", ctx -> {
                seen.add(ctx.getTriggerTiming());
                assertEquals("终止原因", ctx.getOperationParameters().get("reason"));
                long count = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count();
                assertEquals(timing == PROCESS_TERMINATED ? 0L : 1L, count);
                assertEquals(timing == PROCESS_TERMINATED ? 1 : 0, count("business_change"));
                if (failureStage.equals(ctx.getTriggerTiming())) throw new IllegalArgumentException("禁止终止");
            });
        }
        var logs = mock(com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper.class);
        when(logs.insert(any(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog.class)))
                .thenAnswer(call -> jdbc.update("INSERT INTO business_change VALUES ('terminate-log')"));
        var service = proxy(new com.workflow.process.instance.application.ProcessTerminationService(operations,
                engine.getRuntimeService(), engine.getHistoryService(), logs,
                mock(com.workflow.process.task.application.ProcessTaskService.class),
                mock(com.workflow.port.system.SysUserLookupPort.class),
                mock(com.workflow.port.entity.EntityProcessStatePort.class),
                mock(com.workflow.process.task.application.operation.NodeOperationCapabilityService.class)));
        if (!failureStage.equals("SUCCESS")) {
            assertThrows(IllegalArgumentException.class, () -> service.terminateProcess(instanceId, "alice", "终止原因"));
        } else {
            assertEquals(failureStage.equals("SUCCESS") ? 200 : 500,
                    service.terminateProcess(instanceId, "alice", "终止原因").getCode());
        }
        boolean success = failureStage.equals("SUCCESS");
        assertEquals(success ? 0L : 1L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count());
        assertEquals(success ? 1 : 0, count("business_change"));
        assertEquals(success ? "PROCESS_TERMINATED" : failureStage, seen.get(seen.size() - 1));
    }

    /** 本地子任务实际 JDBC 副作用与 Spring 事务共用，晚期处理器失败也必须撤销该写入。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"TASK_SUBMITTING", "TASK_SUBMITTED", "SUCCESS"})
    void localAddSignUsesUnifiedTimingsAndDedicatedContextWithRollback(String failureStage) {
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
        for (var timing : List.of(TASK_SUBMITTING, TASK_SUBMITTED)) {
            action(timing, "IN_TRANSACTION", ctx -> {
                seen.add(ctx.getTriggerTiming());
                assertEquals("child", ctx.getTaskId()); assertEquals("LOCAL_ADD_SIGN", ctx.getTaskKind());
                assertEquals(sourceTask.getId(), ctx.getSourceTaskId()); assertEquals("review", ctx.getElementId());
                assertEquals(timing == TASK_SUBMITTED ? 1 : 0, count("business_change"));
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
        assertEquals(failureStage.equals("SUCCESS") ? "TASK_SUBMITTED" : failureStage, seen.get(seen.size() - 1));
    }

    /** 真实加签收口嵌套真实原任务办理；一次提交只产生一对业务动作，后置异常回滚同事务全部写入。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ENGINE:TASK_SUBMITTING", "ENGINE:TASK_SUBMITTED",
            "LOCAL_ADD_SIGN:TASK_SUBMITTING", "LOCAL_ADD_SIGN:TASK_SUBMITTED",
            "ENGINE:TASK_FORM_SAVING", "ENGINE:TASK_ADVANCING", "ENGINE:NODE_COMPLETED", "SUCCESS"})
    void unifiedApprovalStagesDoNotRepeatWhenTheLastChildAdvancesTheSource(String failureStage) {
        String instanceId = engine.getRuntimeService().startProcessInstanceById(definitionId).getId();
        var sourceTask = engine.getTaskService().createTaskQuery().processInstanceId(instanceId).singleResult();
        var sign = new com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSign();
        sign.setId("sign"); sign.setSourceTaskId(sourceTask.getId()); sign.setProcessInstanceId(instanceId);
        sign.setOperationType("AFTER"); sign.setOperatorId("alice"); sign.setSourceCompleted(false);
        var signs = mock(com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper.class);
        when(signs.findOpenBySourceTaskIdForUpdate(sourceTask.getId())).thenReturn(sign);
        when(signs.selectByIdForUpdate("sign")).thenReturn(sign);
        when(signs.updateById(any(com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSign.class)))
                .thenAnswer(call -> jdbc.update("INSERT INTO business_change VALUES ('sign')"));
        var child = new com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSignUser();
        child.setAddSignId("sign"); child.setUserId("bob"); child.setGeneratedTaskId("child"); child.setStatus("TODO");
        var users = mock(com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper.class);
        when(users.findByGeneratedTaskId("child")).thenReturn(child);
        when(users.findByGeneratedTaskIdForUpdate("child")).thenReturn(child);
        when(users.countPending("sign")).thenReturn(0L);
        var tasks = mock(com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper.class);
        var sourceMirror = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
        sourceMirror.setId(1L); sourceMirror.setTaskId(sourceTask.getId()); sourceMirror.setStatus("todo");
        var childMirror = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
        childMirror.setId(2L); childMirror.setTaskId("child"); childMirror.setStatus("todo");
        when(tasks.selectByTaskId(sourceTask.getId())).thenReturn(sourceMirror);
        when(tasks.selectByTaskId("child")).thenReturn(childMirror);
        when(tasks.completeTask(any(), any(), any(), any(), anyLong()))
                .thenAnswer(call -> jdbc.update("INSERT INTO business_change VALUES ('child')"));
        var forms = mock(com.workflow.process.form.application.NodeFormSubmissionService.class);
        doAnswer(call -> {
            ((Runnable) call.getArgument(2)).run();
            jdbc.update("INSERT INTO business_change VALUES ('form')"); return null;
        }).when(forms).applyEditableData(any(), any(), any());
        var projection = mock(com.workflow.process.task.application.ProcessTaskService.class);
        doAnswer(call -> { jdbc.update("INSERT INTO business_change VALUES ('source-complete')"); return null; })
                .when(projection).completeTask(anyString(), anyString(), anyString(), any());
        var core = proxy(new com.workflow.process.task.application.TaskActionService(operations,
                engine.getTaskService(), engine.getRuntimeService(), engine.getHistoryService(), projection,
                engine.getRepositoryService(), mock(com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper.class),
                mock(com.workflow.admin.identity.user.application.SysUserService.class), forms,
                mock(com.workflow.port.entity.EntityProcessStatePort.class), mock(com.workflow.process.cc.application.ProcessCcService.class),
                mock(com.workflow.process.task.application.nextapproval.NextApproverOverrideService.class),
                new com.workflow.process.task.application.MultiInstanceOutcomeService(engine.getRuntimeService(), engine.getRepositoryService(), engine.getTaskService(), new ObjectMapper()),
                mock(com.workflow.process.task.application.operation.NodeOperationCapabilityService.class),
                mock(com.workflow.process.task.application.TaskIdentityAccessService.class),
                mock(com.workflow.process.instance.application.ProcessEntityVariableService.class)));
        var service = proxy(new com.workflow.process.task.application.TaskAddSignService(operations, engine.getTaskService(), tasks,
                signs, users, mock(com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper.class),
                mock(com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper.class), new ObjectMapper(),
                mock(com.workflow.process.task.application.operation.NodeOperationCapabilityService.class), core,
                mock(com.workflow.process.task.application.TaskIdentityAccessService.class)));
        var seen = new ArrayList<String>();
        for (var timing : List.of(TASK_SUBMITTING, TASK_SUBMITTED, TASK_FORM_SAVING, TASK_ADVANCING)) {
            action(timing, "IN_TRANSACTION", ctx -> {
                String stage = ctx.getTaskKind() + ":" + ctx.getTriggerTiming();
                seen.add(stage); assertEquals("SUBMIT", ctx.getBusinessOperation());
                if (ctx.getTaskKind().equals("LOCAL_ADD_SIGN")) {
                    assertEquals("child", ctx.getTaskId()); assertEquals(sourceTask.getId(), ctx.getSourceTaskId());
                    assertEquals("bob-id", ctx.getOperatorId()); assertEquals("AFTER", ctx.getOperationParameters().get("type"));
                } else {
                    assertEquals(sourceTask.getId(), ctx.getTaskId()); assertTrue(List.of("alice-id", "alice").contains(ctx.getOperatorId()));
                    assertEquals(Map.of("amount", 12), ctx.getSubmittedFormData());
                }
                if (stage.equals(failureStage)) throw new IllegalArgumentException("业务拒绝 " + stage);
            });
        }
        action(NODE_COMPLETED, "IN_TRANSACTION", ctx -> {
            // 最后子任务的提交可推动原引擎任务离开，节点动作只属于原任务，不能借用本地子任务 ID。
            assertEquals(sourceTask.getId(), ctx.getTaskId()); assertEquals("approve", ctx.getVariable("approved"));
            String stage = "ENGINE:NODE_COMPLETED"; seen.add(stage);
            if (stage.equals(failureStage)) throw new IllegalArgumentException("业务拒绝 " + stage);
        });
        boolean sourceRejected = failureStage.equals("ENGINE:TASK_SUBMITTING") || failureStage.equals("ENGINE:TASK_SUBMITTED");
        com.workflow.admin.security.context.UserContext.setCurrentUser("alice-id", "alice");
        try {
            if (sourceRejected) {
                assertThrows(IllegalArgumentException.class, () -> service.handleSourceCompletion(sourceTask.getId(), "alice", "approve", "原意见", "通过", Map.of("amount", 12)));
                assertEquals(0, count("business_change"));
            } else {
                assertTrue(service.handleSourceCompletion(sourceTask.getId(), "alice", "approve", "原意见", "通过", Map.of("amount", 12)));
                assertEquals(List.of("ENGINE:TASK_SUBMITTING", "ENGINE:TASK_SUBMITTED"), seen);
                assertEquals(1, count("business_change"));
                com.workflow.admin.security.context.UserContext.setCurrentUser("bob-id", "bob");
                if (failureStage.equals("SUCCESS")) service.completeAddSignTask("child", "approve", "加签意见");
                else assertThrows(IllegalArgumentException.class, () -> service.completeAddSignTask("child", "approve", "加签意见"));
            }
        } finally { com.workflow.admin.security.context.UserContext.clear(); }
        if (failureStage.equals("SUCCESS")) {
            assertEquals(List.of("ENGINE:TASK_SUBMITTING", "ENGINE:TASK_SUBMITTED", "LOCAL_ADD_SIGN:TASK_SUBMITTING",
                    "ENGINE:TASK_FORM_SAVING", "ENGINE:TASK_ADVANCING", "ENGINE:NODE_COMPLETED", "LOCAL_ADD_SIGN:TASK_SUBMITTED"), seen);
            assertEquals(5, count("business_change"));
            assertEquals(0L, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count());
        } else {
            assertEquals(failureStage, seen.get(seen.size() - 1));
            assertEquals(sourceRejected ? 0 : 1, count("business_change"));
            assertNotNull(engine.getTaskService().createTaskQuery().taskId(sourceTask.getId()).singleResult());
        }
    }

    private void action(FlowActionTriggerTiming timing, String mode, Consumer<FlowActionContext> handler) {
        // 同一时机允许多个动作，配置 ID 必须独立，才能验证统一排序和异常短路。
        String id = timing.name() + "-" + actions.size();
        var action = new FlowAction(); action.setId(id); action.setActionName(id); action.setInterfaceName(id);
        action.setTriggerTiming(timing.name()); action.setScopeType(timing.getScopeTypes().iterator().next().name());
        if ("NODE".equals(action.getScopeType())) action.setElementId("review");
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
