package com.workflow.process.sla.runtime.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.cc.application.ProcessCcNotificationPublisher;
import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaEventMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.task.application.operation.NodeOperationPolicy;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SLA 自动动作与节点三开关的协作测试。
 */
class TaskSlaEventProcessorTest {

    @Test
    void automaticTransferStopsBeforeChangingAssigneeWhenSwitchDenies()
            throws Exception {
        ProcessTaskSlaEventMapper eventMapper =
                mock(ProcessTaskSlaEventMapper.class);
        ProcessTaskSlaMapper slaMapper =
                mock(ProcessTaskSlaMapper.class);
        org.flowable.engine.TaskService flowableTaskService =
                mock(org.flowable.engine.TaskService.class);
        ProcessTaskService processTaskService =
                mock(ProcessTaskService.class);
        NodeOperationCapabilityService capabilityService =
                mock(NodeOperationCapabilityService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        ProcessTaskSlaEvent event = new ProcessTaskSlaEvent();
        event.setId("event-1");
        event.setTaskId("task-1");
        event.setLeaseToken(7L);
        event.setActionType("TRANSFER");
        event.setMetricType("COMPLETION");
        event.setAttempts(0);
        event.setMaxRetries(5);
        String target = objectMapper.writeValueAsString(
                Map.of("userId", "user-2"));
        event.setActionConfigSnapshot(objectMapper.writeValueAsString(
                Map.of("targetConfigJson", target)));
        when(eventMapper.selectClaimed("event-1", "worker-1"))
                .thenReturn(event);

        ProcessTaskSla sla = new ProcessTaskSla();
        sla.setOverallStatus("ACTIVE");
        sla.setCompletionStatus("PENDING");
        when(slaMapper.findByTaskId("task-1")).thenReturn(sla);

        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        doThrow(new ForbiddenException("当前节点不允许转办"))
                .when(capabilityService)
                .requireConfiguredAllowed(
                        "task-1",
                        NodeOperationPolicy.Operation.TRANSFER);

        TaskSlaEventProcessor processor = new TaskSlaEventProcessor(
                eventMapper,
                slaMapper,
                mock(TaskSlaRuntimeService.class),
                flowableTaskService,
                processTaskService,
                mock(TaskAddSignService.class),
                capabilityService,
                mock(ProcessCcService.class),
                mock(ProcessCcNotificationPublisher.class),
                mock(ProcessCcRecordMapper.class),
                mock(SysUserMapper.class),
                mock(SysOrganizationMapper.class),
                mock(IdentityDirectoryPort.class),
                objectMapper);

        processor.process("event-1", "worker-1", 7L);

        verify(flowableTaskService, never())
                .setAssignee(anyString(), anyString());
        verify(processTaskService, never())
                .transferTask(anyString(), anyString(), anyString());
        verify(eventMapper, never()).markSuccess(
                anyString(), anyString(), anyLong(), anyString());
        verify(eventMapper).markFailure(
                eq("event-1"),
                eq("worker-1"),
                eq(7L),
                eq("DEAD"),
                anyLong(),
                contains("当前节点不允许转办"));
    }
}
