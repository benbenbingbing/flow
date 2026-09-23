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

/**
 * 维护单个流程任务的 SLA 计时状态、暂停区间和待执行升级事件。
 * 初始化时固定策略与日历快照；之后所有截止时间重算都使用这份快照，
 * 并把摘要同步到待办表及 Flowable 截止时间供查询和提醒使用。
 */
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

    /**
     * 为新待办创建 SLA；没有节点发布配置时返回 null，已有记录时幂等返回。
     * variables 用于业务部门日历解析，最终策略与日历快照写入记录供后续暂停和恢复使用。
     *
     * @param task 新建待办；其节点和业务主键决定发布配置、SLA 归属与截止时间投影
     * @param variables 流程变量；用于解析业务部门对应的工作日历，随后冻结为任务快照
     * @return 新建或已有的 SLA；节点未配置时为 null
     */
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

        // 截止事件、升级事件、待办摘要与 Flowable dueDate 必须围绕同一截止时间建立。
        scheduleEvents(sla, policy, calendar);
        mirrorSummary(sla);
        flowableTaskService.setDueDate(
                task.getTaskId(),
                java.util.Date.from(completionDue));
        return sla;
    }

    /**
     * 返回任务 SLA 状态、暂停历史和事件明细，供详情页解释当前倒计时。
     *
     * @param taskId 待办 ID，用于加载 SLA、暂停区间和升级事件
     * @return 供详情页展示的计时与事件明细
     * @throws IllegalArgumentException 任务未配置 SLA
     */
    @Transactional(readOnly = true)
    public TaskSlaDTO detail(String taskId) {
        ProcessTaskSla sla = requireSla(taskId);
        return new TaskSlaDTO(
                sla,
                pauseMapper.findBySlaId(sla.getId()),
                eventMapper.findBySlaId(sla.getId()));
    }

    /**
     * 首次响应时锁定并结算响应指标；operatorId 保留办理入口参数，
     * 当前结算只记录响应时刻，不把操作人写入 SLA 主记录。
     *
     * @param taskId 首次响应的待办 ID，用于锁定并结算响应指标
     * @param operatorId 办理入口传入的操作人 ID；当前不写入 SLA 主记录
     * @return 结算后的 SLA 状态
     * @throws IllegalArgumentException 任务未配置 SLA
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla acknowledge(String taskId, String operatorId) {
        ProcessTaskSla sla = requireSlaForUpdate(taskId);
        return acknowledgeLocked(sla);
    }

    /**
     * 未配置 SLA 时返回 null，供普通任务认领路径无需额外分支调用。
     *
     * @param taskId 待办 ID，用于在普通认领路径查找可选 SLA
     * @return 结算后的 SLA；未配置时为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessTaskSla acknowledgeIfConfigured(String taskId) {
        ProcessTaskSla sla = slaMapper.findByTaskIdForUpdate(taskId);
        return sla == null ? null : acknowledgeLocked(sla);
    }

    /**
     * 锁内仅结算仍待响应的指标，并取消尚未执行的响应升级事件。
     *
     * @param sla 已锁定的 SLA 行；仅待响应时更新状态并取消响应事件
     * @return 结算后的同一 SLA 对象
     */
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

    /**
     * 转办后同步当前办理人，后续升级通知与自动加签据此寻找目标。
     *
     * @param taskId 转办待办 ID，用于定位 SLA
     * @param assignee 新的办理人 ID，供后续升级通知与自动加签选取目标
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAssignee(String taskId, String assignee) {
        slaMapper.updateAssignee(taskId, assignee);
    }

    /**
     * 完成任务时结算未响应和办结指标，取消全部待执行事件并更新待办摘要。
     *
     * @param taskId 已完成的待办 ID，用于锁定并结算两项指标
     * @return 完成后的 SLA；未配置时为 null
     */
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

    /**
     * 暂停运行中 SLA；人工暂停受策略开关约束，流程挂起按独立开关处理。
     * 将当前剩余工作分钟写入暂停记录，恢复时据此重算截止时间。
     *
     * @param taskId 待办 ID，用于锁定运行中的 SLA
     * @param reason 暂停原因，空值会转为默认说明并保存到暂停历史
     * @param pauseType 暂停来源；PROCESS_SUSPEND 按流程挂起策略处理，其他类型按人工暂停处理
     * @return 暂停后的 SLA，策略忽略流程挂起或已暂停时返回原状态
     * @throws IllegalStateException 策略禁止人工暂停或当前状态不允许暂停
     */
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
        // 保存剩余工作时间而非简单顺延墙上时长；恢复可能跨休息日或日历时区。
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

    /**
     * 手动恢复暂停的 SLA，超过最大暂停时长的部分从上限时刻继续计时。
     *
     * @param taskId 待办 ID，用于锁定暂停状态及其开放暂停记录
     * @return 重算截止时间后的 SLA；非暂停状态返回原记录
     * @throws IllegalStateException 暂停状态下缺少开放的暂停记录
     */
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

    /**
     * 定时补偿达到最大暂停时长的任务；未到上限或无上限时保持暂停。
     *
     * @param taskId 待办 ID，用于定时检查最大暂停时长
     * @return 达到上限时恢复后的 SLA，否则返回原记录或 null
     */
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

    /**
     * 流程挂起时暂停其所有运行中任务 SLA，供流程状态同步入口调用。
     *
     * @param processInstanceId 流程实例 ID，用于筛选并暂停其运行中的任务 SLA
     */
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

    /**
     * 流程恢复时仅恢复由 PROCESS_SUSPEND 造成的暂停，不触碰人工暂停。
     *
     * @param processInstanceId 流程实例 ID，用于筛选并恢复流程挂起造成的暂停
     */
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

    /**
     * 锁内关闭暂停区间、重算截止时间并重建待执行事件。
     * effectiveResume 是计时恢复起点，now 是实际执行时刻，两者在超出暂停上限时不同。
     *
     * @param sla 已锁定的 SLA 行，写回新的截止时间和运行状态
     * @param pause 开放的暂停区间，记录实际恢复时间与持续秒数
     * @param policy 任务初始化时固定的策略，决定两项指标的计时方式
     * @param calendar 任务初始化时固定的日历，用于跨非工作时间重算截止点
     * @param now 实际执行恢复的时间，用于关闭暂停区间
     * @param effectiveResume 计时重新开始的时间；超过暂停上限时取上限时刻
     * @return 恢复计时并重建事件后的 SLA
     */
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

    /**
     * 暂停超上限时从上限时刻恢复计时，使超出的等待时间计入 SLA。
     *
     * @param pause 开放暂停区间，其开始时间用于计算上限
     * @param policy 冻结的策略，提供最大暂停分钟数
     * @param now 当前时间；未配置上限时直接作为恢复起点
     * @return 不晚于当前时间的有效恢复起点
     */
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

    /**
     * 到期事件仅在 RUNNING 状态将指定指标标记为超时；其他状态的旧事件不修改主记录。
     *
     * @param taskId 到期事件所属待办 ID，用于锁定 SLA
     * @param metricType 到期指标类型 RESPONSE 或 COMPLETION，决定更新哪项状态
     * @return 更新后的 SLA；非运行状态保持原样
     */
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

    /**
     * 按任务 ID 查找可选 SLA，供调度器及普通待办路径判断是否配置。
     *
     * @param taskId 待办 ID，用于判断该任务是否启用 SLA
     * @return 对应 SLA；未配置时为 null
     */
    @Transactional(readOnly = true)
    public ProcessTaskSla find(String taskId) {
        return slaMapper.findByTaskId(taskId);
    }

    /**
     * 根据当前截止时间生成到期及升级事件；版本进入幂等键，恢复后的新一轮
     * 事件不会与暂停前已取消的事件复用同一去重坐标。
     *
     * @param sla 当前 SLA，提供截止时间、状态与版本，组成新事件的触发时间和幂等键
     * @param policy 冻结的策略，提供需要展开的升级步骤及重复次数
     * @param calendar 与截止时间对应的冻结日历；截止点已在调用前算出，此处保留上下文参数
     */
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
            // 重复升级按触发类型相对截止时间计算，executionNo 区分各轮通知。
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

    /**
     * 为响应或办结指标插入一次到期事件，由处理器在触发时标记超时。
     *
     * @param sla 所属 SLA，决定事件关联任务和幂等键
     * @param metricType RESPONSE 或 COMPLETION，决定到期后标记的指标
     * @param due 该指标的绝对截止时间，即事件触发时间
     * @param version 当前 SLA 版本，避免恢复后事件与旧事件共用幂等键
     */
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

    /**
     * 建立统一的待执行事件初态和租约字段，供调度器认领及失败重试。
     *
     * @param sla 所属 SLA，提供任务关联信息
     * @param metricType 事件对应的响应或办结指标
     * @param trigger 事件计划触发的绝对时间
     * @param eventType 事件类别，例如到期或升级
     * @param actionType 处理器执行的动作类型
     * @param version 调用方用于构造幂等键的 SLA 版本；基础事件字段不直接使用
     * @return 带待执行状态和租约初值的事件对象
     */
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

    /**
     * 固定步骤执行所需的收件人和目标配置，避免发布策略后续变化影响已排队事件。
     *
     * @param step 发布时冻结的升级步骤，其模板和目标配置随事件持久化
     * @return 动作配置 JSON，供事件处理器重试时复用
     */
    private String actionConfig(
            TaskSlaPolicySnapshot.EscalationStep step) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("stepName", step.stepName());
        value.put("templateCode", step.templateCode());
        value.put("recipientConfigJson", step.recipientConfigJson());
        value.put("targetConfigJson", step.targetConfigJson());
        return writeJson(value);
    }

    /**
     * 将截止时间与状态投影到待办表，列表查询无需逐条读取 SLA 详情。
     *
     * @param sla 最新 SLA 状态，其截止点和总体状态写入待办列表投影
     */
    private void mirrorSummary(ProcessTaskSla sla) {
        taskMapper.updateSlaSummary(
                sla.getTaskId(),
                sla.getResponseDueAt(),
                sla.getCompletionDueAt(),
                sla.getOverallStatus());
    }

    /**
     * 必须配置 SLA 的详情入口使用，缺失时明确报错。
     *
     * @param taskId 必须已配置 SLA 的待办 ID
     * @return 对应 SLA
     * @throws IllegalArgumentException 找不到任务 SLA
     */
    private ProcessTaskSla requireSla(String taskId) {
        ProcessTaskSla sla = slaMapper.findByTaskId(taskId);
        if (sla == null) {
            throw new IllegalArgumentException("任务未配置SLA");
        }
        return sla;
    }

    /**
     * 更新入口锁定 SLA 行，避免认领、暂停和完成并发覆盖状态。
     *
     * @param taskId 待更新待办 ID，用于加锁读取 SLA 行
     * @return 已锁定的 SLA
     * @throws IllegalArgumentException 找不到任务 SLA
     */
    private ProcessTaskSla requireSlaForUpdate(String taskId) {
        ProcessTaskSla sla = slaMapper.findByTaskIdForUpdate(taskId);
        if (sla == null) {
            throw new IllegalArgumentException("任务未配置SLA");
        }
        return sla;
    }

    /**
     * 恢复初始化时固定的策略快照，供暂停和事件重排使用。
     *
     * @param document 任务创建时持久化的策略 JSON，不读取后来修改的策略
     * @return 用于当前任务状态流转的策略快照
     */
    private TaskSlaPolicySnapshot readPolicy(String document) {
        return readJson(document, TaskSlaPolicySnapshot.class, "SLA策略");
    }

    /**
     * 恢复初始化时固定的日历快照，避免按当前可变日历重算旧任务。
     *
     * @param document 任务创建时持久化的日历 JSON，不读取后来修改的日历
     * @return 用于截止时间重算的日历快照
     */
    private WorkCalendarSnapshot readCalendar(String document) {
        return readJson(document, WorkCalendarSnapshot.class, "工作日历");
    }

    /**
     * 统一解析快照，文档损坏时附带业务名称定位配置来源。
     *
     * @param document 待解析的任务级快照 JSON
     * @param type 目标快照类型，决定反序列化结果
     * @param name 业务名称，用于解析失败时定位快照
     * @return 指定类型的快照对象
     * @throws IllegalStateException 快照 JSON 无法解析
     */
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

    /**
     * 序列化任务级快照，供后续状态流转和升级事件复用。
     *
     * @param value 待冻结的策略、日历或动作配置对象
     * @return 持久化使用的 JSON 文本
     * @throws IllegalStateException 快照对象无法序列化
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SLA运行快照序列化失败", exception);
        }
    }

    /**
     * 响应或办结任一指标超时即显示 BREACHED，其余运行状态保持 RUNNING。
     *
     * @param sla 包含响应和办结指标状态的 SLA
     * @return 用于列表和详情展示的总体运行状态
     */
    private String overall(ProcessTaskSla sla) {
        return "BREACHED".equals(sla.getResponseStatus())
                || "BREACHED".equals(sla.getCompletionStatus())
                ? "BREACHED" : "RUNNING";
    }

    /**
     * 更新状态版本和 UTC 修改时间；新版本也用于恢复后的事件去重键。
     *
     * @param sla 即将写回的 SLA；递增版本并更新 UTC 修改时间
     */
    private void touch(ProcessTaskSla sla) {
        sla.setVersion(value(sla.getVersion()) + 1);
        sla.setUpdateTime(utc(Instant.now()));
    }

    /**
     * 以 UTC 解释数据库截止时间，严格晚于才视为超时。
     *
     * @param now 实际响应或办结时间
     * @param due 数据库保存的 UTC 截止时间；为空表示无该指标
     * @return 实际时间严格晚于截止时间时为 true
     */
    private boolean isAfter(Instant now, LocalDateTime due) {
        return due != null && now.isAfter(instant(due));
    }

    /**
     * 写库前统一转换为 UTC 本地时间，避免服务器默认时区影响比较。
     *
     * @param value 需要写入数据库的绝对时刻，可为空
     * @return UTC 时区对应的本地时间；输入为空时返回 null
     */
    private LocalDateTime utc(Instant value) {
        return value == null
                ? null
                : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 从数据库 UTC 时间恢复绝对时刻，供工作日历继续计算。
     *
     * @param value 从数据库读取的 UTC 本地时间，可为空
     * @return 可用于日历计算的绝对时刻；输入为空时返回 null
     */
    private Instant instant(LocalDateTime value) {
        return value == null
                ? null
                : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * 旧记录缺失剩余分钟或版本时按零处理，避免恢复运算空指针。
     *
     * @param value 可能为空的历史分钟数或版本号
     * @return 非空原值；空值按零处理
     */
    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 未指定类型的请求按人工暂停记录，供流程恢复时排除该暂停。
     *
     * @param value 请求中的暂停来源，可为空
     * @return 规范化的大写来源；空值按 MANUAL 保存
     */
    private String normalizePauseType(String value) {
        return value == null || value.isBlank()
                ? "MANUAL"
                : value.trim().toUpperCase();
    }
}
