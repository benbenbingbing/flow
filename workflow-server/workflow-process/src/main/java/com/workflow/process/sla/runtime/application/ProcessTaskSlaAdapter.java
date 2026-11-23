package com.workflow.process.sla.runtime.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.process.instance.model.ProcessRuntimeViews.TaskSla;
import com.workflow.contracts.process.task.port.ProcessTaskSlaPort;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import com.workflow.process.task.application.TaskActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 每次调用先验证真实任务办理权限，再使用发布策略内的计时事务。 */
@Component
@RequiredArgsConstructor
public class ProcessTaskSlaAdapter implements ProcessTaskSlaPort {
    private final TaskActionService tasks;
    private final TaskSlaRuntimeService sla;

    @Override public TaskSla read(String taskId) {
        tasks.requireTaskAccess(taskId);
        var detail = sla.detail(taskId);
        return detail == null ? null : project(detail.sla());
    }

    @Override public TaskSla acknowledge(String taskId) {
        tasks.requireTaskAccess(taskId);
        return project(sla.acknowledge(taskId, UserContext.requireUsernameOrId()));
    }

    @Override public TaskSla pause(String taskId, String reason, String pauseType) {
        tasks.requireTaskAccess(taskId);
        return project(sla.pause(taskId, reason, pauseType));
    }

    @Override public TaskSla resume(String taskId) {
        tasks.requireTaskAccess(taskId);
        return project(sla.resume(taskId));
    }

    private TaskSla project(ProcessTaskSla row) {
        return row == null ? null : new TaskSla(row.getTaskId(), row.getPolicyCode(), row.getPolicyVersion(),
                row.getTimezoneId(), row.getOverallStatus(), row.getResponseStatus(), row.getCompletionStatus(),
                row.getResponseDueAt(), row.getCompletionDueAt(), row.getRespondedAt(), row.getPauseStartedAt());
    }
}
