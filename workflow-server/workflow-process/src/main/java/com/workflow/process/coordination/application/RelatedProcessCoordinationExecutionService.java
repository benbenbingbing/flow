package com.workflow.process.coordination.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Operation;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.ProcessState;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.TargetImpact;
import com.workflow.process.task.application.ProcessTaskService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行单目标的跨实体流程写操作。
 *
 * <p>这个服务只由 Outbox 处理器调用。执行前必须使用原操作人
 * 重新解析整张关系图，并比对目标流程实例与精确发布版本。
 * 流程变更与操作日志在同一事务中执行；消费器事件 ID 同时作为
 * 日志主键，使崩溃后的重投仍然幂等。</p>
 */
@Service
@RequiredArgsConstructor
public class RelatedProcessCoordinationExecutionService {

    private static final String ROUTE_MARKER =
            "_relatedProcessCoordinationRouteKey";
    private static final String TERMINATION_MARKER =
            "_relatedProcessCoordinationTerminationKey";

    private final RelatedProcessCoordinationPlanService planService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessTaskService processTaskService;
    private final ProcessOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 复核并执行一条 Outbox 目标事件。
     *
     * @param outboxEventId Outbox 事件 ID，同时用作业务幂等主键
     * @param eventKey      事件业务幂等键
     * @param event         服务端计划快照
     * @return 本次是实际执行还是幂等重放
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.OTHER,
            operation = "执行跨实体流程协同",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "RELATED_PROCESS_COORDINATION",
            captureResult = true)
    public ExecutionResult execute(
            String outboxEventId,
            String eventKey,
            RelatedProcessCoordinationEvent event) {
        requireEvent(outboxEventId, eventKey, event);
        RelatedProcessCoordinationPlan original = event.originalPlan();
        requireSnapshotConsistency(eventKey, event);
        RelatedProcessCoordinationPlan current = planService.replan(
                original.source(), event.command());
        if (!original.graphFingerprint().equals(
                current.graphFingerprint())) {
            throw conflict(
                    "RELATED_PROCESS_GRAPH_CHANGED",
                    "关联记录在预览后发生变化，未执行流程协同");
        }
        TargetImpact currentTarget = requireCurrentTarget(
                current.targets(), event.target());
        requirePinnedTarget(event.target(), currentTarget);

        // 即使 Outbox 已被重投，也先完成关系与权限复核，
        // 不允许历史成功日志变成绕过当前权限的通行证。
        if (operationLogMapper.selectById(outboxEventId) != null) {
            return new ExecutionResult(
                    eventKey,
                    original.operation(),
                    currentTarget.processInstanceId(),
                    "IDEMPOTENT_REPLAY");
        }

        return switch (original.operation()) {
            case ROUTE_RELATED_PARENT -> route(
                    outboxEventId,
                    eventKey,
                    original,
                    event.target(),
                    currentTarget);
            case PROPAGATE_TERMINATION -> terminate(
                    outboxEventId,
                    eventKey,
                    original,
                    event.target(),
                    currentTarget);
            default -> throw new IllegalArgumentException(
                    "Outbox 不允许执行只读协同操作");
        };
    }

    private ExecutionResult route(
            String outboxEventId,
            String eventKey,
            RelatedProcessCoordinationPlan plan,
            TargetImpact original,
            TargetImpact current) {
        if (current.state() != ProcessState.ACTIVE) {
            throw conflict(
                    "RELATED_PARENT_PROCESS_DRIFTED",
                    "关联上级流程已不在运行中，未执行路由");
        }
        Object marker = runtimeService.getVariable(
                current.processInstanceId(), ROUTE_MARKER);
        boolean alreadyApplied = eventKey.equals(String.valueOf(marker))
                && current.activeActivityIds().size() == 1
                && current.activeActivityIds().contains(
                        plan.targetActivityId());
        if (!alreadyApplied) {
            if (!original.activeActivityIds().equals(
                    current.activeActivityIds())) {
                throw conflict(
                        "RELATED_PARENT_ACTIVITY_CHANGED",
                        "关联上级流程的当前节点在预览后发生变化");
            }
            runtimeService.setVariable(
                    current.processInstanceId(), ROUTE_MARKER, eventKey);
            // 并行分支必须作为一个整体移动到单一整改节点；
            // 仅移动其中一条 execution 会留下孤儿分支。
            if (current.activeActivityIds().size() == 1) {
                runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(current.processInstanceId())
                        .moveActivityIdTo(
                                current.activeActivityIds().get(0),
                                plan.targetActivityId())
                        .changeState();
            } else {
                runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(current.processInstanceId())
                        .moveActivityIdsToSingleActivityId(
                                current.activeActivityIds(),
                                plan.targetActivityId())
                        .changeState();
            }
            processTaskService.syncTasksFromFlowable(
                    current.processInstanceId());
        }
        writeLog(
                outboxEventId,
                current.processInstanceId(),
                plan,
                original.activeActivityIds(),
                List.of(plan.targetActivityId()),
                "ROUTE_RELATED_PARENT");
        return new ExecutionResult(
                eventKey,
                plan.operation(),
                current.processInstanceId(),
                alreadyApplied ? "IDEMPOTENT_RECOVERY" : "EXECUTED");
    }

    private ExecutionResult terminate(
            String outboxEventId,
            String eventKey,
            RelatedProcessCoordinationPlan plan,
            TargetImpact original,
            TargetImpact current) {
        String marker = "[coordination:" + eventKey + "]";
        if (current.state() == ProcessState.TERMINATED) {
            HistoricProcessInstance historic = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(current.processInstanceId())
                    .singleResult();
            if (historic == null
                    || !StringUtils.hasText(historic.getDeleteReason())
                    || !historic.getDeleteReason().contains(marker)) {
                throw conflict(
                        "RELATED_PROCESS_TERMINATION_DRIFTED",
                        "关联流程已由其他操作结束，不能视为本次传播成功");
            }
            writeLog(
                    outboxEventId,
                    current.processInstanceId(),
                    plan,
                    original.activeActivityIds(),
                    List.of("TERMINATED"),
                    "PROPAGATE_TERMINATION");
            return new ExecutionResult(
                    eventKey,
                    plan.operation(),
                    current.processInstanceId(),
                    "IDEMPOTENT_RECOVERY");
        }
        if (current.state() != ProcessState.ACTIVE
                || !original.activeActivityIds().equals(
                        current.activeActivityIds())) {
            throw conflict(
                    "RELATED_PROCESS_TERMINATION_DRIFTED",
                    "关联流程状态或当前节点在预览后发生变化");
        }
        runtimeService.setVariable(
                current.processInstanceId(), TERMINATION_MARKER, eventKey);
        String reason = StringUtils.hasText(plan.reason())
                ? plan.reason() : "宿主流程终止传播";
        // 关联终止用于维护父子流程状态一致性，属于系统编排而非用户操作，明确豁免节点终止开关。
        runtimeService.deleteProcessInstance(
                current.processInstanceId(), reason + " " + marker);
        processTaskService.deleteTasksByProcessInstance(
                current.processInstanceId());
        writeLog(
                outboxEventId,
                current.processInstanceId(),
                plan,
                original.activeActivityIds(),
                List.of("TERMINATED"),
                "PROPAGATE_TERMINATION");
        return new ExecutionResult(
                eventKey,
                plan.operation(),
                current.processInstanceId(),
                "EXECUTED");
    }

    private TargetImpact requireCurrentTarget(
            List<TargetImpact> currentTargets,
            TargetImpact expected) {
        return currentTargets.stream()
                .filter(item -> item.record().equals(expected.record()))
                .findFirst()
                .orElseThrow(() -> conflict(
                        "RELATED_PROCESS_TARGET_REMOVED",
                        "预览中的目标记录已不在当前关系图中"));
    }

    private void requirePinnedTarget(
            TargetImpact expected,
            TargetImpact current) {
        if (!same(expected.entityProcessLinkId(),
                        current.entityProcessLinkId())
                || !same(expected.processInstanceId(),
                        current.processInstanceId())
                || !same(expected.processDefinitionId(),
                        current.processDefinitionId())
                || !same(expected.processVersionHistoryId(),
                        current.processVersionHistoryId())
                || !same(expected.processVersion(),
                        current.processVersion())
                || !same(expected.processKey(), current.processKey())) {
            throw conflict(
                    "RELATED_PROCESS_VERSION_DRIFTED",
                    "目标记录的流程实例或精确发布版本已变化");
        }
    }

    private void writeLog(
            String id,
            String processInstanceId,
            RelatedProcessCoordinationPlan plan,
            Object before,
            Object after,
            String operationType) {
        if (operationLogMapper.selectById(id) != null) {
            return;
        }
        ProcessOperationLog log = new ProcessOperationLog();
        log.setId(id);
        log.setProcessInstanceId(processInstanceId);
        log.setOperationType(operationType);
        log.setOperatorId(plan.source().operatorId());
        log.setOperatorName(plan.source().operatorName());
        log.setOperationTime(LocalDateTime.now());
        log.setOperationComment(StringUtils.hasText(plan.reason())
                ? plan.reason() : operationType);
        log.setOldValue(json(before));
        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("value", after);
        newValue.put("coordinationPlanId", plan.planId());
        newValue.put("graphFingerprint", plan.graphFingerprint());
        newValue.put("targetProcessVersionHistoryId",
                plan.targets().stream()
                        .filter(item -> processInstanceId.equals(
                                item.processInstanceId()))
                        .map(TargetImpact::processVersionHistoryId)
                        .findFirst().orElse(null));
        log.setNewValue(json(newValue));
        log.setOldValueFormat("JSON");
        log.setNewValueFormat("JSON");
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "流程协同审计内容无法序列化",
                    exception);
        }
    }

    private void requireEvent(
            String outboxEventId,
            String eventKey,
            RelatedProcessCoordinationEvent event) {
        if (!StringUtils.hasText(outboxEventId)
                || !StringUtils.hasText(eventKey)
                || event == null
                || event.originalPlan() == null
                || event.command() == null
                || event.target() == null) {
            throw new IllegalArgumentException(
                    "跨实体流程协同事件不完整");
        }
    }

    /**
     * 复核 Outbox 行与其服务端载荷是同一份计划快照。
     *
     * <p>关系与权限会在随后重查，这里先防止损坏或人工修改的
     * Outbox 载荷把另一条目标或命令塞进已有计划。</p>
     */
    private void requireSnapshotConsistency(
            String eventKey,
            RelatedProcessCoordinationEvent event) {
        RelatedProcessCoordinationPlan plan = event.originalPlan();
        if (event.command().operation() != plan.operation()
                || !event.command().publishedRelationPath().equals(
                        plan.publishedRelationPath())
                || plan.targets().stream().noneMatch(
                        item -> item.equals(event.target()))
                || !RelatedProcessCoordinationPublisher.eventKey(
                        plan, event.target()).equals(eventKey)) {
            throw new IllegalArgumentException(
                    "跨实体流程协同 Outbox 快照不一致");
        }
    }

    private boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private BusinessConflictException conflict(
            String code,
            String message) {
        return new BusinessConflictException(code, message);
    }

    /** 写操作的幂等执行结果。 */
    public record ExecutionResult(
            String eventKey,
            Operation operation,
            String processInstanceId,
            String status) {
    }
}
