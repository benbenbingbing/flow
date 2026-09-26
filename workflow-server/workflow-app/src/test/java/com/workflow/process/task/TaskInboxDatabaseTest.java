package com.workflow.process.task;

import com.workflow.process.task.application.model.TaskInboxQuery;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.model.EntityTaskSummary;
import com.workflow.contracts.entity.port.*;
import com.workflow.contracts.entity.form.port.EntityFormRuntimePort;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.contracts.process.port.TaskBusinessSummaryPort;
import com.workflow.entity.data.application.EntityTaskSummaryRefresh;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.task.application.*;
import com.workflow.process.task.infrastructure.flowable.TaskInboxProjectionListener;
import com.workflow.process.task.infrastructure.persistence.mapper.*;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 实际 Spring/Flowable/MyBatis 共用数据库事务，覆盖投影提交、失败回滚、分页权限和历史回填。 */
class TaskInboxDatabaseTest {
    private ProcessEngine engine;
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ProcessTaskMapper tasks;
    private ProcessTaskService processTasks;
    private SqlSessionTemplate session;
    private final java.util.concurrent.atomic.AtomicInteger queryCount = new java.util.concurrent.atomic.AtomicInteger();
    private TaskInboxMapper inbox;
    private TaskInboxProjectionMapper projectionMapper;
    private TaskInboxProjectionService projection;
    private EntityTaskSummaryRefresh summaryRefresh;
    private TaskInboxQueryService queries;
    private java.sql.Connection mysqlAdmin;
    private String mysqlDatabase;
    private final AtomicBoolean failSummary = new AtomicBoolean();

    @BeforeEach
    void setup() throws Exception {
        source = new DriverManagerDataSource("jdbc:h2:mem:inbox_" + UUID.randomUUID()
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        if ("true".equals(System.getenv("FLOW_INBOX_MYSQL_TEST"))) {
            String base = System.getenv("FLOW_INBOX_MYSQL_ADMIN_URL");
            if (base == null || !base.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/\\?.*"))
                throw new IllegalArgumentException("仅允许本机独立临时 MySQL 库");
            String user = System.getenv("FLOW_INBOX_MYSQL_USER"), password = System.getenv("FLOW_INBOX_MYSQL_PASSWORD");
            mysqlAdmin = java.sql.DriverManager.getConnection(base, user, password);
            mysqlDatabase = "flow_inbox_runtime_" + UUID.randomUUID().toString().replace("-", "");
            try(var statement = mysqlAdmin.createStatement()) { statement.execute("CREATE DATABASE " + mysqlDatabase + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
            source = new DriverManagerDataSource(base.replace("/?", "/"+mysqlDatabase+"?"), user, password);
        }
        transactions = new DataSourceTransactionManager(source);
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(source); config.setTransactionManager(transactions);
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        engine = config.buildProcessEngine();
        jdbc = new JdbcTemplate(source);
        if (mysqlAdmin == null) jdbc.execute("SET MODE MySQL");
        createTables();
        var mybatis = new MybatisConfiguration();
        mybatis.setDatabaseId(mysqlAdmin == null ? "POSTGRESQL" : "MYSQL"); mybatis.setMapUnderscoreToCamelCase(true);
        for (Class<?> type : List.of(ProcessTaskMapper.class, ProcessTaskCandidateUserMapper.class,
                ProcessTaskCandidateGroupMapper.class, TaskInboxMapper.class, TaskInboxProjectionMapper.class, com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper.class)) mybatis.addMapper(type);
        // 投影回填已接入 IPage，独立测试工厂也必须安装生产环境的分页插件。
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(mysqlAdmin == null ? DbType.POSTGRE_SQL : DbType.MYSQL));
        mybatis.addInterceptor(pagination);
        // 计数器位于分页插件外层，才能在插件转调六参数 query 前统计一次业务查询。
        mybatis.addInterceptor(new QueryCounter(queryCount));
        new com.workflow.config.database.DatabaseMybatisConfiguration().largeTextBindings().customize(mybatis);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source); factory.setConfiguration(mybatis);
        session = new SqlSessionTemplate(factory.getObject());
        tasks = session.getMapper(ProcessTaskMapper.class);
        inbox = session.getMapper(TaskInboxMapper.class);
        projectionMapper = session.getMapper(TaskInboxProjectionMapper.class);
        var directory = mock(IdentityDirectoryPort.class);
        when(directory.getDisplayName(anyString())).thenAnswer(call -> {
            if (failSummary.get()) throw new IllegalStateException("injected directory failure");
            return call.getArgument(0);
        });
        when(directory.findGroup(anyString())).thenAnswer(call -> {
            if (failSummary.get()) throw new IllegalStateException("injected directory failure");
            return Optional.empty();
        });
        var beans = new DefaultListableBeanFactory();
        processTasks = transactional(new ProcessTaskService(tasks, engine.getTaskService(), engine.getRuntimeService(),
                engine.getRepositoryService(), mock(NodeConfigMapper.class), mock(EntityFormRuntimePort.class),
                mock(ProcessDefinitionConfigMapper.class), new ObjectMapper(), mock(EntityRecordPort.class), directory));
        beans.registerSingleton("processTasks", processTasks);
        EntityTaskSummaryPort summaries = (entity, record) -> {
            if (failSummary.get()) throw new IllegalStateException("injected summary failure");
            var rows = jdbc.query("SELECT name,code,data_name,current_task_name,status FROM inbox_business WHERE id=?",
                    (rs, index) -> new EntityTaskSummary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), record);
            return rows.isEmpty() ? EntityTaskSummary.empty() : rows.get(0);
        };
        projection = transactional(new TaskInboxProjectionService(tasks,
                session.getMapper(ProcessTaskCandidateUserMapper.class), session.getMapper(ProcessTaskCandidateGroupMapper.class),
                projectionMapper, engine.getTaskService(), engine.getHistoryService(), directory, summaries,
                beans.getBeanProvider(ProcessTaskService.class), engine.getRuntimeService()));
        beans.registerSingleton("projection", projection);
        summaryRefresh = new EntityTaskSummaryRefresh(beans.getBeanProvider(TaskBusinessSummaryPort.class));
        engine.getRuntimeService().addEventListener(new TaskInboxProjectionListener(beans.getBeanProvider(TaskInboxProjectionService.class)));
        queries = transactional(new TaskInboxQueryService(inbox, mock(EntityStatusService.class)));
        engine.getRepositoryService().createDeployment().addString("inbox.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
                <process id="inbox" isExecutable="true"><startEvent id="start"/><sequenceFlow id="a" sourceRef="start" targetRef="review"/>
                <userTask id="review" name="审核" flowable:candidateUsers="alice" flowable:candidateGroups="reviewers,ROLE_auditors"/>
                <sequenceFlow id="b" sourceRef="review" targetRef="end"/><endEvent id="end"/></process></definitions>
                """).deploy();
    }

    @AfterEach void cleanup() throws Exception {
        if (engine != null) engine.close();
        if (mysqlAdmin != null) {
            try(var statement = mysqlAdmin.createStatement()) { statement.execute("DROP DATABASE " + mysqlDatabase); }
            mysqlAdmin.close();
        }
    }

    @Test void createsFinalMixedCandidatesAndRespondsToLiveMembership() {
        String task = start();
        assertEquals(1, count("process_task"));
        assertEquals(1, count("process_task_candidate_user"));
        assertEquals(2, count("process_task_candidate_group"));
        assertNull(tasks.selectByTaskId(task).getAssigneeId());
        assertEquals(1, visible("alice"));
        assertEquals(1, visible("bob"));
        assertEquals(1, visible("carol-id"));
        assertEquals(0, visible("outsider"));
        jdbc.update("INSERT INTO sys_user_group VALUES ('alice-id','group-id')");
        assertEquals(1,visible("alice")); // 同时命中直接用户与组，也只能返回一次。
        jdbc.update("DELETE FROM sys_user_group WHERE user_id='bob-id'");
        assertEquals(0, visible("bob"));
        jdbc.update("UPDATE sys_role SET status='1'");
        assertEquals(0, visible("carol"));
        jdbc.update("UPDATE sys_user SET status='1' WHERE username='alice'");
        assertEquals(0, visible("alice"));
    }

    @Test void claimUnclaimCandidateReplacementAndCompletionStayConsistent() {
        String task = start();
        engine.getTaskService().claim(task, "bob");
        assertEquals("bob", tasks.selectByTaskId(task).getAssigneeId());
        assertEquals(0, visible("alice"));
        assertEquals(1, visible("bob"));
        engine.getTaskService().unclaim(task);
        assertNull(tasks.selectByTaskId(task).getAssigneeId());
        transaction(() -> {
            engine.getTaskService().deleteCandidateUser(task, "alice");
            engine.getTaskService().deleteCandidateGroup(task, "reviewers");
            engine.getTaskService().deleteCandidateGroup(task, "ROLE_auditors");
            engine.getTaskService().addCandidateUser(task, "outsider");
            engine.getTaskService().addUserIdentityLink(task, "alice", "participant");
        });
        assertEquals(0, visible("alice")); assertEquals(0, visible("bob")); assertEquals(1, visible("outsider"));
        transaction(() -> { engine.getTaskService().claim(task, "outsider"); engine.getTaskService().complete(task); });
        assertEquals("done", tasks.selectByTaskId(task).getStatus());
        assertEquals(0, count("process_task_candidate_user"));
        assertEquals(1, inbox.count(query("outsider", "done")));
        assertEquals(0, inbox.count(query("alice", "done")));
    }

    @Test void projectionFailureRollsBackEngineAndCandidateWrites() {
        String task = start();
        failSummary.set(true);
        assertThrows(RuntimeException.class, () -> engine.getTaskService().claim(task, "bob"));
        assertNull(engine.getTaskService().createTaskQuery().taskId(task).singleResult().getAssignee());
        assertNull(tasks.selectByTaskId(task).getAssigneeId());
        assertEquals(1, visible("alice"));
        assertThrows(RuntimeException.class, () -> engine.getTaskService().deleteCandidateUser(task, "alice"));
        assertTrue(engine.getTaskService().getIdentityLinksForTask(task).stream().anyMatch(link -> "alice".equals(link.getUserId())));
        assertEquals(1, count("process_task_candidate_user"));
    }

    @Test void engineDeletionRemovesCandidatesWithoutTurningCancellationIntoDone() {
        String task = start();
        engine.getRuntimeService().deleteProcessInstance(tasks.selectByTaskId(task).getProcessInstanceId(), "withdraw");
        assertEquals("skip", tasks.selectByTaskId(task).getStatus());
        assertEquals(0, count("process_task_candidate_user")); assertEquals(0, count("process_task_candidate_group"));
        assertEquals(0, visible("alice"));
    }

    @Test void cancellationAfterLogicalDeletionStillRemovesCandidateRelations() {
        String task = start(); String instance = tasks.selectByTaskId(task).getProcessInstanceId();
        transaction(() -> { processTasks.deleteTasksByProcessInstance(instance); engine.getRuntimeService().deleteProcessInstance(instance,"withdraw"); });
        assertEquals(0,count("process_task_candidate_user")); assertEquals(0,count("process_task_candidate_group"));
        assertEquals(0,visible("alice"));
    }

    @Test void backfillIsResumableAndDoesNotOverwriteCurrentClaim() throws Exception {
        String task = start(); long id = tasks.selectByTaskId(task).getId();
        jdbc.update("UPDATE process_task SET inbox_summary_ready=0,inbox_identity_ready=0,assignee_id='reviewers'");
        jdbc.update("DELETE FROM process_task_candidate_user"); jdbc.update("DELETE FROM process_task_candidate_group");
        assertTrue(queries.findPage(query("alice", "todo")).isEmpty());
        assertEquals(List.of(id), projectionMapper.findUnready(0, 10));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Future<?> first = executor.submit(() -> { await(barrier); projection.backfill(id); });
            Future<?> second = executor.submit(() -> { await(barrier); engine.getTaskService().claim(task, "bob"); });
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        projection.backfill(id);
        assertEquals("bob", tasks.selectByTaskId(task).getAssigneeId());
        assertEquals(0, projectionMapper.countUnready());
        assertEquals(1, count("process_task_candidate_user"));
        assertEquals(0, visible("alice")); assertEquals(1, visible("bob"));
    }

    @Test void businessChangesRefreshDoneAndTodoTogetherAndRollbackOnFailure() {
        String task = start();
        transaction(() -> { engine.getTaskService().claim(task, "alice"); engine.getTaskService().complete(task); });
        start();
        var staleTask = tasks.selectByTaskId(task);
        transaction(() -> {
            jdbc.update("UPDATE inbox_business SET name='新名称',status='approved'");
            summaryRefresh.changed("invoice", "record"); summaryRefresh.changed("invoice", "record");
        });
        staleTask.setPriority(99); tasks.updateById(staleTask); // 旧的状态更新对象不能回写旧摘要。
        assertEquals(List.of("新名称", "新名称"), jdbc.queryForList("SELECT business_name FROM process_task ORDER BY id", String.class));
        failSummary.set(true);
        assertThrows(RuntimeException.class, () -> transaction(() -> {
            jdbc.update("UPDATE inbox_business SET name='不能提交'"); summaryRefresh.changed("invoice", "record");
        }));
        assertEquals("新名称", jdbc.queryForObject("SELECT name FROM inbox_business", String.class));
        failSummary.set(false);
        transaction(() -> { jdbc.update("DELETE FROM inbox_business"); summaryRefresh.changed("invoice", "record"); });
        assertTrue(jdbc.queryForList("SELECT business_name FROM process_task", String.class).stream().allMatch(Objects::isNull));
    }

    @Test void databaseFiltersStablePagesAndEscapedKeywordsPreserveListSemantics() {
        for (int i=0; i<3; i++) {
            String task = start();
            engine.getTaskService().claim(task, "alice"); engine.getTaskService().complete(task);
        }
        jdbc.update("UPDATE process_task SET end_time='2026-09-23 12:00:00',start_time='2026-09-20 15:00:00',priority=80");
        var q = new TaskInboxQuery("alice-id", "done", 1, 2, "%_!", "发起人", "URGENT", LocalDate.of(2026,9,20), LocalDate.of(2026,9,20));
        assertEquals(3, inbox.count(q));
        var page = inbox.selectPage(q);
        assertEquals(2, page.size()); assertTrue(page.get(0).getId() > page.get(1).getId());
        assertNull(page.get(0).getFormData()); assertEquals("发起人(starter)", page.get(0).getStartUserName());
        var next = inbox.selectPage(new TaskInboxQuery("alice", "done", 2, 2, "%_!", null, null, null, null));
        assertEquals(1, next.size()); assertTrue(next.get(0).getId() < page.get(1).getId());
        assertEquals(0, inbox.count(new TaskInboxQuery("alice", "done", 1, 10, null, null, "HIGH", null, null)));
        assertEquals(0, inbox.count(new TaskInboxQuery("alice", "done", 1, 10, null, null, null, LocalDate.of(2026,9,21), null)));
        jdbc.update("UPDATE sys_user SET nickname='更名' WHERE username='starter'");
        assertEquals("更名(starter)", inbox.selectPage(query("alice", "done")).get(0).getStartUserName());
        jdbc.update("UPDATE sys_user SET nickname='Starter' WHERE username='starter'");
        assertEquals("Starter(starter)",inbox.selectPage(query("alice","done")).get(0).getStartUserName());
        jdbc.update("UPDATE process_task SET business_name='CAFÉ'");
        assertEquals(3,inbox.count(new TaskInboxQuery("alice","done",1,10,"café",null,null,null,null)));
        assertEquals(0,inbox.count(new TaskInboxQuery("alice","done",1,10,"cafe",null,null,null,null)));
        org.springframework.test.util.ReflectionTestUtils.setField(queries,"enabled",false);
        assertTrue(queries.findPage(query("alice","done")).isEmpty());
    }

    @Test void transferReusesTaskPrimaryKeyAndClearsPreviousOutcome() {
        String task = start();
        long id = tasks.selectByTaskId(task).getId();
        transaction(() -> {
            engine.getTaskService().setAssignee(task, "bob");
            processTasks.completeTask(task, "transfer", "转交");
            var transferred = engine.getTaskService().createTaskQuery().taskId(task).singleResult();
            processTasks.createTask(transferred, engine.getRuntimeService().getVariables(transferred.getProcessInstanceId()));
        });
        var row = tasks.selectByTaskId(task);
        assertEquals(id, row.getId()); assertEquals(1, count("process_task"));
        assertEquals("todo",row.getStatus()); assertEquals("bob",row.getAssigneeId());
        assertNull(row.getEndTime()); assertNull(row.getDuration()); assertNull(row.getAction());
        assertEquals(0,visible("alice")); assertEquals(1,visible("bob"));
    }

    @Test void addSignVisibilityRequiresActiveParentChildAndMatchingLiveSource() {
        String task = start();
        var child = tasks.selectByTaskId(task);
        child.setId(null); child.setTaskId("add-sign"); child.setNodeType("ADD_SIGN");
        child.setAssigneeId("outsider"); child.setAssigneeType("user"); tasks.insert(child);
        jdbc.update("INSERT INTO process_task_add_sign VALUES ('sign',?,'ACTIVE',?)",task,child.getProcessInstanceId());
        jdbc.update("INSERT INTO process_task_add_sign_user VALUES ('sign','add-sign','TODO','outsider-id')");
        assertEquals(1, visible("outsider"));
        jdbc.update("UPDATE process_task_add_sign SET status='WAITING_SOURCE'"); assertEquals(0,visible("outsider"));
        jdbc.update("UPDATE process_task_add_sign SET status='ACTIVE'");
        jdbc.update("UPDATE process_task_add_sign_user SET status='DONE'"); assertEquals(0,visible("outsider"));
        jdbc.update("UPDATE process_task_add_sign_user SET status='TODO'");
        jdbc.update("UPDATE process_task SET entity_data_id='other' WHERE task_id='add-sign'"); assertEquals(0,visible("outsider"));
        jdbc.update("UPDATE process_task SET entity_data_id='record' WHERE task_id='add-sign'");
        engine.getRuntimeService().deleteProcessInstance(child.getProcessInstanceId(),"withdraw"); assertEquals(0,visible("outsider"));
    }

    @Test void lightweightSummaryReadsCustomNameAndLongTextWithoutLoadingAggregate() {
        jdbc.execute("ALTER TABLE inbox_business ADD COLUMN deleted INT DEFAULT 0");
        jdbc.execute("ALTER TABLE inbox_business MODIFY COLUMN data_name LONGTEXT");
        String large = "完整名称".repeat(2000);
        jdbc.update("UPDATE inbox_business SET data_name=?",large);
        var mapper = session.getMapper(com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper.class);
        var definitions = mock(com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper.class);
        when(definitions.findByEntityCode("invoice")).thenReturn(Optional.of(new com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition()));
        var tables = mock(com.workflow.entity.data.application.DynamicTableService.class);
        when(tables.getTableName("invoice")).thenReturn("inbox_business");
        var snapshots = mock(com.workflow.entity.definition.application.EntityPublishedSnapshotService.class);
        var snapshot = mock(com.workflow.entity.definition.application.model.EntityPublishedSnapshot.class);
        var name = new com.workflow.entity.definition.infrastructure.persistence.record.EntityField(); name.setFieldCode("name"); name.setDbColumnName("data_name");
        when(snapshot.getFields()).thenReturn(List.of(name)); when(snapshots.getLatestByEntityCode("invoice")).thenReturn(snapshot);
        var reader = new com.workflow.entity.data.application.EntityDataDynamicService(mapper,definitions,tables,null,null,null,null,null,snapshots);
        queryCount.set(0);
        var summary = reader.findTaskSummary("invoice","record");
        assertEquals(1,queryCount.get()); assertEquals("预算%_!",summary.name()); assertEquals(large,summary.dataName());
        jdbc.update("UPDATE inbox_business SET deleted=1"); assertEquals(EntityTaskSummary.empty(),reader.findTaskSummary("invoice","record"));
    }

    @Test void largeHistoryUsesThreeBoundedReadQueriesAndOneSummaryWrite() throws Exception {
        String task = start(); engine.getTaskService().claim(task,"alice"); engine.getTaskService().complete(task);
        var batches = new ArrayList<Object[]>();
        for(int i=0;i<1999;i++) batches.add(new Object[]{"history-"+i,task});
        transaction(() -> jdbc.batchUpdate("""
                INSERT INTO process_task (process_instance_id,process_definition_id,process_key,node_id,task_id,status,assignee_id,
                entity_code,entity_data_id,start_user_id,business_name,business_status,start_time,end_time,create_time,deleted,
                inbox_summary_ready,inbox_identity_ready,form_data)
                SELECT process_instance_id,process_definition_id,process_key,node_id,?,'done',assignee_id,entity_code,entity_data_id,
                start_user_id,business_name,business_status,start_time,end_time,create_time,0,1,1,REPEAT('x',2000)
                FROM process_task WHERE task_id=?
                """,batches));
        var timings = new ArrayList<Long>();
        for(int i=0;i<7;i++) {
            queryCount.set(0); long start=System.nanoTime(); var page=queries.findPage(query("alice","done")).orElseThrow();
            timings.add(System.nanoTime()-start); assertEquals(2000,page.getTotal()); assertEquals(10,page.getRecords().size()); assertEquals(3,queryCount.get());
        }
        timings.sort(Long::compareTo);
        if (mysqlAdmin != null) {
            var statement = session.getConfiguration().getMappedStatement(TaskInboxMapper.class.getName()+".selectPage");
            var params = Map.of("q",query("alice","done")); var bound = statement.getBoundSql(params);
            try(var connection = source.getConnection(); var explain = connection.prepareStatement("EXPLAIN " + bound.getSql())) {
                new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement,params,bound).setParameters(explain);
                try(var rows = explain.executeQuery()) { while(rows.next()) if("pt".equals(rows.getString("table")))
                    System.out.printf("INBOX_PLAN type=%s key=%s rows=%s extra=%s%n",rows.getString("type"),rows.getString("key"),rows.getString("rows"),rows.getString("Extra")); }
            }
        }
        long start=System.nanoTime();
        transaction(() -> { jdbc.update("UPDATE inbox_business SET name='更新摘要'"); summaryRefresh.changed("invoice","record"); });
        long updateNanos=System.nanoTime()-start;
        assertEquals(2000,jdbc.queryForObject("SELECT COUNT(*) FROM process_task WHERE business_name='更新摘要'",Integer.class));
        System.out.printf(Locale.ROOT,"INBOX_BENCH database=%s rows=2000 page=10 sql=3 median_ms=%.2f summary_update_ms=%.2f%n",mysqlAdmin==null?"H2":"MySQL",timings.get(3)/1e6,updateNanos/1e6);
    }

    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(type=org.apache.ibatis.executor.Executor.class, method="query",
            args={org.apache.ibatis.mapping.MappedStatement.class,Object.class,org.apache.ibatis.session.RowBounds.class,org.apache.ibatis.session.ResultHandler.class}))
    private static class QueryCounter implements org.apache.ibatis.plugin.Interceptor {
        private final java.util.concurrent.atomic.AtomicInteger count;
        QueryCounter(java.util.concurrent.atomic.AtomicInteger count) { this.count=count; }
        public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable { count.incrementAndGet(); return invocation.proceed(); }
    }

    private String start() {
        engine.getIdentityService().setAuthenticatedUserId("starter-id");
        try {
            var instance = engine.getRuntimeService().startProcessInstanceByKey("inbox", Map.of("entityCode", "invoice", "entityDataId", "record"));
            return engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult().getId();
        } finally { engine.getIdentityService().setAuthenticatedUserId(null); }
    }
    private long visible(String user) { return inbox.count(query(user, "todo")); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private TaskInboxQuery query(String user, String kind) { return new TaskInboxQuery(user,kind,1,10,null,null,null,null,null); }
    private void transaction(Runnable body) { new TransactionTemplate(transactions).executeWithoutResult(s -> body.run()); }
    private static void await(CyclicBarrier barrier) { try { barrier.await(10, TimeUnit.SECONDS); } catch(Exception e) { throw new RuntimeException(e); } }
    @SuppressWarnings("unchecked") private <T> T transactional(T service) {
        var advice = new TransactionInterceptor(); advice.setTransactionManager(transactions);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(service); factory.setProxyTargetClass(true); factory.addAdvice(advice); return (T) factory.getProxy();
    }

    /** 运行时夹具仅定义实际映射列；MySQL 迁移的旧表保护、索引及 DDL 重跑由独立迁移测试验证。 */
    private void createTables() throws Exception {
        if (mysqlAdmin == null) {
        var columns = new ArrayList<String>();
        for (var field : ProcessTask.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(TableField.class) && !field.getAnnotation(TableField.class).exist()) continue;
            String name = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            String type = field.getType() == String.class ? "VARCHAR(10000)" : field.getType() == LocalDateTime.class ? "TIMESTAMP" : "BIGINT";
            columns.add(name + " " + type + (name.equals("id") ? " AUTO_INCREMENT PRIMARY KEY" : name.equals("deleted") || name.startsWith("inbox_") ? " DEFAULT 0" : ""));
        }
        jdbc.execute("CREATE TABLE process_task (" + String.join(",", columns) + ",UNIQUE(task_id))");
        jdbc.execute("CREATE TABLE process_task_candidate_user(id VARCHAR(64) PRIMARY KEY,process_task_id BIGINT,user_id VARCHAR(64),sort_order INT,create_time TIMESTAMP,UNIQUE(process_task_id,user_id))");
        jdbc.execute("CREATE TABLE process_task_candidate_group(id VARCHAR(64) PRIMARY KEY,process_task_id BIGINT,group_code VARCHAR(64),sort_order INT,create_time TIMESTAMP,UNIQUE(process_task_id,group_code))");
                } else {
            // 在 MySQL 上使用原始建表和本次真实迁移，覆盖长度、默认值、唯一约束与索引。
            String baseline;
            try (var input = getClass().getResourceAsStream("/db/migration/V001__business_schema.sql")) {
                baseline = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            for (String table : List.of("process_task", "process_task_candidate_user", "process_task_candidate_group")) {
                var matcher = java.util.regex.Pattern.compile("CREATE TABLE `" + table + "` \\([\\s\\S]*?;").matcher(baseline);
                assertTrue(matcher.find()); jdbc.execute(matcher.group());
            }
            jdbc.execute("ALTER TABLE process_task ADD COLUMN sla_status VARCHAR(20), ADD COLUMN response_due_time DATETIME(6)");
            try(var connection = source.getConnection()) {
                new db.migration.V105__task_inbox_read_model().migrate(new org.flywaydb.core.api.migration.Context() {
                    public java.sql.Connection getConnection() { return connection; }
                    public org.flywaydb.core.api.configuration.Configuration getConfiguration() { return org.flywaydb.core.Flyway.configure(); }
                });
            }
        }
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(64) PRIMARY KEY,username VARCHAR(64) UNIQUE,nickname VARCHAR(64),deleted INT,status VARCHAR(1))");
        for (String name : List.of("alice", "bob", "carol", "outsider", "starter")) jdbc.update("INSERT INTO sys_user VALUES (?,?,?,0,'0')",name+"-id",name,name.equals("starter")?"发起人":name);
        jdbc.execute("CREATE TABLE sys_group(id VARCHAR(64),group_code VARCHAR(64),status VARCHAR(1),deleted INT)");
        jdbc.execute("CREATE TABLE sys_role(id VARCHAR(64),role_code VARCHAR(64),status VARCHAR(1),deleted INT)");
        jdbc.execute("CREATE TABLE sys_user_group(user_id VARCHAR(64),group_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_user_role(user_id VARCHAR(64),role_id VARCHAR(64))");
        jdbc.update("INSERT INTO sys_group VALUES ('group-id','reviewers','0',0)");
        jdbc.update("INSERT INTO sys_role VALUES ('role-id','auditors','0',0)");
        jdbc.update("INSERT INTO sys_user_group VALUES ('bob-id','group-id')");
        jdbc.update("INSERT INTO sys_user_role VALUES ('carol-id','role-id')");
        jdbc.execute("CREATE TABLE process_task_add_sign(id VARCHAR(64),source_task_id VARCHAR(64),status VARCHAR(32),process_instance_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE process_task_add_sign_user(add_sign_id VARCHAR(64),generated_task_id VARCHAR(64),status VARCHAR(32),user_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE inbox_business(id VARCHAR(64),name VARCHAR(255),code VARCHAR(255),data_name VARCHAR(255),current_task_name VARCHAR(255),status VARCHAR(64))");
        jdbc.update("INSERT INTO inbox_business VALUES ('record','预算%_!','CODE','自定义事项','审核','pending')");
    }
}
