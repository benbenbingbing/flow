package com.workflow.process.task;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.*;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.adapter.EntityRecordMutationAdapter;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.validation.PublishedFormCrossFieldMutationValidator;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.mutation.application.*;
import com.workflow.entity.mutation.infrastructure.persistence.mapper.EntityMutationReceiptMapper;
import com.workflow.entity.mutation.infrastructure.persistence.record.EntityMutationReceipt;
import com.workflow.entity.version.application.*;
import com.workflow.integration.database.api.write.DatabaseInsertDialect;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.port.EntityTaskSummaryPort;
import com.workflow.contracts.identity.model.IdentityHandoverUser;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.sla.runtime.application.TaskSlaRuntimeService;
import com.workflow.process.task.api.request.TaskHandoverRequest;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskHandoverService;
import com.workflow.process.task.application.TaskInboxProjectionService;
import com.workflow.process.task.infrastructure.persistence.mapper.*;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 验证人员交接在真实 Spring/Flowable/MyBatis 共用事务中提交和回滚。
 * 引擎、人员交接查询、镜像与操作日志均使用实际数据库；目录和 SLA 仅隔离无关配置，
 * SLA 适配器仍写入同一 JDBC 事务，且明确禁止把交接当作首次响应或完成。
 */
class TaskHandoverTransactionIntegrationTest {
    private DriverManagerDataSource source;
    private DataSourceTransactionManager transactions;
    private JdbcTemplate jdbc;
    private ProcessEngine engine;
    private ProcessTaskMapper tasks;
    private TaskHandoverMapper handover;
    private TaskSlaRuntimeService sla;
    private TaskHandoverService service;
    private EntityDataDynamicService aggregateQuery;
    private EntityRecordMutationAdapter recordAdapter;
    private List<String> taskIds;
    private List<Snapshot> before;
    private final AtomicInteger targetNameReads = new AtomicInteger();
    private boolean observedUncommittedBatch;
    private static final LocalDateTime RESPONSE_DUE = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final LocalDateTime COMPLETION_DUE = LocalDateTime.of(2026, 9, 29, 18, 0);

    @BeforeEach
    void setup() throws Exception {
        source = new DriverManagerDataSource("jdbc:h2:mem:handover_transaction_" + UUID.randomUUID()
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        transactions = new DataSourceTransactionManager(source);
        var engineConfig = new SpringProcessEngineConfiguration();
        engineConfig.setDataSource(source);
        engineConfig.setTransactionManager(transactions);
        engineConfig.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        engineConfig.setAsyncExecutorActivate(false);
        engine = engineConfig.buildProcessEngine();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("SET MODE MySQL");
        createTables();

        var config = new MybatisConfiguration();
        config.setDatabaseId("POSTGRESQL");
        config.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper : List.of(ProcessTaskMapper.class, TaskHandoverMapper.class,
                TaskInboxProjectionMapper.class, ProcessTaskCandidateUserMapper.class,
                ProcessTaskCandidateGroupMapper.class, ProcessOperationLogMapper.class,
                EntityDataDynamicMapper.class, EntityMutationReceiptMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        tasks = session.getMapper(ProcessTaskMapper.class);
        handover = session.getMapper(TaskHandoverMapper.class);
        var projectionMapper = session.getMapper(TaskInboxProjectionMapper.class);

        var directory = mock(IdentityDirectoryPort.class);
        when(directory.findHandoverUser("alice-id")).thenReturn(Optional.of(
                new IdentityHandoverUser("alice-id", "alice", "原办理人", "1", true)));
        when(directory.lockHandoverUser("bob-id")).thenReturn(Optional.of(
                new IdentityHandoverUser("bob-id", "bob", "接收人", "0", false)));
        when(directory.getDisplayName(anyString())).thenAnswer(call -> {
            String user = call.getArgument(0);
            if ("bob".equals(user) && targetNameReads.incrementAndGet() == 2) {
                // 第二任务更新镜像前，首任务已真实变更引擎、镜像、SLA 和日志。
                // 新连接仍须看见原状态，否则即便最终回滚断言通过也可能隐藏过早提交。
                assertFirstTaskStagedButNotCommitted();
            }
            return user;
        });
        recordAdapter = currentTaskAdapter(session);
        var processTasks = transactional(new ProcessTaskService(tasks, engine.getTaskService(),
                engine.getRuntimeService(), engine.getRepositoryService(), null, null, null,
                new ObjectMapper(), recordAdapter, directory));
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("processTasks", processTasks);
        var inbox = transactional(new TaskInboxProjectionService(tasks,
                session.getMapper(ProcessTaskCandidateUserMapper.class),
                session.getMapper(ProcessTaskCandidateGroupMapper.class), projectionMapper,
                engine.getTaskService(), engine.getHistoryService(), directory,
                mock(EntityTaskSummaryPort.class), beans.getBeanProvider(ProcessTaskService.class),
                engine.getRuntimeService()));
        sla = mock(TaskSlaRuntimeService.class);
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return jdbc.update("UPDATE handover_sla SET assignee = ? WHERE task_id = ?",
                    call.getArgument(1, String.class), call.getArgument(0, String.class));
        }).when(sla).updateAssignee(anyString(), anyString());
        service = transactional(new TaskHandoverService(directory, handover, tasks,
                mock(ProcessTaskAddSignMapper.class), mock(ProcessTaskAddSignUserMapper.class),
                projectionMapper, inbox, engine.getTaskService(), processTasks, sla,
                session.getMapper(ProcessOperationLogMapper.class)));

        engine.getRepositoryService().createDeployment().addString("handover.bpmn20.xml", """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="handover-test">
                  <process id="handover" isExecutable="true">
                    <startEvent id="start"/><sequenceFlow id="to-review" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="部门审批" flowable:assignee="alice"/>
                    <sequenceFlow id="to-end" sourceRef="review" targetRef="end"/><endEvent id="end"/>
                  </process>
                </definitions>
                """).deploy();
        taskIds = List.of(startTask(), startTask()).stream().sorted().toList();
        before = independentSnapshots();
        UserContext.setCurrentUser("admin-id", "admin");
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        if (engine != null) engine.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void disabledDeletedSourceTransfersWholeBatchWithoutCompletingOrAcknowledgingTasks() throws Exception {
        // 同一份真实子表数据在普通读取时仍违反一对一关系，交接不能依赖它能成功展开。
        assertInvalidRelation();
        clearInvocations(aggregateQuery);
        assertEquals(2, service.transfer(request(true)));

        assertTrue(observedUncommittedBatch);
        assertEquals(0, handover.count("alice-id"));
        assertEquals(2, handover.count("bob-id"));
        List<Snapshot> after = independentSnapshots();
        for (int index = 0; index < taskIds.size(); index++) {
            String taskId = taskIds.get(index);
            Snapshot original = before.get(index);
            Snapshot transferred = after.get(index);
            assertEquals("bob", transferred.engineAssignee());
            assertEquals("bob", transferred.mirrorAssignee());
            assertEquals("bob", transferred.slaAssignee());
            assertEquals(original.mirrorId(), transferred.mirrorId(), "交接不能重建镜像任务");
            assertEquals("todo", transferred.taskStatus());
            assertNull(transferred.taskEnd());
            assertNull(transferred.taskAction());
            assertEquals("PENDING", transferred.responseStatus());
            assertEquals("PENDING", transferred.completionStatus());
            assertEquals("RUNNING", transferred.overallStatus());
            assertNull(transferred.respondedAt());
            assertNull(transferred.completedAt());
            assertEquals(RESPONSE_DUE, transferred.responseDue());
            assertEquals(COMPLETION_DUE, transferred.completionDue());
            assertEquals(35, transferred.responseRemainingMinutes());
            assertEquals(120, transferred.completionRemainingMinutes());
            assertEquals(1, transferred.logCount());
            assertEquals(0, engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(taskId).finished().count());
            assertNotNull(engine.getRuntimeService().createProcessInstanceQuery()
                    .processInstanceId(tasks.selectByTaskId(taskId).getProcessInstanceId()).singleResult());
            verify(sla).updateAssignee(taskId, "bob");
            assertEquals("bob", jdbc.queryForObject(
                    "SELECT current_task_assignee FROM wf_handover_entity WHERE id=?", String.class, taskId));
        }
        verifyNoInteractions(aggregateQuery);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM entity_mutation_receipt WHERE status='SUCCESS'", Integer.class));
        assertInvalidRelation();
        verifyNoMoreInteractions(sla);
        assertEquals(List.of("TRANSFER", "TRANSFER"), jdbc.queryForList(
                "SELECT operation_type FROM process_operation_log ORDER BY task_id", String.class));
        assertEquals(List.of("admin-id", "admin-id"), jdbc.queryForList(
                "SELECT operator_id FROM process_operation_log ORDER BY task_id", String.class));
        assertEquals(List.of("人员交接：离职交接", "人员交接：离职交接"), jdbc.queryForList(
                "SELECT operation_comment FROM process_operation_log ORDER BY task_id", String.class));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void secondMirrorWriteFailureRollsBackFirstTaskAndBothEngineAssignments() throws Exception {
        // 实际数据库约束拒绝第二条镜像的新办理人，失败发生于 Flowable setAssignee 之后。
        jdbc.execute("ALTER TABLE process_task ADD CONSTRAINT reject_second_handover CHECK "
                + "(task_id <> '" + taskIds.get(1) + "' OR assignee_id <> 'bob')");

        assertThrows(RuntimeException.class, () -> service.transfer(request(false)));

        assertTrue(observedUncommittedBatch);
        assertWholeBatchRolledBack();
        verify(sla).updateAssignee(taskIds.get(0), "bob");
        verifyNoMoreInteractions(sla);
    }

    @Test
    void secondAuditInsertFailureRollsBackAllTaskProjectionSlaAndAuditWrites() throws Exception {
        // 第二条日志由真实 MyBatis INSERT 触发约束失败，此时两条任务均已修改引擎和投影。
        jdbc.execute("ALTER TABLE process_operation_log ADD CONSTRAINT reject_second_audit CHECK "
                + "(task_id <> '" + taskIds.get(1) + "')");

        assertThrows(RuntimeException.class, () -> service.transfer(request(false)));

        assertTrue(observedUncommittedBatch);
        assertWholeBatchRolledBack();
        for (String taskId : taskIds) verify(sla).updateAssignee(taskId, "bob");
        verifyNoMoreInteractions(sla);
    }

    @Test
    void returningToPreviousAssigneeUpdatesSummaryDespiteHistoricalReceipt() {
        String taskId = taskIds.get(0);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        for (String assignee : List.of("bob", "alice", "bob")) {
            transaction.executeWithoutResult(status -> recordAdapter.updateCurrentTask(
                    "handover_entity", taskId, taskId, "部门审批", assignee));
            assertEquals(assignee, jdbc.queryForObject(
                    "SELECT current_task_assignee FROM wf_handover_entity WHERE id=?", String.class, taskId));
        }
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM entity_mutation_receipt WHERE status='SUCCESS'", Integer.class));
    }

    @Test
    void summaryWriteFailureRollsBackEngineTasksSlaLogsAndReceipts() throws Exception {
        jdbc.execute("ALTER TABLE wf_handover_entity ADD CONSTRAINT reject_summary CHECK "
                + "(id <> '" + taskIds.get(1) + "' OR current_task_assignee <> 'bob')");
        assertThrows(RuntimeException.class, () -> service.transfer(request(true)));
        assertWholeBatchRolledBack();
    }

    /** 保留真实关系读取规则和脏子表，防止测试仅用 mock 假定普通读取会失败。 */
    private void assertInvalidRelation() {
        var failure = assertThrows(BusinessConflictException.class,
                () -> aggregateQuery.findById("handover_entity", taskIds.get(0)));
        assertEquals("ENTITY_VERSION_RELATION_CARDINALITY_VIOLATION", failure.getErrorCode());
    }

    /** 实体摘要写入、幂等回执与流程引擎共享同一数据源和事务。 */
    private EntityRecordMutationAdapter currentTaskAdapter(SqlSessionTemplate session) {
        var mapper = session.getMapper(EntityDataDynamicMapper.class);
        var tables = mock(DynamicTableService.class);
        when(tables.getTableName("handover_entity")).thenReturn("wf_handover_entity");
        when(tables.getTableName("handover_child")).thenReturn("wf_handover_child");
        when(tables.tableExists("handover_child")).thenReturn(true);
        var definitions = mock(EntityDefinitionMapper.class);
        var parent = new EntityDefinition(); parent.setId("parent"); parent.setEntityCode("handover_entity");
        var child = new EntityDefinition(); child.setId("child"); child.setEntityCode("handover_child");
        when(definitions.findByEntityCode("handover_entity")).thenReturn(Optional.of(parent));
        when(definitions.findByEntityCode("handover_child")).thenReturn(Optional.of(child));
        var relationMapper = mock(EntityRelationMapper.class);
        var relation = new EntityRelation(); relation.setRelationName("一对一子表");
        relation.setRelationCode("detail"); relation.setDataKey("detail");
        relation.setParentEntityCode("handover_entity"); relation.setChildEntityCode("handover_child");
        relation.setChildRefFieldCode("parent_id"); relation.setRelationType(EntityRelation.RelationType.ONE_TO_ONE);
        when(relationMapper.selectByParentEntityId("parent")).thenReturn(List.of(relation));
        var json = new ObjectMapper();
        var relations = new EntityRelationRuntimeService(mapper, definitions, mock(EntityFieldMapper.class),
                relationMapper, tables, json, new EntityRuntimeRecordMapper(json), null);
        aggregateQuery = mock(EntityDataDynamicService.class);
        when(aggregateQuery.findById(org.mockito.ArgumentMatchers.eq("handover_entity"), anyString()))
                .thenAnswer(call -> {
                    var dto = new EntityDataDTO(); dto.setEntityCode("handover_entity"); dto.setId(call.getArgument(1));
                    relations.loadRelationData(dto);
                    return dto;
                });
        var mutations = new EntityDataMutationService(mapper, definitions, null, tables,
                null, null, relations, null, null, null, null, null, null, null);
        var writer = new EntityAggregateWriter(aggregateQuery, mutations, tables, mapper, relations, json);
        var receipts = new EntityMutationReceiptService(session.getMapper(EntityMutationReceiptMapper.class), json,
                new JdbcWriteAttempt(jdbc, mock(DatabaseInsertDialect.class)));
        var executor = transactional(new EntityMutationTransactionExecutor(writer, aggregateQuery,
                mock(EntityVersionPolicyMatcher.class), mock(EntityRecordVersionService.class),
                mock(EntityRelatedVersionCaptureService.class), receipts, mock(EntityFormUniqueClaimService.class),
                mock(PublishedFormCrossFieldMutationValidator.class), json));
        return new EntityRecordMutationAdapter(new EntityMutationPipeline(executor, mock(SystemAuditPort.class)),
                null, null, null);
    }

    private TaskHandoverRequest request(boolean all) {
        return new TaskHandoverRequest("alice-id", "bob-id", all ? List.of() : taskIds, all, " 离职交接 ");
    }

    private void assertFirstTaskStagedButNotCommitted() throws SQLException {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        String first = taskIds.get(0);
        assertEquals("bob", engine.getTaskService().createTaskQuery().taskId(first).singleResult().getAssignee());
        assertEquals("bob", jdbc.queryForObject("SELECT assignee_id FROM process_task WHERE task_id=?", String.class, first));
        assertEquals("bob", jdbc.queryForObject("SELECT assignee FROM handover_sla WHERE task_id=?", String.class, first));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM process_operation_log", Integer.class));
        assertEquals(before, independentSnapshots());
        observedUncommittedBatch = true;
    }

    private void assertWholeBatchRolledBack() throws SQLException {
        assertEquals(before, independentSnapshots());
        assertEquals(2, handover.count("alice-id"));
        assertEquals(0, handover.count("bob-id"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM process_operation_log", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM entity_mutation_receipt", Integer.class));
        for (String taskId : taskIds) {
            assertEquals("alice", jdbc.queryForObject(
                    "SELECT current_task_assignee FROM wf_handover_entity WHERE id=?", String.class, taskId));
            assertEquals("alice", engine.getTaskService().createTaskQuery().taskId(taskId).singleResult().getAssignee());
            assertEquals(0, engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(taskId).finished().count());
        }
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    /** 直接打开独立连接，避免 JDBC 线程绑定连接把本事务未提交内容当作外界可见状态。 */
    private List<Snapshot> independentSnapshots() throws SQLException {
        List<Snapshot> result = new ArrayList<>();
        try (Connection connection = source.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT ft.ASSIGNEE_, pt.id, pt.assignee_id, pt.status, pt.end_time, pt.action,
                    s.assignee, s.response_status, s.completion_status, s.overall_status,
                    s.responded_at, s.completed_at, s.response_due, s.completion_due,
                    s.response_remaining_minutes, s.completion_remaining_minutes,
                    (SELECT COUNT(*) FROM process_operation_log log WHERE log.task_id = pt.task_id)
                FROM ACT_RU_TASK ft JOIN process_task pt ON pt.task_id = ft.ID_
                  JOIN handover_sla s ON s.task_id = pt.task_id WHERE pt.task_id = ?
                """)) {
            for (String taskId : taskIds) {
                statement.setString(1, taskId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    result.add(new Snapshot(rows.getString(1), rows.getLong(2), rows.getString(3), rows.getString(4),
                            rows.getObject(5, LocalDateTime.class), rows.getString(6), rows.getString(7),
                            rows.getString(8), rows.getString(9), rows.getString(10),
                            rows.getObject(11, LocalDateTime.class), rows.getObject(12, LocalDateTime.class),
                            rows.getObject(13, LocalDateTime.class), rows.getObject(14, LocalDateTime.class),
                            rows.getInt(15), rows.getInt(16), rows.getInt(17)));
                }
            }
        }
        return result;
    }

    private String startTask() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("handover");
        Task task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        engine.getRuntimeService().setVariables(instance.getId(), Map.of(
                "entityCode", "handover_entity", "entityDataId", task.getId()));
        jdbc.update("INSERT INTO wf_handover_entity(id,current_task_id,current_task_name,current_task_assignee) VALUES (?,?,?,'alice')",
                task.getId(), task.getId(), task.getName());
        jdbc.update("INSERT INTO wf_handover_child(id,parent_id) VALUES (?,?),(?,?)",
                UUID.randomUUID().toString(), task.getId(), UUID.randomUUID().toString(), task.getId());
        ProcessTask mirror = new ProcessTask();
        mirror.setTaskId(task.getId()); mirror.setNodeId(task.getTaskDefinitionKey()); mirror.setNodeName(task.getName());
        mirror.setProcessInstanceId(instance.getId()); mirror.setProcessDefinitionId(instance.getProcessDefinitionId());
        mirror.setProcessName("人员交接测试"); mirror.setAssigneeId("alice"); mirror.setAssigneeName("原办理人");
        mirror.setAssigneeType("user"); mirror.setStatus("todo"); mirror.setDeleted(0);
        mirror.setInboxIdentityReady(true); mirror.setInboxSummaryReady(true); mirror.setCreateTime(LocalDateTime.now());
        tasks.insert(mirror);
        jdbc.update("""
                INSERT INTO handover_sla (task_id,assignee,response_status,completion_status,overall_status,
                    response_due,completion_due,response_remaining_minutes,completion_remaining_minutes)
                VALUES (?,'alice','PENDING','PENDING','RUNNING',?,?,35,120)
                """, task.getId(), RESPONSE_DUE, COMPLETION_DUE);
        return task.getId();
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

    private void createTables() {
        createEntityTable(EntityMutationReceipt.class);
        jdbc.execute("CREATE UNIQUE INDEX handover_receipt_key ON entity_mutation_receipt(idempotency_key)");
        jdbc.execute("CREATE TABLE wf_handover_entity(id VARCHAR(64) PRIMARY KEY, current_task_id VARCHAR(64),"
                + "current_task_name VARCHAR(255),current_task_assignee VARCHAR(64),update_time TIMESTAMP,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE wf_handover_child(id VARCHAR(64) PRIMARY KEY,parent_id VARCHAR(64),deleted INT DEFAULT 0,create_time TIMESTAMP)");
        createEntityTable(ProcessTask.class);
        createEntityTable(ProcessOperationLog.class);
        jdbc.execute("CREATE UNIQUE INDEX unique_handover_task ON process_task(task_id)");
        jdbc.execute("CREATE TABLE process_task_candidate_user(id VARCHAR(64) PRIMARY KEY,process_task_id BIGINT,user_id VARCHAR(64),sort_order INT,create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE process_task_candidate_group(id VARCHAR(64) PRIMARY KEY,process_task_id BIGINT,group_code VARCHAR(64),sort_order INT,create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(64) PRIMARY KEY,username VARCHAR(64),status VARCHAR(1),deleted INT)");
        jdbc.update("INSERT INTO sys_user VALUES ('alice-id','alice','1',1),('bob-id','bob','0',0)");
        jdbc.execute("CREATE TABLE sys_group(id VARCHAR(64),group_code VARCHAR(64),status VARCHAR(1),deleted INT)");
        jdbc.execute("CREATE TABLE sys_role(id VARCHAR(64),role_code VARCHAR(64),status VARCHAR(1),deleted INT)");
        jdbc.execute("CREATE TABLE sys_user_group(user_id VARCHAR(64),group_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_user_role(user_id VARCHAR(64),role_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE process_task_add_sign(id VARCHAR(64),source_task_id VARCHAR(64),status VARCHAR(32),process_instance_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE process_task_add_sign_user(add_sign_id VARCHAR(64),generated_task_id VARCHAR(64),status VARCHAR(32),user_id VARCHAR(64))");
        jdbc.execute("""
                CREATE TABLE handover_sla(task_id VARCHAR(64) PRIMARY KEY,assignee VARCHAR(64),
                    response_status VARCHAR(32),completion_status VARCHAR(32),overall_status VARCHAR(32),
                    responded_at TIMESTAMP,completed_at TIMESTAMP,response_due TIMESTAMP,completion_due TIMESTAMP,
                    response_remaining_minutes INT,completion_remaining_minutes INT)
                """);
    }

    /** 按实际实体映射创建测试列；生产 DDL 与迁移约束由迁移测试负责，此处专测事务副作用。 */
    private void createEntityTable(Class<?> entity) {
        var columns = new ArrayList<String>();
        for (var field : entity.getDeclaredFields()) {
            TableField mapping = field.getAnnotation(TableField.class);
            if (Modifier.isStatic(field.getModifiers()) || mapping != null && !mapping.exist()) continue;
            String name = mapping != null && !mapping.value().isBlank() ? mapping.value()
                    : field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            String type = field.getType() == String.class ? "VARCHAR(10000)"
                    : field.getType() == LocalDateTime.class ? "TIMESTAMP" : "BIGINT";
            String extra = "id".equals(name)
                    ? (field.getType() == Long.class ? " AUTO_INCREMENT PRIMARY KEY" : " PRIMARY KEY")
                    : "deleted".equals(name) || name.startsWith("inbox_") ? " DEFAULT 0" : "";
            columns.add(name + " " + type + extra);
        }
        jdbc.execute("CREATE TABLE " + entity.getAnnotation(TableName.class).value() + " (" + String.join(",", columns) + ")");
    }

    private record Snapshot(String engineAssignee, long mirrorId, String mirrorAssignee, String taskStatus,
                            LocalDateTime taskEnd, String taskAction, String slaAssignee, String responseStatus,
                            String completionStatus, String overallStatus, LocalDateTime respondedAt,
                            LocalDateTime completedAt, LocalDateTime responseDue, LocalDateTime completionDue,
                            int responseRemainingMinutes, int completionRemainingMinutes, int logCount) { }
}
