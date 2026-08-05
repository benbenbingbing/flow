package com.workflow.process.task.api.web;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.task.api.response.TaskVO;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.task.application.TaskDetailService;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.HistoryService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ProcessTaskControllerTest {

    @Test
    void todoTaskMappingIncludesSlaSummary() throws Exception {
        ProcessTaskController controller = new ProcessTaskController(
                mock(ProcessTaskService.class),
                mock(TaskDetailService.class),
                mock(TaskActionService.class),
                mock(ProcessInstanceAccessService.class),
                mock(TaskAddSignService.class),
                mock(EntityDataDynamicService.class),
                mock(HistoryService.class),
                mock(SysUserService.class));
        LocalDateTime responseDue = LocalDateTime.of(
                2026, 8, 4, 2, 21, 20);
        LocalDateTime completionDue = responseDue.plusMinutes(1);
        ProcessTask task = new ProcessTask();
        task.setTaskId("task-1");
        task.setSlaStatus("BREACHED");
        task.setResponseDueTime(responseDue);
        task.setDueTime(completionDue);

        Method converter = ProcessTaskController.class
                .getDeclaredMethod("convertToTaskVO", ProcessTask.class);
        converter.setAccessible(true);
        TaskVO result = (TaskVO) converter.invoke(controller, task);

        assertEquals("BREACHED", result.getSlaStatus());
        assertEquals(
                Date.from(responseDue.toInstant(ZoneOffset.UTC)),
                result.getResponseDueTime());
        assertEquals(
                Date.from(completionDue.toInstant(ZoneOffset.UTC)),
                result.getDueTime());
    }
}
