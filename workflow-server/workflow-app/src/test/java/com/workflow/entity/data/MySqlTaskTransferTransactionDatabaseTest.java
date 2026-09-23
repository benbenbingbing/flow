package com.workflow.entity.data;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.application.MultiInstanceOutcomeService;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实 Spring 事务和 MySQL 故障验证转办原子性。Flowable 依赖只模拟同连接的数据写入，
 * 操作日志使用真实 Mapper；不据此宣称已运行其它数据库或真实 Flowable 引擎。
 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlTaskTransferTransactionDatabaseTest {
    @Test
    void failedNewTodoRollsBackEngineAndOriginalTodoWithoutWritingAudit() {
        try (var fixture = new TransferFixture(true, false)) {
            assertThrows(DataAccessException.class, fixture::transfer);
            fixture.assertOriginalState();
            assertEquals(0, fixture.h.jdbc.queryForObject("SELECT COUNT(*) FROM process_operation_log", Integer.class));
            verify(fixture.processTasks, never()).syncTasksFromFlowable(anyString());
        }
    }

    @Test
    void failedAuditRollsBackAllTransferWrites() {
        try (var fixture = new TransferFixture(false, true)) {
            assertThrows(DataAccessException.class, fixture::transfer);
            fixture.assertOriginalState();
            assertEquals(0, fixture.h.jdbc.queryForObject("SELECT COUNT(*) FROM process_operation_log", Integer.class));
            verify(fixture.processTasks, never()).syncTasksFromFlowable(anyString());
        }
    }

    @Test
    void missingTransferredTaskFailsAndRestoresPreviousAssignment() {
        try (var fixture = new TransferFixture(false, false)) {
            when(fixture.query.singleResult()).thenReturn(fixture.task, (Task) null);
            assertThrows(BusinessConflictException.class, fixture::transfer);
            fixture.assertOriginalState();
            assertEquals(0, fixture.h.jdbc.queryForObject("SELECT COUNT(*) FROM process_operation_log", Integer.class));
        }
    }

    @Test
    void successfulTransferCommitsAssignmentTodoAndAuditTogether() {
        try (var fixture = new TransferFixture(false, false)) {
            fixture.transfer();
            assertEquals("bob", fixture.observer.queryForObject(
                    "SELECT assignee FROM " + fixture.stateTable + " WHERE id='source'", String.class));
            assertEquals("transfer", fixture.h.jdbc.queryForObject(
                    "SELECT status FROM task_transfer_probe WHERE id='source'", String.class));
            assertEquals("todo", fixture.h.jdbc.queryForObject(
                    "SELECT status FROM task_transfer_probe WHERE id='new'", String.class));
            assertEquals("alice", fixture.h.jdbc.queryForObject("SELECT old_value FROM process_operation_log", String.class));
            assertEquals("bob", fixture.h.jdbc.queryForObject("SELECT new_value FROM process_operation_log", String.class));
            assertEquals("TRANSFER", fixture.h.jdbc.queryForObject("SELECT operation_type FROM process_operation_log", String.class));
            verify(fixture.processTasks).syncTasksFromFlowable("process");
        }
    }

    private static final class TransferFixture implements AutoCloseable {
        private final MySqlRuntimePaginationDatabaseTest.Fixture database =
                new MySqlRuntimePaginationDatabaseTest.Fixture();
        private final MySqlWriteAttemptDatabaseTest.Harness h;
        private final JdbcTemplate observer;
        private final String stateTable;
        private final ProcessTaskService processTasks = mock(ProcessTaskService.class);
        private final Task task = mock(Task.class);
        private final TaskQuery query = mock(TaskQuery.class);
        private final TaskActionService actions;

        /** 在新待办或审计处注入真实 SQL 约束错误，先前写入必须由外层事务一起撤销。 */
        private TransferFixture(boolean failTodo, boolean failAudit) {
            stateTable = database.table("task_transfer_probe",
                    "id VARCHAR(64) PRIMARY KEY,assignee VARCHAR(64),status VARCHAR(32)");
            database.table("process_operation_log", "id VARCHAR(64) PRIMARY KEY,process_instance_id VARCHAR(64),"
                    + "task_id VARCHAR(64),operation_type VARCHAR(32),operator_id VARCHAR(64),operator_name VARCHAR(100),"
                    + "operation_time DATETIME(6),operation_comment TEXT,old_value TEXT,new_value TEXT,"
                    + "old_value_format VARCHAR(32),new_value_format VARCHAR(32),ip_address VARCHAR(100),"
                    + "user_agent TEXT,create_time DATETIME(6)"
                    + (failAudit ? ",CONSTRAINT transfer_audit_" + database.suffix
                            + " CHECK (operation_type <> 'TRANSFER')" : ""));
            h = new MySqlWriteAttemptDatabaseTest.Harness(database, ProcessOperationLogMapper.class);
            observer = new JdbcTemplate(database.source);
            h.jdbc.update("INSERT INTO task_transfer_probe VALUES ('source','alice','todo')");
            var tasks = mock(TaskService.class);
            var runtime = mock(RuntimeService.class);
            var outcomes = mock(MultiInstanceOutcomeService.class);
            when(tasks.createTaskQuery()).thenReturn(query);
            when(query.taskId("task")).thenReturn(query);
            when(query.singleResult()).thenReturn(task);
            when(task.getId()).thenReturn("task");
            when(task.getAssignee()).thenReturn("alice");
            when(task.getProcessInstanceId()).thenReturn("process");
            when(outcomes.normalizeAction(anyString())).thenReturn("transfer");
            when(runtime.getVariables("process")).thenReturn(Map.of());
            doAnswer(call -> {
                h.jdbc.update("UPDATE task_transfer_probe SET assignee=? WHERE id='source'", (String) call.getArgument(1));
                return null;
            }).when(tasks).setAssignee("task", "bob");
            doAnswer(call -> {
                h.jdbc.update("UPDATE task_transfer_probe SET status='transfer' WHERE id='source'");
                return null;
            }).when(processTasks).completeTask(eq("task"), eq("transfer"), anyString());
            when(processTasks.createTask(any(Task.class), anyMap())).thenAnswer(call -> {
                h.jdbc.update("INSERT INTO task_transfer_probe VALUES (?,'bob','todo')", failTodo ? "source" : "new");
                return new ProcessTask();
            });
            actions = h.transactional(new TaskActionService(tasks, runtime, mock(HistoryService.class), processTasks,
                    mock(RepositoryService.class), h.mapper(ProcessOperationLogMapper.class), mock(SysUserService.class),
                    mock(NodeFormSubmissionService.class), mock(EntityRecordPort.class), mock(ProcessCcService.class),
                    mock(NextApproverOverrideService.class), outcomes, mock(NodeOperationCapabilityService.class),
                    mock(TaskIdentityAccessService.class)));
            UserContext.setCurrentUser("alice", "alice");
        }

        private void transfer() {
            actions.completeTask("task", "alice", "transfer", "handover", "bob", "转办");
        }

        private void assertOriginalState() {
            // 使用独立连接检查最终提交状态，避免原连接缓存或未提交视图掩盖部分成功。
            assertEquals("alice", observer.queryForObject("SELECT assignee FROM " + stateTable + " WHERE id='source'", String.class));
            assertEquals("todo", observer.queryForObject("SELECT status FROM " + stateTable + " WHERE id='source'", String.class));
            assertEquals(1, observer.queryForObject("SELECT COUNT(*) FROM " + stateTable, Integer.class));
        }

        @Override public void close() {
            UserContext.clear();
            database.close();
        }
    }
}
