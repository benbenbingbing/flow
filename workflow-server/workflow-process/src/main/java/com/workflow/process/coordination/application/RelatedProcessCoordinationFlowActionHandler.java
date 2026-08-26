package com.workflow.process.coordination.application;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.action.FlowActionFailurePolicy;
import com.workflow.contracts.action.TypedFlowActionHandler;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Command;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Operation;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.TargetImpact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在现有“流程动作”中接入跨实体流程协同能力。
 *
 * <p>该 Bean 只是开发人员可注册的通用处理器，不引入新的用户配置
 * 概念。只读校验必须配置为“事务内 + 失败回滚”，否则不可能
 * 阻止宿主流程，处理器会在任何查询或副作用前拒绝这种误配。</p>
 */
@Component
@RequiredArgsConstructor
public class RelatedProcessCoordinationFlowActionHandler
        implements TypedFlowActionHandler<Command> {

    private final RelatedProcessCoordinationPlanService planService;
    private final RelatedProcessCoordinationPublisher publisher;

    @Override
    public Class<Command> getParamType() {
        return Command.class;
    }

    @Override
    public void execute(FlowActionContext context, Command command) {
        requireBlockingPolicy(context, command);
        RelatedProcessCoordinationPlan plan = planService.plan(
                context, command);
        Map<String, Object> preview = preview(plan);
        context.addExecutionTrace(
                "RELATED_PROCESS_IMPACT_PREVIEW",
                "已完成关联流程影响预览",
                preview);
        if (plan.writeOperation()) {
            int queued = publisher.publish(plan, command);
            preview.put("status", "QUEUED");
            preview.put("queuedTargets", queued);
            context.addExecutionTrace(
                    "RELATED_PROCESS_OUTBOX_QUEUED",
                    "关联流程写操作已按目标进入 Outbox",
                    Map.of("queuedTargets", queued));
        } else {
            preview.put("status", "ASSERTED");
        }
        context.setExecutionResult(preview);
    }

    @Override
    public boolean retryable() {
        // 只读操作无副作用；写操作仅发布幂等 Outbox 事件。
        return true;
    }

    private void requireBlockingPolicy(
            FlowActionContext context,
            Command command) {
        if (command == null || command.operation() == null) {
            throw new IllegalArgumentException("流程协同操作不能为空");
        }
        boolean readOnly = command.operation()
                == Operation.ASSERT_RELATED_STATE
                || command.operation()
                        == Operation.WAIT_RELATED_PROCESSES;
        if (readOnly
                && (!FlowActionExecutionMode.IN_TRANSACTION.name()
                        .equalsIgnoreCase(context.getExecutionMode())
                || !FlowActionFailurePolicy.ROLLBACK.name()
                        .equalsIgnoreCase(context.getFailurePolicy()))) {
            throw new IllegalArgumentException(
                    "关联流程前置校验必须使用事务内执行且失败回滚");
        }
    }

    private Map<String, Object> preview(
            RelatedProcessCoordinationPlan plan) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", plan.planId());
        result.put("operation", plan.operation().name());
        result.put("relatedRecords", plan.targets().size());
        result.put("activeProcesses", plan.activeCount());
        result.put("terminalProcesses", plan.terminalCount());
        result.put("recordsWithoutProcess", plan.noProcessCount());
        result.put("targets", plan.targets().stream()
                .map(this::targetPreview)
                .toList());
        return result;
    }

    private Map<String, Object> targetPreview(TargetImpact target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("entityCode", target.record().entityCode());
        item.put("recordId", target.record().recordId());
        item.put("processState", target.state().name());
        item.put("processInstanceId", target.processInstanceId());
        item.put("processVersionHistoryId",
                target.processVersionHistoryId());
        item.put("processVersion", target.processVersion());
        item.put("activeActivityIds", target.activeActivityIds());
        return item;
    }
}
