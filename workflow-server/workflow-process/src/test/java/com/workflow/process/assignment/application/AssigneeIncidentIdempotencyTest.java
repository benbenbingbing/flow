package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 事件创建与自动重试请求号的幂等性测试。 */
class AssigneeIncidentIdempotencyTest {

    @Test
    void duplicateOpenIncidentReturnsExistingIdWithoutInsert() {
        ExistingIncidentJdbcTemplate jdbcTemplate =
                new ExistingIncidentJdbcTemplate();
        AssigneeIncidentRecorder recorder = new AssigneeIncidentRecorder(
                jdbcTemplate, new ObjectMapper());

        String id = recorder.create(new AssigneeIncidentRecorder.CreateCommand(
                "config-1", "definition-1", "instance-1", "task-1",
                "approve", "审批", "CREATE_INCIDENT", "OPEN",
                "EMPTY", "无办理人", "resolver-1", Map.of(), "", "",
                "ops", 0, 30, 2, null, Map.of()));

        assertEquals("existing-incident", id);
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void duplicateAutomaticRetryRequestDoesNotRunResolverAgain()
            throws SQLException {
        ResultSet row = incidentRow();
        DuplicateActionJdbcTemplate jdbcTemplate =
                new DuplicateActionJdbcTemplate(row);
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        AssigneeResolutionService resolutionService =
                mock(AssigneeResolutionService.class);
        AssigneeIncidentService service = new AssigneeIncidentService(
                jdbcTemplate, new ObjectMapper(), taskService,
                runtimeService, resolutionService);
        AssigneeIncidentHandleRequest request =
                new AssigneeIncidentHandleRequest();
        request.setRequestId("AUTO:incident-1:1");
        request.setAction("RETRY_RESOLVER");
        request.setReason("系统自动重试空办理人解析");

        Map<String, Object> result = service.handle("incident-1", request);

        assertEquals("incident-1", result.get("id"));
        assertEquals(1, jdbcTemplate.duplicateInsertAttempts);
        verifyNoInteractions(taskService, runtimeService, resolutionService);
    }

    private ResultSet incidentRow() throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("id")).thenReturn("incident-1");
        when(row.getString("process_config_id")).thenReturn("config-1");
        when(row.getString("process_definition_id"))
                .thenReturn("definition-1");
        when(row.getString("process_instance_id"))
                .thenReturn("instance-1");
        when(row.getString("task_id")).thenReturn("task-1");
        when(row.getString("node_id")).thenReturn("approve");
        when(row.getString("node_name")).thenReturn("审批");
        when(row.getString("policy")).thenReturn("WAIT_AND_RETRY");
        when(row.getString("status")).thenReturn("RETRY_SCHEDULED");
        when(row.getString("resolver_code")).thenReturn("resolver-1");
        when(row.getString("responsibility_owner")).thenReturn("ops");
        when(row.getInt("retry_count")).thenReturn(0);
        when(row.getInt("max_retries")).thenReturn(3);
        when(row.getInt("initial_delay_seconds")).thenReturn(30);
        when(row.getDouble("backoff_multiplier")).thenReturn(2D);
        return row;
    }

    private static final class ExistingIncidentJdbcTemplate
            extends JdbcTemplate {
        private int updateCount;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(
                String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) List.of("existing-incident");
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            throw new AssertionError("已有开放事件时不应再次写入");
        }
    }

    private static final class DuplicateActionJdbcTemplate
            extends JdbcTemplate {
        private final ResultSet incidentRow;
        private int duplicateInsertAttempts;

        private DuplicateActionJdbcTemplate(ResultSet incidentRow) {
            this.incidentRow = incidentRow;
        }

        @Override
        public <T> List<T> query(
                String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("FROM process_assignee_incident WHERE id")) {
                try {
                    return List.of(rowMapper.mapRow(incidentRow, 0));
                } catch (SQLException error) {
                    throw new IllegalStateException(error);
                }
            }
            if (sql.contains("FROM process_assignee_incident_action")) {
                return List.of();
            }
            throw new AssertionError("非预期查询：" + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("INSERT INTO process_assignee_incident_action")) {
                duplicateInsertAttempts++;
                throw new DuplicateKeyException("duplicate request id");
            }
            throw new AssertionError("重复请求不应执行状态更新：" + sql);
        }
    }
}
