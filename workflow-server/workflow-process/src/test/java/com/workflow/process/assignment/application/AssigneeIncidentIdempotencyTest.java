package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.api.request.AssigneeIncidentHandleRequest;
import com.workflow.process.assignment.infrastructure.persistence.mapper.AssigneeIncidentMapper;
import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import java.util.List;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 事件创建与自动重试请求号仍保持幂等，数据库执行改由 Mapper。 */
class AssigneeIncidentIdempotencyTest {
    private JdbcWriteAttempt attempt() {
        return new JdbcWriteAttempt(new JdbcTemplate(), DatabaseDialects.insert(DatabaseVendor.MYSQL));
    }

    @Test
    void duplicateOpenIncidentReturnsExistingIdWithoutInsert() {
        var mapper = mock(AssigneeIncidentMapper.class);
        when(mapper.selectIds(any())).thenReturn(List.of("existing-incident"));
        var recorder = new AssigneeIncidentRecorder(mapper, new ObjectMapper(), DatabaseQueryDialects.forDatabaseId("MYSQL"), attempt());
        String id = recorder.create(new AssigneeIncidentRecorder.CreateCommand(
                "config-1", "definition-1", "instance-1", "task-1", "approve", "审批",
                "CREATE_INCIDENT", "OPEN", "EMPTY", "无办理人", "resolver-1", Map.of(), "", "", "ops", 0, 30, 2, null, Map.of()));
        assertEquals("existing-incident", id);
        verify(mapper, never()).execute(any());
    }

    @Test
    void duplicateAutomaticRetryRequestDoesNotRunResolverAgain() {
        var mapper = mock(AssigneeIncidentMapper.class);
        var row = new org.springframework.util.LinkedCaseInsensitiveMap<Object>();
        row.put("id", "incident-1"); row.put("status", "RETRY_SCHEDULED");
        row.put("retry_count", 0); row.put("max_retries", 3);
        row.put("initial_delay_seconds", 30); row.put("backoff_multiplier", 2D);
        when(mapper.selectRows(any())).thenAnswer(call -> {
            com.workflow.core.database.mybatis.ParameterizedSql sql = call.getArgument(0);
            return sql.sql().contains("FROM process_assignee_incident WHERE id") ? List.of(row) : List.of();
        });
        when(mapper.execute(any())).thenThrow(new DuplicateKeyException("duplicate request id"));
        var taskService = mock(TaskService.class);
        var runtimeService = mock(RuntimeService.class);
        var resolutionService = mock(AssigneeResolutionService.class);
        var service = new AssigneeIncidentService(mapper, new ObjectMapper(), taskService, runtimeService,
                resolutionService, DatabaseQueryDialects.forDatabaseId("MYSQL"), attempt());
        var request = new AssigneeIncidentHandleRequest();
        request.setRequestId("AUTO:incident-1:1"); request.setAction("RETRY_RESOLVER");
        assertEquals("incident-1", service.handle("incident-1", request).get("id"));
        verify(mapper, times(1)).execute(any());
        verifyNoInteractions(taskService, runtimeService, resolutionService);
    }
}
