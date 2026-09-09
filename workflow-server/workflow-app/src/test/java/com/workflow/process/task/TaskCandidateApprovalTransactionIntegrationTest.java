package com.workflow.process.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.application.MultiInstanceOutcomeService;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 验证候选人直接审批的真实事务和并发边界。
 * Spring Flowable 与业务 JDBC 共用事务管理器，TaskActionService 经过读取实际
 * {@code @Transactional} 注解的 Spring 代理，不能用直接调用或 mock commit 替代。
 */
class TaskCandidateApprovalTransactionIntegrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private ProcessEngine engine;
    private TaskActionService taskActionService;
    private TaskIdentityAccessService taskIdentityAccessService;
    private String processInstanceId;
    private String taskId;
    private CompletionHook completionHook = () -> { };

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:approval_transaction_"
                + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
        configuration.setDataSource(dataSource);
        configuration.setTransactionManager(transactionManager);
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setAsyncExecutorActivate(false);
        engine = configuration.buildProcessEngine();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE approval_projection (task_id VARCHAR(64) PRIMARY KEY, "
                + "assignee VARCHAR(64), task_status VARCHAR(16) NOT NULL)");
        jdbc.execute("CREATE TABLE approval_claim_log (task_id VARCHAR(64), operator_name VARCHAR(64))");

        SysGroupMapper groupMapper = mock(SysGroupMapper.class);
        SysGroup reviewers = new SysGroup();
        reviewers.setId("review-group-id");
        reviewers.setGroupCode("reviewers");
        reviewers.setStatus("0");
        reviewers.setDeleted(0);
        when(groupMapper.selectGroupsByUserId("alice-id")).thenReturn(List.of(reviewers));
        when(groupMapper.selectGroupsByUserId("bob-id")).thenReturn(List.of(reviewers));
        taskIdentityAccessService = spy(new TaskIdentityAccessService(
                engine.getTaskService(), groupMapper, mock(SysRoleMapper.class),
                mock(IdentityDirectoryPort.class)));

        // 测试适配器保留真实 JDBC 副作用，与引擎共享数据源；省略实体表等无关依赖。
        // 如果引擎 claim/complete 提前提交，后续的回滚和外部连接快照断言会真实失败。
        ProcessTaskService projectionService = mock(ProcessTaskService.class);
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("UPDATE approval_projection SET assignee = ? WHERE task_id = ?",
                    invocation.getArgument(2, String.class), invocation.getArgument(0, String.class));
            return null;
        }).when(projectionService).synchronizeClaimedTask(anyString(), anyString(), anyString());
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("UPDATE approval_projection SET task_status = 'done' WHERE task_id = ?",
                    invocation.getArgument(0, String.class));
            completionHook.afterEngineCompletion();
            return null;
        }).when(projectionService).completeTask(anyString(), eq("approve"), anyString(), isNull());
        ProcessOperationLogMapper operationLogMapper = mock(ProcessOperationLogMapper.class);
        when(operationLogMapper.insert(any(ProcessOperationLog.class))).thenAnswer(invocation -> {
            ProcessOperationLog operation = invocation.getArgument(0);
            return jdbc.update("INSERT INTO approval_claim_log(task_id, operator_name) VALUES (?, ?)",
                    operation.getTaskId(), operation.getNewValue());
        });
        SysUserService userService = mock(SysUserService.class);
        when(userService.getDisplayName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        TaskActionService target = new TaskActionService(
                engine.getTaskService(), engine.getRuntimeService(), engine.getHistoryService(),
                projectionService, engine.getRepositoryService(), operationLogMapper, userService,
                mock(NodeFormSubmissionService.class), mock(EntityActionCapabilityService.class),
                mock(EntityRecordPort.class), mock(ProcessCcService.class),
                mock(NextApproverOverrideService.class),
                new MultiInstanceOutcomeService(engine.getRuntimeService(), engine.getRepositoryService(),
                        engine.getTaskService(), new ObjectMapper()),
                mock(NodeOperationCapabilityService.class), taskIdentityAccessService);
        TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
        transactionInterceptor.setTransactionManager(transactionManager);
        transactionInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(transactionInterceptor);
        taskActionService = (TaskActionService) proxyFactory.getProxy();

        engine.getRepositoryService().createDeployment().addString("candidate-review.bpmn20.xml", """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://workflow.test/process">
                  <bpmn:process id="candidate_review" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:userTask id="review" name="候选人审批" flowable:candidateGroups="reviewers" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="toReview" sourceRef="start" targetRef="review" />
                    <bpmn:sequenceFlow id="toEnd" sourceRef="review" targetRef="end" />
                  </bpmn:process>
                </bpmn:definitions>
                """).deploy();
        ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("candidate_review");
        processInstanceId = instance.getId();
        taskId = engine.getTaskService().createTaskQuery().processInstanceId(processInstanceId)
                .singleResult().getId();
        jdbc.update("INSERT INTO approval_projection(task_id, task_status) VALUES (?, 'todo')", taskId);
        UserContext.setCurrentUser("alice-id", "alice");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void checkingCandidateAccessDoesNotClaimOrWriteAnything() throws SQLException {
        DatabaseSnapshot before = snapshotOnIndependentConnection();

        taskActionService.requireTaskAccess(taskId);

        assertEquals(before, snapshotOnIndependentConnection());
        assertEquals(new DatabaseSnapshot(true, null, null, "todo", 0), before);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void directApprovalCommitsClaimCompletionProjectionAndAuditTogether() throws SQLException {
        completionHook = () -> {
            // 引擎已在当前事务中完成，本地状态也已变更；另一个真实数据库连接仍只看见待办。
            assertNull(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult());
            assertEquals("done", jdbc.queryForObject(
                    "SELECT task_status FROM approval_projection WHERE task_id = ?", String.class, taskId));
            assertEquals(new DatabaseSnapshot(true, null, null, "todo", 0), snapshotOnIndependentConnection());
        };

        approveAsCurrentUser();

        assertEquals(new DatabaseSnapshot(false, null, "alice", "done", 1), snapshotOnIndependentConnection());
        assertCompletedOnceBy("alice");
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void failureAfterEngineCompletionRollsBackClaimAndAllApprovalWrites() throws SQLException {
        completionHook = () -> {
            // 失败点刻意置于真实 Flowable complete 后，避免只测到尚未写引擎的前置异常。
            assertNull(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult());
            throw new InjectedApprovalFailure();
        };

        assertThrows(InjectedApprovalFailure.class, this::approveAsCurrentUser);

        assertEquals(new DatabaseSnapshot(true, null, null, "todo", 0), snapshotOnIndependentConnection());
        Task task = engine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
        assertNotNull(task);
        assertNull(task.getAssignee());
        assertEquals(1, engine.getTaskService().getIdentityLinksForTask(taskId).stream()
                .filter(link -> "candidate".equals(link.getType()) && "reviewers".equals(link.getGroupId()))
                .count());
        assertNull(engine.getRuntimeService().getVariable(processInstanceId, "approved"));
        assertNull(engine.getRuntimeService().getVariable(processInstanceId, "_approvers_"));
        assertEquals(0, engine.getHistoryService().createHistoricTaskInstanceQuery()
                .taskId(taskId).finished().count());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void concurrentCandidateSubmissionsCommitExactlyOneApproval() throws Exception {
        CyclicBarrier authorizedTogether = new CyclicBarrier(2);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            // 两人都读到未认领任务并通过实际候选校验后，才同时竞争数据库中的任务归属。
            authorizedTogether.await(10, TimeUnit.SECONDS);
            return null;
        }).when(taskIdentityAccessService).requireCurrentUserAccess(any(Task.class));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<ApprovalAttempt> results;
        try {
            Future<ApprovalAttempt> alice = executor.submit(() -> submitApproval("alice"));
            Future<ApprovalAttempt> bob = executor.submit(() -> submitApproval("bob"));
            results = List.of(alice.get(20, TimeUnit.SECONDS), bob.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(1, results.stream().filter(ApprovalAttempt::succeeded).count(), results.toString());
        ApprovalAttempt winner = results.stream().filter(ApprovalAttempt::succeeded).findFirst().orElseThrow();
        ApprovalAttempt loser = results.stream().filter(result -> !result.succeeded()).findFirst().orElseThrow();
        assertNotNull(loser.failure());
        assertTrue(isTaskStateConflict(loser.failure()),
                () -> "竞争失败必须来自任务状态冲突，不能由屏障超时或其他测试故障冒充: " + loser.failure());
        assertEquals(new DatabaseSnapshot(false, null, winner.username(), "done", 1), snapshotOnIndependentConnection());
        assertCompletedOnceBy(winner.username());
        assertEquals(winner.username(), jdbc.queryForObject(
                "SELECT operator_name FROM approval_claim_log WHERE task_id = ?", String.class, taskId));
    }

    @Test
    void anotherCandidateCannotSubmitAfterExplicitClaim() throws SQLException {
        taskActionService.claimTask(taskId);
        DatabaseSnapshot claimed = new DatabaseSnapshot(true, "alice", "alice", "todo", 1);
        assertEquals(claimed, snapshotOnIndependentConnection());
        UserContext.setCurrentUser("bob-id", "bob");

        BusinessConflictException conflict = assertThrows(BusinessConflictException.class, this::approveAsCurrentUser);
        assertEquals("TASK_ALREADY_CLAIMED", conflict.getErrorCode());

        assertEquals(claimed, snapshotOnIndependentConnection());
        assertEquals(0, engine.getHistoryService().createHistoricTaskInstanceQuery()
                .taskId(taskId).finished().count());
    }

    private void approveAsCurrentUser() {
        taskActionService.completeTask(taskId, UserContext.getUsername(), "approve", "同意", null, null);
    }

    /** 只接受认领竞争、乐观锁或对方已完成造成的任务消失，不接受任意运行时异常。 */
    private boolean isTaskStateConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof BusinessConflictException conflict) {
                return "TASK_ALREADY_CLAIMED".equals(conflict.getErrorCode())
                        || "TASK_ALREADY_COMPLETED".equals(conflict.getErrorCode());
            }
            if (cause instanceof FlowableOptimisticLockingException
                    || cause instanceof FlowableTaskAlreadyClaimedException
                    || cause instanceof FlowableObjectNotFoundException) {
                return true;
            }
        }
        return false;
    }

    private ApprovalAttempt submitApproval(String username) {
        UserContext.setCurrentUser(username + "-id", username);
        try {
            approveAsCurrentUser();
            return new ApprovalAttempt(username, true, null);
        } catch (RuntimeException failure) {
            return new ApprovalAttempt(username, false, failure);
        } finally {
            UserContext.clear();
        }
    }

    private void assertCompletedOnceBy(String username) {
        assertNull(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult());
        assertEquals(1, engine.getHistoryService().createHistoricTaskInstanceQuery()
                .taskId(taskId).taskAssignee(username).finished().count());
    }

    /**
     * 直接获取新连接，明确绕过 Spring 线程绑定连接，观测尚未提交数据对其他事务的可见性。
     * 不能复用当前 JdbcTemplate，否则会错误地把本事务内查询当成提交后的状态。
     */
    private DatabaseSnapshot snapshotOnIndependentConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean active;
            String engineAssignee = null;
            try (PreparedStatement statement = connection.prepareStatement("SELECT ASSIGNEE_ FROM ACT_RU_TASK WHERE ID_ = ?")) {
                statement.setString(1, taskId);
                try (ResultSet result = statement.executeQuery()) {
                    active = result.next();
                    if (active) {
                        engineAssignee = result.getString(1);
                    }
                }
            }
            String localAssignee;
            String localStatus;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT assignee, task_status FROM approval_projection WHERE task_id = ?")) {
                statement.setString(1, taskId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    localAssignee = result.getString(1);
                    localStatus = result.getString(2);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM approval_claim_log WHERE task_id = ?")) {
                statement.setString(1, taskId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    return new DatabaseSnapshot(active, engineAssignee, localAssignee, localStatus, result.getInt(1));
                }
            }
        }
    }

    @FunctionalInterface
    private interface CompletionHook {
        void afterEngineCompletion() throws Exception;
    }

    private static final class InjectedApprovalFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private record DatabaseSnapshot(boolean active, String engineAssignee, String localAssignee,
                                    String localStatus, int claimAuditCount) { }

    private record ApprovalAttempt(String username, boolean succeeded, RuntimeException failure) { }
}
