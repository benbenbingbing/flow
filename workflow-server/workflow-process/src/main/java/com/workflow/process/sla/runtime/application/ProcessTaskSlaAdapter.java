package com.workflow.process.sla.runtime.application;

import com.workflow.port.process.model.ProcessPage;
import com.workflow.port.process.model.instance.ProcessRuntimeViews.TaskSla;
import com.workflow.port.process.ProcessTaskSlaPort;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Objects;

/** 按已发布 SLA 策略处理计时和监控，调用方负责授权。 */
@Component
@RequiredArgsConstructor
public class ProcessTaskSlaAdapter implements ProcessTaskSlaPort {
    private final TaskSlaRuntimeService sla;
    private final TaskSlaMonitorService monitor;

    @Override public TaskSla read(String taskId) {
        var detail = sla.detail(taskId);
        return detail == null ? null : project(detail.sla());
    }

    @Override public TaskSla acknowledge(String taskId) {
        return project(sla.acknowledge(taskId));
    }

    @Override public TaskSla pause(String taskId, String reason, String pauseType) {
        return project(sla.pause(taskId, reason, pauseType));
    }

    @Override public TaskSla resume(String taskId) {
        return project(sla.resume(taskId));
    }

    @Override
    public ProcessPage<MonitorItem> pageMonitor(MonitorQuery query) {
        Objects.requireNonNull(query, "SLA 查询条件不能为空");
        var page = monitor.page(query.pageNumber(), query.pageSize(), query.status(), query.processKey(),
                query.processInstanceId(), query.assigneeId(), query.keyword());
        return new ProcessPage<>(page.getRecords().stream().map(row -> new MonitorItem(row.getProcessInstanceId(),
                row.getProcessKey(), row.getNodeName(), row.getCurrentAssigneeId(), project(row))).toList(),
                page.getTotal(), Math.toIntExact(page.getPageNum()), Math.toIntExact(page.getPageSize()));
    }

    @Override
    public Map<String, Long> countMonitorByStatus(String processKey, String processInstanceId) {
        return Map.copyOf(monitor.statistics(processKey, processInstanceId));
    }

    private TaskSla project(ProcessTaskSla row) {
        return row == null ? null : new TaskSla(row.getTaskId(), row.getPolicyCode(), row.getPolicyVersion(),
                row.getTimezoneId(), row.getOverallStatus(), row.getResponseStatus(), row.getCompletionStatus(),
                row.getResponseDueAt(), row.getCompletionDueAt(), row.getRespondedAt(), row.getPauseStartedAt());
    }
}
