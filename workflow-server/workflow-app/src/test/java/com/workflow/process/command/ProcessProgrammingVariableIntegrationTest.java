package com.workflow.process.command;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.model.CommandMeta;
import com.workflow.contracts.process.model.ProcessPageRequest;
import com.workflow.contracts.process.model.ProcessSort;
import com.workflow.contracts.process.model.ProcessEndType;
import com.workflow.contracts.process.model.ProcessLifecycle;
import com.workflow.contracts.process.query.model.*;
import com.workflow.contracts.process.variable.model.*;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.process.command.application.*;
import com.workflow.process.command.infrastructure.persistence.mapper.ProcessCommandRecordMapper;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.query.application.*;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskOperationApplicationService;
import com.workflow.process.task.application.TaskApprovalQueryService;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSign;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.status.application.ProcessEndReason;
import com.workflow.process.task.application.LocalAddSignTaskAccessService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSignUser;
import com.workflow.process.variable.application.ProcessVariableAdapter;
import com.workflow.process.variable.application.ProcessVariableReader;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实引擎和数据库证明变量、幂等回执共用事务。授权组件在此隔离，
 * 不替代单独的身份测试；测试不以 Mockito 调用次数冒充引擎状态或回滚断言。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessProgrammingVariableIntegrationTest {
    private ProcessEngine engine;
    private boolean mysql;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ProcessCommandRecordMapper receipts;
    private CurrentActorPort actors;
    private ProcessInstanceAccessService instanceAccess;
    private ProcessTaskAddSignUserMapper addSignUsers;
    private ProcessInvocationAccess access;
    private ProcessVariableAdapter variables;
    private ProcessCommandExecutor commands;
    private String instanceId;
    private ProcessQueryAdapter queries;
    private ProcessTaskService mirrors;
    private ProcessTaskAddSignMapper addSigns;
    private com.workflow.process.action.application.FlowActionDispatcher businessActions;
    private com.workflow.process.action.application.FlowActionOperationDispatcher operationActions;
    private com.workflow.process.action.infrastructure.flowable.FlowActionEngineEventListener endListener;
    private com.workflow.process.form.application.NodeFormSubmissionService submittedForms;
    private com.workflow.process.task.application.nextapproval.NextApproverOverrideService nextApprovers;
    private com.workflow.process.task.application.TaskActionService businessCore;

    @BeforeAll
    void database() throws Exception {
        String mysqlUrl = System.getProperty("process.programming.test.mysqlUrl");
        mysql = mysqlUrl != null;
        if (mysql && !mysqlUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/process_api_test_[a-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("真实数据库测试仅允许显式指定本机 process_api_test_ 隔离库");
        }
        // MySQL Connector/J 默认可能跨 catalog 查元数据；隔离库不能误读另一个测试库的引擎表。
        if (mysql) mysqlUrl += (mysqlUrl.contains("?") ? "&" : "?") + "nullCatalogMeansCurrent=true";
        var source = new DriverManagerDataSource(mysql ? mysqlUrl : "jdbc:h2:mem:process_programming_" + UUID.randomUUID()
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", mysql ? "root" : "sa", "");
        if (mysql) assertEquals(0, new JdbcTemplate(source).queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class),
                "只允许全新的隔离测试库，不能覆盖现有表");
        transactions = new DataSourceTransactionManager(source);
        var configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(source);
        configuration.setTransactionManager(transactions);
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        engine = configuration.buildProcessEngine();
        jdbc = new JdbcTemplate(source);
        if (mysql) {
            try (var connection = source.getConnection()) {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                        new org.springframework.core.io.ClassPathResource("db/migration/V122__process_command_receipt.sql"));
            }
        } else {
        jdbc.execute("SET MODE MySQL");
        // H2 仅用于行为及事务测试；生产迁移的 MySQL 排序规则由迁移契约测试覆盖。
        jdbc.execute("""
                CREATE TABLE process_command_receipt (
                  id VARCHAR(64) PRIMARY KEY,request_id VARCHAR(200) NOT NULL,invocation_scope VARCHAR(512) NOT NULL,
                  command_type VARCHAR(64) NOT NULL,command_hash VARCHAR(64) NOT NULL,status VARCHAR(16) NOT NULL,
                  process_instance_id VARCHAR(128),task_id VARCHAR(128),changed BOOLEAN NOT NULL,
                  result_document CLOB,create_time TIMESTAMP NOT NULL,update_time TIMESTAMP NOT NULL)
                """);
        }
        var mybatis = new MybatisConfiguration();
        // H2 没有 FOR SHARE；使用现有 Oracle 当前读分支验证回执竞争和事务，
        // MySQL/PostgreSQL 共享锁行为仍须在对应数据库测试中验证，不能由本测试代替。
        mybatis.setDatabaseId(mysql ? "MYSQL" : "ORACLE");
        mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.addMapper(ProcessCommandRecordMapper.class);
        // 投影字段使用 MyBatis-Plus Lambda 元数据，模拟版本仓储也需要注册真实实体映射。
        mybatis.addMapper(ProcessVersionHistoryMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(mybatis);
        receipts = new SqlSessionTemplate(factory.getObject()).getMapper(ProcessCommandRecordMapper.class);
        engine.getRepositoryService().createDeployment().addString("programming.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="programming-test">
                  <process id="programming" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="split"/>
                    <parallelGateway id="split"/>
                    <sequenceFlow id="s2" sourceRef="split" targetRef="reviewA"/>
                    <sequenceFlow id="s3" sourceRef="split" targetRef="reviewB"/>
                    <userTask id="reviewA" flowable:assignee="alice"/>
                    <userTask id="reviewB" flowable:assignee="alice"/>
                    <sequenceFlow id="s4" sourceRef="reviewA" targetRef="join"/>
                    <sequenceFlow id="s5" sourceRef="reviewB" targetRef="join"/>
                    <parallelGateway id="join"/><sequenceFlow id="s6" sourceRef="join" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """).deploy();
    }

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM process_command_receipt");
        jdbc.execute("CREATE TABLE IF NOT EXISTS operation_timing_form (form_value VARCHAR(32))");
        jdbc.update("DELETE FROM operation_timing_form");
        businessActions = mock(com.workflow.process.action.application.FlowActionDispatcher.class);
        operationActions = new com.workflow.process.action.application.FlowActionOperationDispatcher(businessActions, engine.getRuntimeService(), engine.getHistoryService());
        submittedForms = mock(com.workflow.process.form.application.NodeFormSubmissionService.class);
        doAnswer(call -> { Runnable callback = call.getArgument(2); if (callback != null) callback.run(); return null; })
                .when(submittedForms).applyEditableData(any(), any(), any());
        nextApprovers = mock(com.workflow.process.task.application.nextapproval.NextApproverOverrideService.class);
        actors = mock(CurrentActorPort.class);
        when(actors.current()).thenReturn(new CurrentActor("alice-id", "alice"));
        instanceAccess = mock(ProcessInstanceAccessService.class);
        var taskAccess = mock(TaskIdentityAccessService.class);
        when(taskAccess.canCurrentUserAccess(any())).thenReturn(true);
        addSignUsers = mock(ProcessTaskAddSignUserMapper.class);
        access = new ProcessInvocationAccess(actors, instanceAccess, taskAccess);
        commands = newExecutor();
        variables = transactional(new ProcessVariableAdapter(new ProcessVariableReader(engine.getRuntimeService(),
                engine.getTaskService(), engine.getHistoryService(), addSignUsers), access, commands,
                engine.getRuntimeService(), engine.getTaskService(), taskAccess, mock(LocalAddSignTaskAccessService.class)));
        instanceId = engine.getRuntimeService().startProcessInstanceByKey("programming", Map.of("risk", "LOW")).getId();
        mirrors = mock(ProcessTaskService.class);
        addSigns = mock(ProcessTaskAddSignMapper.class);
        var links = mock(EntityProcessLinkMapper.class);
        var time = new ProcessQueryTime("Asia/Shanghai");
        var projection = new ProcessProjectionReader(engine.getRuntimeService(), engine.getHistoryService(),
                engine.getRepositoryService(), links, mock(ProcessVersionHistoryMapper.class), taskAccess, time);
        queries = new ProcessQueryAdapter(engine.getHistoryService(), engine.getRuntimeService(), engine.getTaskService(),
                access, instanceAccess, projection, time, mirrors, addSignUsers, addSigns,
                mock(LocalAddSignTaskAccessService.class), links, mock(ProcessOperationLogMapper.class), receipts,
                mock(TaskOperationApplicationService.class), mock(TaskApprovalQueryService.class), mock(NodeOperationDecisionService.class));
    }

    @AfterEach
    void deleteInstances() {
        if (endListener != null) engine.getRuntimeService().removeEventListener(endListener);
        endListener = null;
        com.workflow.admin.security.context.UserContext.clear();
        if (!mysql || jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fail_receipt'", Integer.class) > 0) {
            jdbc.execute("ALTER TABLE process_command_receipt DROP CONSTRAINT " + (mysql ? "" : "IF EXISTS ") + "fail_receipt");
        }
        engine.getRuntimeService().createProcessInstanceQuery().list().forEach(instance ->
                engine.getRuntimeService().deleteProcessInstance(instance.getId(), "test cleanup"));
    }

    @AfterAll
    void close() {
        if (engine != null) engine.close();
        if (jdbc != null && !mysql) jdbc.execute("SHUTDOWN");
    }

    @Test
    void rootAndTaskHistoryNeverOverwriteEachOthersVariables() {
        Task task = tasks().get(0);
        engine.getRuntimeService().setVariableLocal(task.getExecutionId(), "risk", "BRANCH");
        engine.getTaskService().setVariableLocal(task.getId(), "risk", "TASK");
        assertEquals("LOW", variables.getProcessVariable(instanceId, "risk", VariableReadMode.RUNTIME_ONLY).value());
        assertEquals("TASK", variables.getTaskLocalVariable(task.getId(), "risk", VariableReadMode.RUNTIME_ONLY).value());
        assertEquals("LOW", variables.getProcessVariable(instanceId, "risk", VariableReadMode.HISTORY_ONLY).value());
        tasks().forEach(item -> engine.getTaskService().complete(item.getId()));
        assertEquals("LOW", variables.getProcessVariable(instanceId, "risk", VariableReadMode.RUNTIME_OR_HISTORY).value());
        assertEquals("TASK", variables.getTaskLocalVariable(task.getId(), "risk", VariableReadMode.RUNTIME_OR_HISTORY).value());
        assertCode("PROCESS_ALREADY_ENDED", () -> variables.getProcessVariables(instanceId, VariableReadMode.RUNTIME_ONLY));
        assertCode("TASK_ALREADY_COMPLETED", () -> variables.getTaskLocalVariables(task.getId(), VariableReadMode.RUNTIME_ONLY));
    }

    @Test
    void nullRemovalAndNestedSnapshotsHaveIndependentSemantics() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("nullable", null);
        input.put("items", new ArrayList<>(List.of(Map.of("name", "original"))));
        variables.patchProcessVariables(new ProcessVariablePatch(new CommandMeta("null-and-delete"), instanceId, input, Set.of("risk")));
        assertTrue(variables.getProcessVariable(instanceId, "nullable", VariableReadMode.RUNTIME_ONLY).present());
        assertNull(variables.getProcessVariable(instanceId, "nullable", VariableReadMode.RUNTIME_ONLY).value());
        assertFalse(variables.getProcessVariable(instanceId, "risk", VariableReadMode.RUNTIME_ONLY).present());
        Map<String, Object> snapshot = variables.getProcessVariables(instanceId, VariableReadMode.RUNTIME_ONLY);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("new", "value"));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) snapshot.get("items")).clear());
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) ((List<?>) snapshot.get("items")).get(0)).clear());
    }

    @Test
    void taskPatchOnlyTouchesTaskLocalScope() {
        Task task = tasks().get(0);
        variables.patchTaskLocalVariables(new TaskVariablePatch(new CommandMeta("task-local"), task.getId(), Map.of("risk", "HIGH"), Set.of()));
        assertEquals("HIGH", engine.getTaskService().getVariableLocal(task.getId(), "risk"));
        assertEquals("LOW", engine.getRuntimeService().getVariableLocal(instanceId, "risk"));
        assertNull(engine.getTaskService().getVariableLocal(tasks().get(1).getId(), "risk"));
    }

    @Test
    void persistedReplaySurvivesNewExecutorAndRejectsDifferentPayload() {
        var patch = patch("replay", "HIGH");
        var first = variables.patchProcessVariables(patch);
        // 另建执行器模拟无 JVM 缓存的重试；即使流程已结束也应重放首次结果而非再次写变量。
        tasks().forEach(task -> engine.getTaskService().complete(task.getId()));
        var replay = newExecutor().execute(patch.meta(), "PROCESS_VARIABLE_PATCH", patch, Boolean.class,
                () -> { throw new AssertionError("回执重放不能再次执行业务"); });
        assertTrue(replay.receipt().replayed());
        assertEquals(first.operationId(), replay.receipt().operationId());
        assertCode("IDEMPOTENCY_CONFLICT", () -> variables.patchProcessVariables(patch("replay", "DIFFERENT")));
        assertEquals(1, receiptCount());
    }

    @Test
    void outerRollbackRevertsEngineAndReceiptAndAllowsRetry() {
        var patch = patch("outer-rollback", "HIGH");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            variables.patchProcessVariables(patch);
            assertEquals("HIGH", engine.getRuntimeService().getVariableLocal(instanceId, "risk"));
            assertEquals(1, receiptCount());
            status.setRollbackOnly();
        });
        assertEquals("LOW", engine.getRuntimeService().getVariableLocal(instanceId, "risk"));
        assertEquals(0, receiptCount());
        assertFalse(variables.patchProcessVariables(patch).replayed());
    }

    @Test
    void receiptFailureAfterEngineWriteRollsBackBothAndDoesNotPoisonRetry() {
        jdbc.execute("ALTER TABLE process_command_receipt ADD CONSTRAINT fail_receipt CHECK (status <> 'SUCCESS')");
        assertThrows(RuntimeException.class, () -> variables.patchProcessVariables(patch("receipt-failure", "HIGH")));
        assertEquals("LOW", engine.getRuntimeService().getVariableLocal(instanceId, "risk"));
        assertEquals(0, receiptCount());
        jdbc.execute("ALTER TABLE process_command_receipt DROP CONSTRAINT fail_receipt");
        assertFalse(variables.patchProcessVariables(patch("receipt-failure", "HIGH")).replayed());
    }

    @Test
    void concurrentIdenticalCommandsExecuteOnlyOnce() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempting = new CountDownLatch(1);
        var count = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        var patch = patch("concurrent", "HIGH");
        try {
            var first = pool.submit(() -> commands.execute(patch.meta(), "TEST_CONCURRENT", patch, Boolean.class, () -> {
                count.incrementAndGet();
                entered.countDown();
                await(release);
                engine.getRuntimeService().setVariableLocal(instanceId, "risk", "HIGH");
                return new ProcessCommandExecutor.Outcome<>(instanceId, null, true, true);
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> {
                attempting.countDown();
                return newExecutor().execute(patch.meta(), "TEST_CONCURRENT", patch, Boolean.class,
                        () -> { throw new AssertionError("并发相同请求不能二次推进"); });
            });
            assertTrue(attempting.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertFalse(first.get(10, TimeUnit.SECONDS).receipt().replayed());
            assertTrue(second.get(10, TimeUnit.SECONDS).receipt().replayed());
            assertEquals(1, count.get());
            assertEquals(1, receiptCount());
            assertEquals("HIGH", engine.getRuntimeService().getVariableLocal(instanceId, "risk"));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void reservedFieldsAreHiddenAndWholePatchRejectedBeforeAnyWrite() {
        engine.getRuntimeService().setVariableLocal(instanceId, "initiator", "alice");
        engine.getRuntimeService().setVariableLocal(instanceId, "approved", true);
        assertEquals(Map.of("risk", "LOW"), variables.getProcessVariables(instanceId, VariableReadMode.RUNTIME_ONLY));
        assertCode("VARIABLE_NOT_WRITABLE", () -> variables.patchProcessVariables(new ProcessVariablePatch(
                new CommandMeta("reserved"), instanceId, Map.of("risk", "HIGH", "approved", false), Set.of())));
        assertEquals("LOW", engine.getRuntimeService().getVariableLocal(instanceId, "risk"));
        assertEquals(0, receiptCount());
    }

    @Test
    void missingActorFailsAndActionScopeIsBoundToInstanceAndRestored() {
        when(actors.current()).thenReturn(null);
        assertCode("PROCESS_ACTOR_REQUIRED", () -> variables.patchProcessVariables(patch("no-actor", "HIGH")));
        try (var scope = ProcessInvocationScope.openAction(instanceId, "action-a", "execution-a")) {
            assertEquals("LOW", variables.getProcessVariable(instanceId, "risk", VariableReadMode.RUNTIME_ONLY).value());
            variables.patchProcessVariables(patch("action-write", "HIGH"));
            assertCode("PROCESS_ACTOR_REQUIRED", access::requireActor);
            assertCode("PROCESS_ACTOR_REQUIRED", () -> access.requireRead("other-instance"));
            try (var nested = ProcessInvocationScope.openAction("other-instance", "action-b", "execution-b")) {
                assertFalse(access.isActionFor(instanceId));
                assertTrue(access.isActionFor("other-instance"));
            }
            assertTrue(access.isActionFor(instanceId));
        }
        assertCode("PROCESS_ACTOR_REQUIRED", () -> access.requireRead(instanceId));
        verifyNoInteractions(instanceAccess);
    }

    @Test
    void localAddSignTaskNeverFallsBackToSourceTaskVariableScope() {
        when(addSignUsers.findByGeneratedTaskId("local-sign")).thenReturn(new ProcessTaskAddSignUser());
        assertCode("VARIABLE_SCOPE_UNSUPPORTED", () -> variables.getTaskLocalVariables("local-sign", VariableReadMode.RUNTIME_OR_HISTORY));
        assertCode("VARIABLE_SCOPE_UNSUPPORTED", () -> variables.patchTaskLocalVariables(new TaskVariablePatch(
                new CommandMeta("local-sign-write"), "local-sign", Map.of("risk", "HIGH"), Set.of())));
        assertEquals(0, receiptCount());
    }

    @Test
    void activeQueryIncludesEveryParallelBranchAndOnlyValidLocalAddSign() {
        Task source = tasks().get(0);
        var local = mirror(91L, "local-active", "ADD_SIGN", "todo");
        var held = mirror(92L, "local-held", "ADD_SIGN", "hold");
        when(mirrors.getTasksByProcessInstance(instanceId)).thenReturn(List.of(local, held));
        when(mirrors.getTaskByTaskId("local-active")).thenReturn(local);
        var child = new ProcessTaskAddSignUser();
        child.setGeneratedTaskId(local.getTaskId()); child.setAddSignId("add-sign"); child.setStatus("TODO");
        when(addSignUsers.findByGeneratedTaskId("local-active")).thenReturn(child);
        var parent = new ProcessTaskAddSign();
        parent.setId("add-sign"); parent.setSourceTaskId(source.getId()); parent.setProcessInstanceId(instanceId); parent.setStatus("ACTIVE");
        when(addSigns.selectById("add-sign")).thenReturn(parent);
        var active = queries.listActiveTasks(instanceId);
        assertEquals(3, active.size());
        assertEquals(Set.of("reviewA", "reviewB"), active.stream().filter(task -> "ENGINE".equals(task.kind()))
                .map(ProcessTaskView::nodeId).collect(java.util.stream.Collectors.toSet()));
        var localView = queries.findActiveTask("local-active").orElseThrow();
        assertNull(localView.engineTaskId());
        assertFalse(localView.canClaim());
        parent.setStatus("CANCELED");
        assertTrue(queries.findActiveTask("local-active").isEmpty());
        assertEquals(2, queries.listActiveTasks(instanceId).size());
    }

    @Test
    void historyKeepsIndependentMirrorRowsForSameTaskAndSupplementsLegacyEngineTasks() {
        Task task = tasks().get(0);
        var original = mirror(101L, task.getId(), "USER_TASK", "transfer");
        original.setAssigneeId("alice"); original.setAction("transfer");
        var next = mirror(102L, task.getId(), "USER_TASK", "done");
        next.setAssigneeId("bob"); next.setAction("approve");
        when(mirrors.getTasksByProcessInstance(instanceId)).thenReturn(List.of(original, next));
        var result = queries.pageTaskHistory(new ProcessHistoryQuery(instanceId, null, null, null, null, null));
        assertEquals(3, result.total());
        assertEquals(2, result.items().stream().filter(item -> task.getId().equals(item.taskId())).count());
        assertTrue(result.items().stream().map(ProcessTaskHistoryView::historyId).toList().containsAll(List.of("mirror:101", "mirror:102")));
    }

    @Test
    void readableInstanceTotalIsComputedAfterAuthorizationAndPagesAreStable() {
        String other = engine.getRuntimeService().startProcessInstanceByKey("programming").getId();
        engine.getRuntimeService().startProcessInstanceByKey("programming");
        when(instanceAccess.canRead(any())).thenAnswer(call -> {
            String id = call.getArgument(0, org.flowable.engine.history.HistoricProcessInstance.class).getId();
            return Set.of(instanceId, other).contains(id);
        });
        var first = queries.pageInstances(instancePage(1));
        var second = queries.pageInstances(instancePage(2));
        assertEquals(2, first.total()); assertEquals(2, second.total());
        assertEquals(1, first.items().size()); assertEquals(1, second.items().size());
        assertNotEquals(first.items().get(0).instanceId(), second.items().get(0).instanceId());
        assertEquals(first, queries.pageInstances(instancePage(1)));
        assertTrue(queries.pageInstances(instancePage(3)).items().isEmpty());
    }

    @Test
    void queryDistinguishesMissingDeniedAndTechnicalFailure() {
        assertTrue(queries.findInstance("missing").isEmpty());
        assertCode("PROCESS_NOT_FOUND", () -> queries.requireInstance("missing"));
        doThrow(new ForbiddenException("无权读取")).when(instanceAccess).requireReadAccess(instanceId);
        assertCode("PROCESS_ACCESS_DENIED", () -> queries.findInstance(instanceId));
        doThrow(new IllegalStateException("database unavailable")).when(instanceAccess).requireReadAccess(instanceId);
        var failure = assertThrows(IllegalStateException.class, () -> queries.findInstance(instanceId));
        assertEquals("database unavailable", failure.getMessage());
    }

    @Test
    void endedAndSuspendedLifecyclesUsePersistedTypeInsteadOfCommentKeywords() {
        engine.getRuntimeService().suspendProcessInstanceById(instanceId);
        assertEquals(ProcessLifecycle.SUSPENDED, queries.requireInstance(instanceId).lifecycle());
        engine.getRuntimeService().activateProcessInstanceById(instanceId);
        engine.getRuntimeService().deleteProcessInstance(instanceId, ProcessEndReason.encode("TERMINATED", "撤回申请无效"));
        var ended = queries.requireInstance(instanceId);
        assertEquals(ProcessLifecycle.ENDED, ended.lifecycle());
        assertEquals(ProcessEndType.TERMINATED, ended.endType());
        assertTrue(queries.listActiveActivities(instanceId).isEmpty());
        assertTrue(queries.listActiveTasks(instanceId).isEmpty());
    }

    @Test
    void commandOperationHistoryExposesDurableRequestAndOperationIds() {
        var receipt = variables.patchProcessVariables(patch("audit-variable", "HIGH"));
        var result = queries.pageOperations(new ProcessHistoryQuery(instanceId, null, null, null, null, null));
        assertEquals(1, result.total());
        assertEquals("audit-variable", result.items().get(0).requestId());
        assertEquals(receipt.operationId(), result.items().get(0).operationId());
        assertEquals("alice-id", result.items().get(0).operatorId());
        variables.patchProcessVariables(patch("audit-variable", "HIGH"));
        assertEquals(1, queries.pageOperations(new ProcessHistoryQuery(instanceId, null, null, null, null, null)).total());
    }

    @Test
    void actionExecutorBindsScopeAndConvenienceCallsSharePortsAndReplay() {
        when(actors.current()).thenReturn(null);
        var adapter = new com.workflow.process.action.infrastructure.flowable.FlowActionRuntimeAdapter(queries, variables,
                new ProcessVariableReader(engine.getRuntimeService(), engine.getTaskService(), engine.getHistoryService(), addSignUsers),
                mock(com.workflow.contracts.entity.port.EntityRecordQueryPort.class), new ObjectMapper());
        var beans = mock(org.springframework.context.ApplicationContext.class);
        when(beans.getBean("testAction")).thenReturn((com.workflow.contracts.process.action.spi.FlowActionProvider) context -> {
            assertEquals("LOW", context.getVariable("risk"), "重试仍使用触发快照，不应漂移为实时值");
            assertEquals(2, context.getActiveTasks().size());
            assertEquals(instanceId, context.getProcessInstance().instanceId());
            context.setProcessVariable("risk", "HIGH");
            assertEquals("HIGH", context.getLiveVariables().get("risk"));
            assertEquals("HIGH", context.getVariable("risk"), "成功写入应更新本次动作的局部快照");
        });
        var executor = new com.workflow.process.action.application.FlowActionExecutor(beans, adapter,
                mock(com.workflow.process.action.application.FlowActionExecutionService.class), new ObjectMapper());
        var action = new com.workflow.process.action.infrastructure.persistence.record.FlowAction();
        action.setId("test-action"); action.setInterfaceName("testAction");
        var event = new com.workflow.process.action.domain.FlowActionTriggerEvent();
        event.setProcessInstanceId(instanceId); event.setVariables(Map.of("risk", "LOW"));
        executor.executeAction(action, event, "same-execution");
        executor.executeAction(action, event, "same-execution");
        assertEquals(1, receiptCount());
        assertEquals("HIGH", engine.getRuntimeService().getVariableLocal(instanceId, "risk"));
        assertCode("PROCESS_ACTOR_REQUIRED", () -> access.requireRead(instanceId));
        assertCode("PROCESS_ACCESS_DENIED", () -> adapter.setVariable(instanceId, "risk", "OUTSIDE"));
    }

    @Test
    void receiveCommandReplaysConsumedExecutionAndWritesRootVariablesAtomically() {
        String receive = startReceive();
        var command = new com.workflow.contracts.process.instance.model.TriggerReceiveTaskCommand(new CommandMeta("receive"),
                receive, null, "wait", null, Map.of("callback", "DONE"));
        var port = instanceCommands();
        var first = port.triggerReceiveTask(command);
        assertNotNull(first.executionId());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(receive).singleResult());
        assertEquals("DONE", variables.getProcessVariable(receive, "callback", VariableReadMode.HISTORY_ONLY).value());
        var replay = port.triggerReceiveTask(command);
        assertTrue(replay.receipt().replayed());
        assertEquals(first.executionId(), replay.executionId());
        assertEquals(first.receipt().operationId(), replay.receipt().operationId());
        verify(instanceAccess).requireSignalAccess(receive);
    }

    @Test
    void receiptFailureRollsBackReceiveAdvanceAndVariablesTogether() {
        String receive = startReceive();
        String execution = engine.getRuntimeService().createExecutionQuery().processInstanceId(receive).activityId("wait").singleResult().getId();
        var command = new com.workflow.contracts.process.instance.model.TriggerReceiveTaskCommand(new CommandMeta("receive-fail"),
                receive, execution, "wait", null, Map.of("callback", "DONE"));
        jdbc.execute("ALTER TABLE process_command_receipt ADD CONSTRAINT fail_receipt CHECK (status <> 'SUCCESS')");
        assertThrows(RuntimeException.class, () -> instanceCommands().triggerReceiveTask(command));
        assertNotNull(engine.getRuntimeService().createExecutionQuery().executionId(execution).singleResult());
        assertFalse(engine.getRuntimeService().hasVariableLocal(receive, "callback"));
        assertEquals(0, receiptCount());
    }

    @Test
    void receiveCoordinatesMustAgreeBeforeAnyVariableWrite() {
        String receive = startReceive();
        String execution = engine.getRuntimeService().createExecutionQuery().processInstanceId(receive).activityId("wait").singleResult().getId();
        var command = new com.workflow.contracts.process.instance.model.TriggerReceiveTaskCommand(new CommandMeta("wrong-node"),
                receive, execution, "other-node", null, Map.of("callback", "DONE"));
        assertCode("PROCESS_INVALID_ARGUMENT", () -> instanceCommands().triggerReceiveTask(command));
        assertFalse(engine.getRuntimeService().hasVariableLocal(receive, "callback"));
        assertNotNull(engine.getRuntimeService().createExecutionQuery().executionId(execution).singleResult());
        assertEquals(0, receiptCount());
    }

    /** 测试调用真实实例服务；目录、表单详情等未参与本场景的依赖才予以隔离。 */
    private com.workflow.process.instance.application.ProcessInstanceCommandAdapter instanceCommands() {
        var instances = transactional(new com.workflow.process.instance.application.ProcessInstanceService(
                engine.getRuntimeService(), engine.getHistoryService(), engine.getRepositoryService(),
                mock(com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper.class),
                mock(com.workflow.admin.identity.user.application.SysUserService.class),
                mock(com.workflow.entity.data.application.EntityDataDynamicService.class),
                mock(com.workflow.process.instance.application.ProcessProgressRuntimeService.class), instanceAccess,
                mock(com.workflow.process.instance.application.ProcessDetailRuntimeService.class),
                mock(com.workflow.process.instance.application.ProcessTerminationService.class),
                mock(com.workflow.process.task.application.operation.NodeOperationCapabilityService.class),
                mock(com.workflow.entity.definition.application.EntityStatusService.class),
                mock(com.workflow.process.instance.application.PublishedBpmnReader.class)));
        return new com.workflow.process.instance.application.ProcessInstanceCommandAdapter(commands, access,
                mock(com.workflow.process.task.application.TaskActionService.class),
                mock(com.workflow.process.instance.application.ProcessTerminationService.class), instances,
                engine.getRuntimeService(), engine.getHistoryService());
    }

    private String startReceive() {
        engine.getRepositoryService().createDeployment().addString("receive.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="programming-test">
                  <process id="programmingReceive" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="wait"/>
                    <receiveTask id="wait"/><sequenceFlow id="s2" sourceRef="wait" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """).deploy();
        return engine.getRuntimeService().startProcessInstanceByKey("programmingReceive").getId();
    }

    private ProcessInstanceQuery instancePage(int page) {
        return new ProcessInstanceQuery(ProcessInstanceScope.READABLE, null, "programming", null, null, null, null, null,
                new ProcessPageRequest(page, 1, ProcessSort.NEWEST_FIRST));
    }

    private ProcessTask mirror(long id, String taskId, String kind, String status) {
        var task = new ProcessTask();
        task.setId(id); task.setTaskId(taskId); task.setNodeType(kind); task.setStatus(status);
        task.setProcessInstanceId(instanceId); task.setNodeId("reviewA"); task.setNodeName("审批");
        task.setCreateTime(java.time.LocalDateTime.of(2026, 10, 16, 9, 0));
        return task;
    }

    private List<Task> tasks() {
        return engine.getTaskService().createTaskQuery().processInstanceId(instanceId).orderByTaskId().asc().list();
    }

    private ProcessVariablePatch patch(String requestId, String risk) {
        return new ProcessVariablePatch(new CommandMeta(requestId), instanceId, Map.of("risk", risk), Set.of());
    }

    private int receiptCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM process_command_receipt", Integer.class); }

    private ProcessCommandExecutor newExecutor() {
        return transactional(new ProcessCommandExecutor(receipts,
                // H2 的唯一冲突 SQLState 为 23505，与 PostgreSQL 一致；不伪装成 MySQL 的 1062。
                new JdbcWriteAttempt(jdbc, DatabaseDialects.insert(mysql ? DatabaseVendor.MYSQL : DatabaseVendor.POSTGRESQL)),
                new ObjectMapper().findAndRegisterModules(), access));
    }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(transactions);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(advice);
        return (T) factory.getProxy();
    }

    private static void assertCode(String code, Runnable action) {
        assertEquals(code, assertThrows(ProcessOperationException.class, action::run).getErrorCode());
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
    /** 公共命令通过真实 TaskActionService 推进引擎；表单解析单独做契约测试。 */
    private com.workflow.process.task.application.ProcessTaskCommandAdapter taskCommands(
            com.workflow.process.task.application.TaskSubmissionGuard guard) {
        com.workflow.admin.security.context.UserContext.setCurrentUser("alice-id", "alice");
        var outcome = new com.workflow.process.task.application.MultiInstanceOutcomeService(
                engine.getRuntimeService(), engine.getRepositoryService(), engine.getTaskService(), new ObjectMapper());
        var identity = new TaskIdentityAccessService(engine.getTaskService(),
                mock(com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper.class),
                mock(com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper.class),
                mock(com.workflow.contracts.identity.port.IdentityDirectoryPort.class));
        var core = transactional(new com.workflow.process.task.application.TaskActionService(operationActions,
                engine.getTaskService(), engine.getRuntimeService(), engine.getHistoryService(), mirrors,
                engine.getRepositoryService(), mock(ProcessOperationLogMapper.class),
                mock(com.workflow.admin.identity.user.application.SysUserService.class),
                submittedForms,
                mock(com.workflow.contracts.entity.port.EntityRecordPort.class),
                mock(com.workflow.process.cc.application.ProcessCcService.class),
                nextApprovers, outcome,
                mock(com.workflow.process.task.application.operation.NodeOperationCapabilityService.class), identity,
                mock(com.workflow.process.instance.application.ProcessEntityVariableService.class)));
        businessCore = core;
        return new com.workflow.process.task.application.ProcessTaskCommandAdapter(commands, access,
                engine.getTaskService(), engine.getRuntimeService(), engine.getHistoryService(), engine.getRepositoryService(),
                core, mock(com.workflow.process.task.application.TaskAddSignService.class),
                mock(LocalAddSignTaskAccessService.class), guard, mock(TaskOperationApplicationService.class), addSigns,
                mock(NodeOperationDecisionService.class), outcome, new ObjectMapper(),
                mock(com.workflow.entity.form.application.EntityFormActionService.class));
    }

    /** 直接检查 JDBC 表单与真实引擎状态，证明时机先后，重放不会再次触发。 */
    @Test
    void businessTimingsSurroundFormSaveEngineAdvanceAndIdempotentReplay() {
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        var observed = new ArrayList<String>();
        doAnswer(call -> { ((Runnable) call.getArgument(2)).run(); jdbc.update("INSERT INTO operation_timing_form VALUES ('saved')"); return null; })
                .when(submittedForms).applyEditableData(any(), any(), any());
        doAnswer(call -> {
            com.workflow.process.action.domain.FlowActionTriggerEvent event = call.getArgument(0);
            observed.add(event.getTriggerTiming());
            assertEquals("ENGINE", event.getTaskKind());
            assertEquals("SUBMIT", event.getBusinessOperation());
            assertEquals(Map.of("amount", 12), event.getSubmittedFormData());
            int formCount = jdbc.queryForObject("SELECT COUNT(*) FROM operation_timing_form", Integer.class);
            switch (event.getTriggerTiming()) {
                case "TASK_SUBMITTING", "TASK_FORM_SAVING" -> {
                    assertEquals(0, formCount);
                    assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
                }
                case "TASK_ADVANCING" -> {
                    assertEquals(1, formCount);
                    assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
                    engine.getRuntimeService().setVariable(instanceId, "risk", "HIGH");
                }
                case "TASK_SUBMITTED" -> {
                    assertEquals(1, formCount);
                    assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
                }
                default -> fail("unexpected phase: " + event.getTriggerTiming());
            }
            return null;
        }).when(businessActions).dispatch(any());
        doAnswer(call -> {
            assertEquals("TASK_ADVANCING", observed.get(observed.size() - 1));
            assertEquals("HIGH", engine.getRuntimeService().getVariable(instanceId, "risk"));
            return null;
        }).when(nextApprovers).validateAndStage(any(), any(), any(), any(), any(), any(), anyBoolean());
        var submission = new com.workflow.contracts.process.task.model.TaskSubmission("同意", null, Map.of("amount", 12), null, null);
        var command = new com.workflow.contracts.process.task.model.CompleteTaskCommand(new CommandMeta("timing-approve"), task.getId(), "approve", submission);
        assertFalse(port.complete(command).receipt().replayed());
        assertEquals(List.of("TASK_SUBMITTING", "TASK_FORM_SAVING", "TASK_ADVANCING", "TASK_SUBMITTED"), observed);
        assertTrue(port.complete(command).receipt().replayed());
        assertEquals(4, observed.size());
        assertEquals(1, receiptCount());
    }

    /** 包含引擎已完成后的失败，必须回滚表单、引擎与成功回执。 */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"TASK_SUBMITTING", "TASK_FORM_SAVING", "TASK_ADVANCING", "TASK_SUBMITTED"})
    void failureAtAnyBusinessStageRollsBackTheWholeOperation(String failureStage) {
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        doAnswer(call -> { ((Runnable) call.getArgument(2)).run(); jdbc.update("INSERT INTO operation_timing_form VALUES ('saved')"); return null; })
                .when(submittedForms).applyEditableData(any(), any(), any());
        var observed = new ArrayList<String>();
        doAnswer(call -> {
            com.workflow.process.action.domain.FlowActionTriggerEvent event = call.getArgument(0);
            observed.add(event.getTriggerTiming());
            if (failureStage.equals(event.getTriggerTiming())) throw new IllegalArgumentException("业务拒绝");
            return null;
        }).when(businessActions).dispatch(any());
        assertThrows(ProcessOperationException.class, () -> port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                new CommandMeta("failure-" + failureStage), task.getId(), "approve", null)));
        assertEquals(failureStage, observed.get(observed.size() - 1));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
        assertEquals(2, tasks().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operation_timing_form", Integer.class));
        assertEquals(0, receiptCount());
    }

    @Test void transferHasItsOwnStagesAndNeverPretendsToAdvanceTheTask() {
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        var observed = new ArrayList<String>();
        doAnswer(call -> {
            com.workflow.process.action.domain.FlowActionTriggerEvent event = call.getArgument(0);
            observed.add(event.getTriggerTiming());
            assertEquals("bob", event.getOperationParameters().get("transferTo"));
            assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
            return null;
        }).when(businessActions).dispatch(any());
        var command = new com.workflow.contracts.process.task.model.TransferTaskCommand(new CommandMeta("timing-transfer"), task.getId(), "bob", null);
        port.transfer(command);
        assertEquals(List.of("TASK_TRANSFERRING", "TASK_TRANSFERRED"), observed);
        assertEquals("bob", engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertTrue(port.transfer(command).receipt().replayed());
        assertEquals(2, observed.size());
    }

    @Test void advancingActionChangesTheRealGatewayRouteBeforeTheEngineComputesIt() {
        engine.getRepositoryService().createDeployment().addString("business-route.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="timing-route">
                  <process id="business_route" isExecutable="true"><startEvent id="start"/>
                  <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                  <userTask id="review" flowable:assignee="alice"/>
                  <sequenceFlow id="toGate" sourceRef="review" targetRef="gate"/><exclusiveGateway id="gate" default="low"/>
                  <sequenceFlow id="high" sourceRef="gate" targetRef="highReview"><conditionExpression xsi:type="tFormalExpression">${risk == 'HIGH'}</conditionExpression></sequenceFlow>
                  <sequenceFlow id="low" sourceRef="gate" targetRef="lowReview"/>
                  <userTask id="highReview" flowable:assignee="alice"/><userTask id="lowReview" flowable:assignee="alice"/>
                  </process></definitions>
                """).deploy();
        instanceId = engine.getRuntimeService().startProcessInstanceByKey("business_route", Map.of("risk", "LOW")).getId();
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        doAnswer(call -> {
            com.workflow.process.action.domain.FlowActionTriggerEvent event = call.getArgument(0);
            if ("TASK_ADVANCING".equals(event.getTriggerTiming())) engine.getRuntimeService().setVariable(instanceId, "risk", "HIGH");
            // 后处理再次改值也不能改变本次已经计算出的路由。
            if ("TASK_SUBMITTED".equals(event.getTriggerTiming())) engine.getRuntimeService().setVariable(instanceId, "risk", "LOW");
            return null;
        }).when(businessActions).dispatch(any());
        port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(new CommandMeta("route-approve"), task.getId(), "approve", null));
        assertEquals(List.of("highReview"), tasks().stream().map(Task::getTaskDefinitionKey).toList());
        assertEquals("LOW", engine.getRuntimeService().getVariable(instanceId, "risk"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PROCESS_WITHDRAWING", "PROCESS_WITHDRAWN", "SUCCESS"})
    void withdrawStagesSurroundDeletionAndAllFailuresRestoreTheInstance(String failureStage) {
        endListener = new com.workflow.process.action.infrastructure.flowable.FlowActionEngineEventListener(
                mock(com.workflow.process.action.application.FlowActionDispatcher.class), engine.getRuntimeService(),
                mock(com.workflow.process.action.infrastructure.flowable.FlowActionRuntimeAdapter.class), operationActions);
        engine.getRuntimeService().addEventListener(endListener, org.flowable.common.engine.api.delegate.event.FlowableEngineEventType.PROCESS_CANCELLED);
        engine.getIdentityService().setAuthenticatedUserId("alice");
        try { instanceId = engine.getRuntimeService().startProcessInstanceByKey("programming", Map.of("risk", "LOW")).getId(); }
        finally { engine.getIdentityService().setAuthenticatedUserId(null); }
        taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var observed = new ArrayList<String>();
        doAnswer(call -> {
            com.workflow.process.action.domain.FlowActionTriggerEvent event = call.getArgument(0);
            observed.add(event.getTriggerTiming());
            assertEquals("原因", event.getOperationParameters().get("reason"));
            if ("PROCESS_WITHDRAWN".equals(event.getTriggerTiming())) {
                assertTrue(tasks().isEmpty());
                jdbc.update("INSERT INTO operation_timing_form VALUES ('withdrawn')");
            } else assertEquals(2, tasks().size());
            if (failureStage.equals(event.getTriggerTiming())) throw new IllegalArgumentException("拒绝撤回");
            return null;
        }).when(businessActions).dispatch(any());
        if ("SUCCESS".equals(failureStage)) {
            businessCore.withdrawProcess(instanceId, "alice", "原因");
            assertTrue(tasks().isEmpty());
            assertEquals(List.of("PROCESS_WITHDRAWING", "PROCESS_WITHDRAWN"), observed);
        } else {
            assertThrows(RuntimeException.class, () -> businessCore.withdrawProcess(instanceId, "alice", "原因"));
            assertEquals(2, tasks().size());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operation_timing_form", Integer.class));
        }
    }

    @Test void deferredBackgroundCompletionOnlyRunsActualFormAndEngineSteps() {
        taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        businessCore.completeDeferredTask(task.getId(), "alice", "approve", "加签收口", null, null, Map.of());
        var events = org.mockito.ArgumentCaptor.forClass(com.workflow.process.action.domain.FlowActionTriggerEvent.class);
        verify(businessActions, times(2)).dispatch(events.capture());
        assertEquals(List.of("TASK_FORM_SAVING", "TASK_ADVANCING"),
                events.getAllValues().stream().map(com.workflow.process.action.domain.FlowActionTriggerEvent::getTriggerTiming).toList());
        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @Test
    void publicCompletionUsesCoreOutcomeAndPersistsReplayAfterTaskDisappears() {
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        var command = new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                new CommandMeta("approve-one"), task.getId(), "approve", null);
        var first = port.complete(command);
        assertEquals("COMPLETED", first.taskStatus());
        assertEquals(ProcessLifecycle.RUNNING, first.processLifecycle());
        assertEquals("approve", engine.getRuntimeService().getVariable(instanceId, "approved"));
        assertEquals(1, tasks().size());
        assertTrue(port.complete(command).receipt().replayed());
        assertEquals(first.receipt().operationId(), port.complete(command).receipt().operationId());
        verify(mirrors, times(1)).completeTask(eq(task.getId()), eq("approve"), any(), any());
    }

    @Test
    void receiptFailureRollsBackRealCompletionAndOutcomeVariables() {
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        jdbc.execute("ALTER TABLE process_command_receipt ADD CONSTRAINT fail_receipt CHECK (status <> 'SUCCESS')");
        assertThrows(RuntimeException.class, () -> port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                new CommandMeta("complete-fail"), task.getId(), "approve", null)));
        assertEquals(2, tasks().size());
        assertNull(engine.getRuntimeService().getVariable(instanceId, "approved"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_command_receipt", Integer.class));
    }

    @Test
    void transferChangesAssigneeWithoutAdvancingAndRollsBackWithOuterTransaction() {
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        var command = new com.workflow.contracts.process.task.model.TransferTaskCommand(
                new CommandMeta("transfer-one"), task.getId(), "bob", null);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertEquals("TRANSFERRED", port.transfer(command).taskStatus());
            assertEquals("bob", engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            status.setRollbackOnly();
        });
        assertEquals("alice", engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertFalse(port.transfer(command).receipt().replayed());
        assertEquals(2, tasks().size());
        assertTrue(port.transfer(command).receipt().replayed());
    }

    @Test
    void formDenialAndOtherAssigneeCannotAdvanceOrLeaveReceipt() {
        var guard = mock(com.workflow.process.task.application.TaskSubmissionGuard.class);
        var port = taskCommands(guard);
        var task = tasks().get(0);
        doThrow(new ForbiddenException("提交按钮不可用")).when(guard).require(anyString(), any(), any());
        assertCode("PROCESS_ACCESS_DENIED", () -> port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                new CommandMeta("form-denied"), task.getId(), "approve", null)));
        reset(guard);
        engine.getTaskService().setAssignee(task.getId(), "bob");
        assertCode("PROCESS_ACCESS_DENIED", () -> port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                new CommandMeta("other-assignee"), task.getId(), "approve", null)));
        assertEquals(2, tasks().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_command_receipt", Integer.class));
        verifyNoInteractions(guard);
    }

    @Test
    void differentRequestIdCannotReenterSameTaskAndScopeIsClearedAfterFailure() {
        var guard = mock(com.workflow.process.task.application.TaskSubmissionGuard.class);
        var port = taskCommands(guard);
        var task = tasks().get(0);
        doAnswer(invocation -> {
            port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                    new CommandMeta("recursive-inner"), task.getId(), "approve", null));
            return null;
        }).when(guard).require(anyString(), any(), any());
        var command = new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                new CommandMeta("recursive-outer"), task.getId(), "approve", null);
        assertCode("COMMAND_REENTRANT", () -> port.complete(command));
        assertEquals(2, tasks().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_command_receipt", Integer.class));
        reset(guard);
        assertFalse(port.complete(command).receipt().replayed());
    }

    @Test
    void arbitraryActionAndDedicatedOperationsCannotBypassTheirGuards() {
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var task = tasks().get(0);
        for (String action : List.of("invented-action", "resubmit", "transfer")) {
            assertCode("TASK_ACTION_NOT_ALLOWED", () -> port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                    new CommandMeta("bad-" + action), task.getId(), action, null)));
        }
        assertEquals(2, tasks().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_command_receipt", Integer.class));
    }
    @Test
    void concurrentDifferentCompletionsAdvanceTaskOnlyOnceAndRollbackLosingReceipt() throws Exception {
        var guard = mock(com.workflow.process.task.application.TaskSubmissionGuard.class);
        var port = taskCommands(guard);
        var task = tasks().get(0);
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> { barrier.await(10, TimeUnit.SECONDS); return null; })
                .when(guard).require(anyString(), any(), any());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String requestId = "race-" + i;
                results.add(workers.submit(() -> {
                    com.workflow.admin.security.context.UserContext.setCurrentUser("alice-id", "alice");
                    try { return port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                            new CommandMeta(requestId), task.getId(), "approve", null)); }
                    catch (RuntimeException failure) { return failure; }
                    finally { com.workflow.admin.security.context.UserContext.clear(); }
                }));
            }
            List<Object> actual = List.of(results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS));
            assertEquals(1, actual.stream().filter(com.workflow.contracts.process.task.model.TaskCommandResult.class::isInstance).count());
            assertEquals(1, actual.stream().filter(RuntimeException.class::isInstance).count());
            assertEquals(1, tasks().size());
            assertEquals(1, receiptCount());
        } finally { workers.shutdownNow(); }
    }

    @Test
    void returnUsesPublishedRouteAndResubmitPreservesApproveOutcomeWithDistinctAction() {
        engine.getRepositoryService().createDeployment().addString("return.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="programming-test">
                  <process id="returnTest" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                    <userTask id="review" flowable:assignee="alice"/>
                    <sequenceFlow id="s2" sourceRef="review" targetRef="gate"/>
                    <exclusiveGateway id="gate" default="done"/>
                    <sequenceFlow id="back" sourceRef="gate" targetRef="edit">
                      <conditionExpression xsi:type="tFormalExpression"><![CDATA[${approved == 'reject'}]]></conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="done" sourceRef="gate" targetRef="end"/>
                    <userTask id="edit" flowable:assignee="alice"/>
                    <sequenceFlow id="again" sourceRef="edit" targetRef="gate"/><endEvent id="end"/>
                  </process>
                </definitions>
                """).deploy();
        org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId("alice-id");
        String returned;
        try { returned = engine.getRuntimeService().startProcessInstanceByKey("returnTest").getId(); }
        finally { org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(null); }
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var decisions = (NodeOperationDecisionService) org.springframework.test.util.ReflectionTestUtils.getField(port, "decisions");
        var rule = new com.workflow.process.task.application.operation.NodeOperationPolicy.Rule(
                true, null, null, false, List.of(), false, null, Set.of(), Set.of(), Set.of("edit", "absent"), null);
        when(decisions.availableActions(anyString())).thenReturn(Map.of("reject",
                new NodeOperationDecisionService.ActionDecision("reject", true, null, null, rule)));
        Task source = engine.getTaskService().createTaskQuery().processInstanceId(returned).singleResult();
        var meta = new CommandMeta("return-command");
        assertCode("TASK_ACTION_NOT_ALLOWED", () -> port.returnToNode(
                new com.workflow.contracts.process.task.model.ReturnTaskCommand(meta, source.getId(), "absent", null)));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(source.getId()).singleResult());
        assertEquals(0, receiptCount());
        var result = port.returnToNode(new com.workflow.contracts.process.task.model.ReturnTaskCommand(meta, source.getId(), "edit", null));
        assertFalse(result.receipt().replayed());
        Task edit = engine.getTaskService().createTaskQuery().processInstanceId(returned).singleResult();
        assertEquals("edit", edit.getTaskDefinitionKey());
        var submitted = port.resubmit(new com.workflow.contracts.process.task.model.ResubmitTaskCommand(
                new CommandMeta("resubmit-command"), edit.getId(), null));
        assertEquals(ProcessLifecycle.ENDED, submitted.processLifecycle());
        assertEquals("approve", engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(returned).variableName("approved").singleResult().getValue());
        assertEquals("resubmit", engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(returned).variableName("action").singleResult().getValue());
        assertEquals(Boolean.FALSE, engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(returned).variableName("_rejected_").singleResult().getValue());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void historyReturnReopensTaskAndDispatchesOnlyOnSourceWithIdempotentReplay(boolean published) {
        var xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="return-selection">
                  <process id="returnSelection" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="first" sourceRef="start" targetRef="prepare"/>
                    <userTask id="prepare" flowable:assignee="alice"/>
                    <sequenceFlow id="editLine" sourceRef="prepare" targetRef="edit"/>
                    <userTask id="edit" name="填写" flowable:assignee="alice">
                      <extensionElements><flowable:properties><flowable:property name="taskReturnConfig"
                        value='{"mode":"HISTORY"}'/></flowable:properties></extensionElements>
                    </userTask>
                    <sequenceFlow id="next" sourceRef="edit" targetRef="choice"/>
                    <userTask id="alternate" flowable:assignee="alice"/>
                    <sequenceFlow id="alternateLine" sourceRef="alternate" targetRef="choice"/>
                    <exclusiveGateway id="choice"/><sequenceFlow id="reviewLine" sourceRef="choice" targetRef="review"/>
                    <userTask id="review" flowable:assignee="alice">
                      <extensionElements><flowable:properties><flowable:property name="taskReturnConfig"
                        value='{"mode":"FIXED","targetNodeIds":["edit","alternate"],"resubmitNodeId":"edit"}'/></flowable:properties></extensionElements>
                    </userTask>
                    <sequenceFlow id="exit" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """;
        var policy = new com.workflow.process.task.application.operation.NodeHistoryReturnPolicy(new ObjectMapper());
        policy.validate(xml);
        assertThrows(IllegalArgumentException.class, () -> policy.validate(xml.replace("exclusiveGateway", "parallelGateway")));
        String deployedXml = xml;
        if (published) {
            String snapshot = policy.compile(xml, "returnSelection", null);
            deployedXml = xml.replace("<process id=\"returnSelection\" isExecutable=\"true\">", """
                    <process id="returnSelection" isExecutable="true">
                      <extensionElements><flowable:properties>
                        <flowable:property name="publishedTaskReturnTargets" value='%s'/>
                      </flowable:properties></extensionElements>
                    """.formatted(snapshot));
        }
        engine.getRepositoryService().createDeployment().addString("selection.bpmn20.xml", deployedXml).deploy();
        var taskService = engine.getTaskService();
        var runtime = engine.getRuntimeService();
        engine.getIdentityService().setAuthenticatedUserId("alice");
        String id;
        try { id = runtime.startProcessInstanceByKey("returnSelection", Map.of("businessValue", "keep")).getId(); }
        finally { engine.getIdentityService().setAuthenticatedUserId(null); }
        var prepare = taskService.createTaskQuery().processInstanceId(id).singleResult();
        taskService.complete(prepare.getId());
        var edit = taskService.createTaskQuery().processInstanceId(id).singleResult();
        taskService.complete(edit.getId());
        var review = taskService.createTaskQuery().processInstanceId(id).singleResult();
        var port = taskCommands(mock(com.workflow.process.task.application.TaskSubmissionGuard.class));
        var handled = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
        handled.setTaskId(edit.getId());
        handled.setStatus("done");
        handled.setAction("approve");
        var earlier = new com.workflow.process.task.infrastructure.persistence.record.ProcessTask();
        earlier.setTaskId(prepare.getId());
        earlier.setStatus("done");
        earlier.setAction("approve");
        when(mirrors.getTasksByProcessInstance(id)).thenReturn(List.of(handled, earlier));
        when(mirrors.lockPendingHistoryReturnSource(review.getId())).thenReturn(true);
        var logs = mock(ProcessOperationLogMapper.class);
        var records = new java.util.ArrayList<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog>();
        var failPostAction = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(logs.selectList(any())).thenReturn(records);
        when(logs.insert(any(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog.class)))
                .thenAnswer(call -> { if (!failPostAction.get()) records.add(call.getArgument(0)); return 1; });
        var returns = new com.workflow.process.task.application.TaskHistoryReturnService(taskService, runtime,
                engine.getHistoryService(), engine.getRepositoryService(), businessCore,
                mock(com.workflow.process.task.application.TaskAddSignService.class),
                mock(com.workflow.process.task.application.TaskSubmissionGuard.class), mirrors, policy, access,
                commands, operationActions, logs, new ObjectMapper(),
                mock(com.workflow.contracts.identity.port.IdentityDirectoryPort.class));

        var targets = returns.targets(review.getId());
        assertEquals(List.of("edit"), targets.stream().map(target -> target.nodeId()).toList());
        assertEquals(!published, targets.get(0).resubmit());
        var request = new com.workflow.process.task.application.TaskHistoryReturnService.Request(
                "history-selection", targets.get(0).activityInstanceId(), "请补充材料", null, null);
        var stages = new java.util.ArrayList<String>();
        org.mockito.Mockito.doAnswer(call -> {
            var event = (com.workflow.process.action.domain.FlowActionTriggerEvent) call.getArgument(0);
            if (event.getTriggerTiming().startsWith("TASK_RETURN")) {
                stages.add(event.getTriggerTiming());
                assertEquals("review", event.getElementId());
                assertEquals(review.getId(), event.getTaskId());
                assertEquals("edit", event.getOperationParameters().get("targetNodeId"));
                if ("TASK_RETURNED".equals(event.getTriggerTiming())) {
                    assertEquals(taskService.createTaskQuery().processInstanceId(id).singleResult().getId(),
                            event.getOperationParameters().get("targetTaskId"));
                    if (failPostAction.get()) throw new IllegalArgumentException("阻断回退");
                }
            }
            return null;
        }).when(businessActions).dispatch(any());
        assertThrows(ProcessOperationException.class, () -> returns.execute(review.getId(), request));
        assertEquals(review.getId(), taskService.createTaskQuery().processInstanceId(id).singleResult().getId());
        assertEquals(0, receiptCount());
        failPostAction.set(false);
        stages.clear();
        assertEquals("edit", returns.execute(review.getId(), request).targetNodeId());
        assertEquals("edit", returns.execute(review.getId(), request).targetNodeId());
        assertEquals(List.of("TASK_RETURNING", "TASK_RETURNED"), stages);
        var reopened = taskService.createTaskQuery().processInstanceId(id).singleResult();
        assertEquals("edit", reopened.getTaskDefinitionKey());
        org.junit.jupiter.api.Assertions.assertNotEquals(edit.getId(), reopened.getId());
        assertEquals(!published, returns.canResubmit(reopened.getId()));
        assertEquals(List.of("prepare"), returns.targets(reopened.getId()).stream().map(target -> target.nodeId()).toList());
        assertEquals("keep", runtime.getVariable(id, "businessValue"));
        if (!published) {
            assertCode("TASK_ACTION_NOT_ALLOWED", () -> port.complete(new com.workflow.contracts.process.task.model.CompleteTaskCommand(
                    new CommandMeta("invalid-reopen-completion"), reopened.getId(), "approve", null)));
        }
        var submission = new com.workflow.process.task.api.request.TaskCompleteRequest();
        submission.setRequestId("history-selection-resubmit");
        submission.setTaskId(reopened.getId());
        submission.setAction(published ? "approve" : "resubmit");
        port.completeHttp(submission);
        assertEquals("approve", runtime.getVariable(id, "approved"));
        var nextReview = taskService.createTaskQuery().processInstanceId(id).singleResult();
        assertEquals("review", nextReview.getTaskDefinitionKey());
        taskService.complete(nextReview.getId());
        assertEquals(0, runtime.createProcessInstanceQuery().processInstanceId(id).count());
    }

}
