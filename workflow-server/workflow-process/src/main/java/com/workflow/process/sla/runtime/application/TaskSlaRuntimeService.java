package com.workflow.process.sla.runtime.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.process.sla.calendar.application.WorkCalendarCalculator;
import com.workflow.process.sla.calendar.application.WorkCalendarSnapshot;
import com.workflow.process.sla.policy.application.TaskSlaPolicySnapshot;
import com.workflow.process.sla.runtime.api.response.TaskSlaDTO;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaEventMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.mapper.ProcessTaskSlaPauseMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSla;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaPause;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskSlaRuntimeService {

    private final PublishedTaskSlaConfigReader configReader;
    private final TaskSlaCalendarResolver calendarResolver;
    private final WorkCalendarCalculator calculator;
    private final ProcessTaskSlaMapper slaMapper;
    private final ProcessTaskSlaPauseMapper pauseMapper;
    private final ProcessTaskSlaEventMapper eventMapper;
    private final ProcessTaskMapper taskMapper;
    private final TaskService flowableTaskService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla initialize(
            ProcessTask task,
            Map<String, Object> variables) {
        ProcessTaskSla existing = slaMapper.findByTaskId(task.getTaskId());
        if (existing != null) {
            return existing;
        }
        PublishedTaskSlaConfig config = configReader.read(
                task.getProcessDefinitionId(),
                task.getNodeId());
        if (config == null || config.policySnapshot() == null) {
            return null;
        }
        WorkCalendarSnapshot calendar = calendarResolver.resolve(
                config,
                task.getProcessInstanceId(),
                task.getEntityCode(),
                task.getEntityDataId(),
                variables);
        TaskSlaPolicySnapshot policy = config.policySnapshot();
        Instant started = Instant.now();
        Instant responseDue = policy.responseTargetMinutes() == null
                ? null
                : calculator.addMinutes(
                        started,
                        policy.responseTargetMinutes(),
                        policy.responseTimeBasis(),
                        calendar);
        Instant completionDue = calculator.addMinutes(
                started,
                policy.completionTargetMinutes(),
                policy.completionTimeBasis(),
                calendar);

        ProcessTaskSla sla = new ProcessTaskSla();
        sla.setTaskId(task.getTaskId());
        sla.setProcessInstanceId(task.getProcessInstanceId());
        sla.setProcessDefinitionId(task.getProcessDefinitionId());
        sla.setProcessKey(task.getProcessKey());
        sla.setNodeId(task.getNodeId());
        sla.setNodeName(task.getNodeName());
        sla.setBusinessKey(task.getBusinessKey());
        sla.setEntityCode(task.getEntityCode());
        sla.setEntityDataId(task.getEntityDataId());
        sla.setPolicyCode(policy.policyCode());
        sla.setPolicyVersion(policy.version());
        sla.setPolicySnapshotJson(writeJson(policy));
        sla.setCalendarCode(calendar.calendarCode());
        sla.setCalendarVersion(calendar.version());
        sla.setCalendarSnapshotJson(writeJson(calendar));
        sla.setTimezoneId(calendar.timezoneId());
        sla.setCurrentAssigneeId(task.getAssigneeId());
        sla.setStartedAt(utc(started));
        sla.setResponseDueAt(utc(responseDue));
        sla.setCompletionDueAt(utc(completionDue));
        sla.setResponseRemainingMinutes(policy.responseTargetMinutes());
        sla.setCompletionRemainingMinutes(policy.completionTargetMinutes());
        sla.setResponseStatus(responseDue == null
                ? "NOT_APPLICABLE" : "PENDING");
        sla.setCompletionStatus("PENDING");
        sla.setOverallStatus("RUNNING");
        sla.setVersion(1);
        sla.setCreateTime(utc(started));
        sla.setUpdateTime(utc(started));
        slaMapper.insert(sla);

        scheduleEvents(sla, policy, calendar);
        mirrorSummary(sla);
        flowableTaskService.setDueDate(
                task.getTaskId(),
                java.util.Date.from(completionDue));
        return sla;
    }

    @Transactional(readOnly = true)
    public TaskSlaDTO detail(String taskId) {
        ProcessTaskSla sla = requireSla(taskId);
        return new TaskSlaDTO(
                sla,
                pauseMapper.findBySlaId(sla.getId()),
                eventMapper.findBySlaId(sla.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla acknowledge(String taskId, String operatorId) {
        ProcessTaskSla sla = requireSlaForUpdate(taskId);
        return acknowledgeLocked(sla);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla acknowledgeIfConfigured(String taskId) {
        ProcessTaskSla sla = slaMapper.findByTaskIdForUpdate(taskId);
        return sla == null ? null : acknowledgeLocked(sla);
    }

    private ProcessTaskSla acknowledgeLocked(ProcessTaskSla sla) {
        if (!"PENDING".equals(sla.getResponseStatus())) {
            return sla;
        }
        Instant now = Instant.now();
        sla.setRespondedAt(utc(now));
        sla.setResponseStatus(isAfter(now, sla.getResponseDueAt())
                ? "BREACHED" : "MET");
        sla.setResponseRemainingMinutes(0);
        sla.setOverallStatus(overall(sla));
        touch(sla);
        slaMapper.updateById(sla);
        eventMapper.cancelPendingByMetric(sla.getId(), "RESPONSE");
        mirrorSummary(sla);
        return sla;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAssignee(String taskId, String assignee) {
        slaMapper.updateAssignee(taskId, assignee);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla complete(String taskId) {
        ProcessTaskSla sla = slaMapper.findByTaskIdForUpdate(taskId);
        if (sla == null || "COMPLETED".equals(sla.getOverallStatus())) {
            return sla;
        }
        Instant now = Instant.now();
        if ("PENDING".equals(sla.getResponseStatus())) {
            sla.setRespondedAt(utc(now));
            sla.setResponseStatus(isAfter(now, sla.getResponseDueAt())
                    ? "BREACHED" : "MET");
            sla.setResponseRemainingMinutes(0);
        }
        sla.setCompletedAt(utc(now));
        sla.setCompletionStatus(isAfter(now, sla.getCompletionDueAt())
                ? "BREACHED" : "MET");
        sla.setCompletionRemainingMinutes(0);
        sla.setOverallStatus("COMPLETED");
        touch(sla);
        slaMapper.updateById(sla);
        eventMapper.cancelPendingBySlaId(sla.getId());
        mirrorSummary(sla);
        return sla;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla pause(
            String taskId,
            String reason,
            String pauseType) {
        ProcessTaskSla sla = requireSlaForUpdate(taskId);
        TaskSlaPolicySnapshot policy =
                readPolicy(sla.getPolicySnapshotJson());
        boolean processSuspend =
                "PROCESS_SUSPEND".equalsIgnoreCase(pauseType);
        if (processSuspend && !policy.pauseOnProcessSuspend()) {
            return sla;
        }
        if (!policy.allowManualPause()
                && !processSuspend) {
            throw new IllegalStateException("当前SLA策略不允许人工暂停");
        }
        if ("PAUSED".equals(sla.getOverallStatus())) {
            return sla;
        }
        if (!"RUNNING".equals(sla.getOverallStatus())) {
            throw new IllegalStateException("仅运行中的SLA可以暂停");
        }
        Instant now = Instant.now();
        WorkCalendarSnapshot calendar =
                readCalendar(sla.getCalendarSnapshotJson());
        Integer responseRemaining = "PENDING".equals(sla.getResponseStatus())
                ? calculator.remainingMinutes(
                        now,
                        instant(sla.getResponseDueAt()),
                        policy.responseTimeBasis(),
                        calendar)
                : 0;
        int completionRemaining = calculator.remainingMinutes(
                now,
                instant(sla.getCompletionDueAt()),
                policy.completionTimeBasis(),
                calendar);

        ProcessTaskSlaPause pause = new ProcessTaskSlaPause();
        pause.setSlaId(sla.getId());
        pause.setTaskId(taskId);
        pause.setPauseType(normalizePauseType(pauseType));
        pause.setReason(reason == null || reason.isBlank()
                ? "暂停SLA计时" : reason.trim());
        pause.setOperatorId(UserContext.getUserId());
        pause.setStartedAt(utc(now));
        pause.setResponseRemainingMinutes(responseRemaining);
        pause.setCompletionRemainingMinutes(completionRemaining);
        pause.setCreateTime(utc(now));
        pause.setUpdateTime(utc(now));
        pauseMapper.insert(pause);

        sla.setPauseStartedAt(utc(now));
        sla.setResponseRemainingMinutes(responseRemaining);
        sla.setCompletionRemainingMinutes(completionRemaining);
        sla.setOverallStatus("PAUSED");
        touch(sla);
        slaMapper.updateById(sla);
        eventMapper.cancelPendingBySlaId(sla.getId());
        mirrorSummary(sla);
        return sla;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla resume(String taskId) {
        ProcessTaskSla sla = requireSlaForUpdate(taskId);
        if (!"PAUSED".equals(sla.getOverallStatus())) {
            return sla;
        }
        ProcessTaskSlaPause pause =
                pauseMapper.findOpenForUpdate(sla.getId());
        if (pause == null) {
            throw new IllegalStateException("SLA暂停记录不存在");
        }
        TaskSlaPolicySnapshot policy =
                readPolicy(sla.getPolicySnapshotJson());
        WorkCalendarSnapshot calendar =
                readCalendar(sla.getCalendarSnapshotJson());
        Instant now = Instant.now();
        Instant effectiveResume = cappedResumeTime(pause, policy, now);
        return resumeLocked(
                sla,
                pause,
                policy,
                calendar,
                now,
                effectiveResume);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla resumeIfPauseExpired(String taskId) {
        ProcessTaskSla sla = slaMapper.findByTaskIdForUpdate(taskId);
        if (sla == null || !"PAUSED".equals(sla.getOverallStatus())) {
            return sla;
        }
        ProcessTaskSlaPause pause =
                pauseMapper.findOpenForUpdate(sla.getId());
        if (pause == null) {
            return sla;
        }
        TaskSlaPolicySnapshot policy =
                readPolicy(sla.getPolicySnapshotJson());
        if (policy.maxPauseMinutes() == null
                || policy.maxPauseMinutes() <= 0) {
            return sla;
        }
        Instant cap = instant(pause.getStartedAt()).plus(
                Duration.ofMinutes(policy.maxPauseMinutes()));
        Instant now = Instant.now();
        if (now.isBefore(cap)) {
            return sla;
        }
        return resumeLocked(
                sla,
                pause,
                policy,
                readCalendar(sla.getCalendarSnapshotJson()),
                now,
                cap);
    }

    @Transactional(rollbackFor = Exception.class)
    public void pauseProcess(String processInstanceId) {
        for (ProcessTaskSla sla :
                slaMapper.findByProcessInstanceId(processInstanceId)) {
            if ("RUNNING".equals(sla.getOverallStatus())) {
                pause(
                        sla.getTaskId(),
                        "流程实例挂起",
                        "PROCESS_SUSPEND");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void resumeProcess(String processInstanceId) {
        for (ProcessTaskSla candidate :
                slaMapper.findByProcessInstanceId(processInstanceId)) {
            ProcessTaskSla sla =
                    slaMapper.findByTaskIdForUpdate(
                            candidate.getTaskId());
            if (sla == null
                    || !"PAUSED".equals(sla.getOverallStatus())) {
                continue;
            }
            ProcessTaskSlaPause pause =
                    pauseMapper.findOpenForUpdate(sla.getId());
            if (pause == null
                    || !"PROCESS_SUSPEND".equals(
                            pause.getPauseType())) {
                continue;
            }
            TaskSlaPolicySnapshot policy =
                    readPolicy(sla.getPolicySnapshotJson());
            Instant now = Instant.now();
            resumeLocked(
                    sla,
                    pause,
                    policy,
                    readCalendar(sla.getCalendarSnapshotJson()),
                    now,
                    cappedResumeTime(pause, policy, now));
        }
    }

    private ProcessTaskSla resumeLocked(
            ProcessTaskSla sla,
            ProcessTaskSlaPause pause,
            TaskSlaPolicySnapshot policy,
            WorkCalendarSnapshot calendar,
            Instant now,
            Instant effectiveResume) {
        if ("PENDING".equals(sla.getResponseStatus())) {
            sla.setResponseDueAt(utc(calculator.addMinutes(
                    effectiveResume,
                    value(pause.getResponseRemainingMinutes()),
                    policy.responseTimeBasis(),
                    calendar)));
        }
        sla.setCompletionDueAt(utc(calculator.addMinutes(
                effectiveResume,
                value(pause.getCompletionRemainingMinutes()),
                policy.completionTimeBasis(),
                calendar)));
        pause.setResumedAt(utc(now));
        pause.setDurationSeconds(Duration.between(
                instant(pause.getStartedAt()), now).getSeconds());
        pause.setUpdateTime(utc(now));
        pauseMapper.updateById(pause);

        sla.setPauseStartedAt(null);
        sla.setOverallStatus("RUNNING");
        touch(sla);
        slaMapper.updateById(sla);
        scheduleEvents(sla, policy, calendar);
        mirrorSummary(sla);
        flowableTaskService.setDueDate(
                sla.getTaskId(),
                java.util.Date.from(instant(sla.getCompletionDueAt())));
        return sla;
    }

    private Instant cappedResumeTime(
            ProcessTaskSlaPause pause,
            TaskSlaPolicySnapshot policy,
            Instant now) {
        if (policy.maxPauseMinutes() == null
                || policy.maxPauseMinutes() <= 0) {
            return now;
        }
        Instant cap = instant(pause.getStartedAt()).plus(
                Duration.ofMinutes(policy.maxPauseMinutes()));
        return now.isAfter(cap) ? cap : now;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla markBreach(
            String taskId,
            String metricType) {
        ProcessTaskSla sla = requireSlaForUpdate(taskId);
        if (!"RUNNING".equals(sla.getOverallStatus())) {
            return sla;
        }
        if ("RESPONSE".equalsIgnoreCase(metricType)
                && "PENDING".equals(sla.getResponseStatus())) {
            sla.setResponseStatus("BREACHED");
            sla.setResponseRemainingMinutes(0);
        } else if ("COMPLETION".equalsIgnoreCase(metricType)
                && "PENDING".equals(sla.getCompletionStatus())) {
            sla.setCompletionStatus("BREACHED");
            sla.setCompletionRemainingMinutes(0);
        }
        sla.setOverallStatus(overall(sla));
        touch(sla);
        slaMapper.updateById(sla);
        mirrorSummary(sla);
        return sla;
    }

    @Transactional(readOnly = true)
    public ProcessTaskSla find(String taskId) {
        return slaMapper.findByTaskId(taskId);
    }

    private void scheduleEvents(
            ProcessTaskSla sla,
            TaskSlaPolicySnapshot policy,
            WorkCalendarSnapshot calendar) {
        int version = value(sla.getVersion());
        if ("PENDING".equals(sla.getResponseStatus())
                && sla.getResponseDueAt() != null) {
            insertDeadlineEvent(
                    sla,
                    "RESPONSE",
                    instant(sla.getResponseDueAt()),
                    version);
        }
        if ("PENDING".equals(sla.getCompletionStatus())) {
            insertDeadlineEvent(
                    sla,
                    "COMPLETION",
                    instant(sla.getCompletionDueAt()),
                    version);
        }
        for (TaskSlaPolicySnapshot.EscalationStep step :
                policy.escalationSteps() == null
                        ? List.<TaskSlaPolicySnapshot.EscalationStep>of()
                        : policy.escalationSteps()) {
            Instant due = "RESPONSE".equalsIgnoreCase(step.metricType())
                    ? instant(sla.getResponseDueAt())
                    : instant(sla.getCompletionDueAt());
            if (due == null) {
                continue;
            }
            int executions = Math.max(1, step.maxExecutions());
            for (int executionNo = 1;
                 executionNo <= executions;
                 executionNo++) {
                long repeat = step.repeatIntervalMinutes() == null
                        ? 0L
                        : (long) step.repeatIntervalMinutes()
                                * (executionNo - 1);
                long offset = Math.max(0, step.offsetMinutes());
                Instant trigger = switch (
                        step.triggerType().toUpperCase()) {
                    case "BEFORE_DUE" ->
                            due.minus(Duration.ofMinutes(offset))
                                    .plus(Duration.ofMinutes(repeat));
                    case "AFTER_DUE" ->
                            due.plus(Duration.ofMinutes(offset + repeat));
                    default -> due.plus(Duration.ofMinutes(repeat));
                };
                ProcessTaskSlaEvent event = baseEvent(
                        sla,
                        step.metricType(),
                        trigger,
                        "ESCALATION",
                        step.actionType(),
                        version);
                event.setStepId(step.id());
                event.setExecutionNo(executionNo);
                event.setMaxExecutions(executions);
                event.setActionConfigSnapshot(
                        actionConfig(step));
                event.setIdempotencyKey(String.join(
                        ":",
                        "task-sla",
                        sla.getId(),
                        "v" + version,
                        step.metricType(),
                        step.id(),
                        String.valueOf(executionNo)));
                eventMapper.insert(event);
            }
        }
    }

    private void insertDeadlineEvent(
            ProcessTaskSla sla,
            String metricType,
            Instant due,
            int version) {
        ProcessTaskSlaEvent event = baseEvent(
                sla,
                metricType,
                due,
                "DEADLINE",
                "MARK_BREACH",
                version);
        event.setIdempotencyKey(String.join(
                ":",
                "task-sla",
                sla.getId(),
                "v" + version,
                metricType,
                "deadline"));
        eventMapper.insert(event);
    }

    private ProcessTaskSlaEvent baseEvent(
            ProcessTaskSla sla,
            String metricType,
            Instant trigger,
            String eventType,
            String actionType,
            int version) {
        ProcessTaskSlaEvent event = new ProcessTaskSlaEvent();
        event.setSlaId(sla.getId());
        event.setTaskId(sla.getTaskId());
        event.setEventType(eventType);
        event.setMetricType(metricType.toUpperCase());
        event.setTriggerAt(utc(trigger));
        event.setActionType(actionType.toUpperCase());
        event.setExecutionNo(1);
        event.setMaxExecutions(1);
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setMaxRetries(5);
        event.setLeaseToken(0L);
        event.setCreateTime(utc(Instant.now()));
        event.setUpdateTime(utc(Instant.now()));
        return event;
    }

    private String actionConfig(
            TaskSlaPolicySnapshot.EscalationStep step) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("stepName", step.stepName());
        value.put("templateCode", step.templateCode());
        value.put("recipientConfigJson", step.recipientConfigJson());
        value.put("targetConfigJson", step.targetConfigJson());
        return writeJson(value);
    }

    private void mirrorSummary(ProcessTaskSla sla) {
        taskMapper.updateSlaSummary(
                sla.getTaskId(),
                sla.getResponseDueAt(),
                sla.getCompletionDueAt(),
                sla.getOverallStatus());
    }

    private ProcessTaskSla requireSla(String taskId) {
        ProcessTaskSla sla = slaMapper.findByTaskId(taskId);
        if (sla == null) {
            throw new IllegalArgumentException("任务未配置SLA");
        }
        return sla;
    }

    private ProcessTaskSla requireSlaForUpdate(String taskId) {
        ProcessTaskSla sla = slaMapper.findByTaskIdForUpdate(taskId);
        if (sla == null) {
            throw new IllegalArgumentException("任务未配置SLA");
        }
        return sla;
    }

    private TaskSlaPolicySnapshot readPolicy(String document) {
        return readJson(document, TaskSlaPolicySnapshot.class, "SLA策略");
    }

    private WorkCalendarSnapshot readCalendar(String document) {
        return readJson(document, WorkCalendarSnapshot.class, "工作日历");
    }

    private <T> T readJson(
            String document,
            Class<T> type,
            String name) {
        try {
            return objectMapper.readValue(document, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(name + "快照解析失败", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SLA运行快照序列化失败", exception);
        }
    }

    private String overall(ProcessTaskSla sla) {
        return "BREACHED".equals(sla.getResponseStatus())
                || "BREACHED".equals(sla.getCompletionStatus())
                ? "BREACHED" : "RUNNING";
    }

    private void touch(ProcessTaskSla sla) {
        sla.setVersion(value(sla.getVersion()) + 1);
        sla.setUpdateTime(utc(Instant.now()));
    }

    private boolean isAfter(Instant now, LocalDateTime due) {
        return due != null && now.isAfter(instant(due));
    }

    private LocalDateTime utc(Instant value) {
        return value == null
                ? null
                : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(LocalDateTime value) {
        return value == null
                ? null
                : value.toInstant(ZoneOffset.UTC);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizePauseType(String value) {
        return value == null || value.isBlank()
                ? "MANUAL"
                : value.trim().toUpperCase();
    }
}
