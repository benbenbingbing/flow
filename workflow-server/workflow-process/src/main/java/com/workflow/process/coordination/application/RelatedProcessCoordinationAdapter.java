package com.workflow.process.coordination.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.instance.port.RelatedProcessCoordinationPort;
import com.workflow.process.action.application.PublishedFlowActionInvocation;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RelatedProcessCoordinationAdapter implements RelatedProcessCoordinationPort {
    private final RelatedProcessCoordinationFlowActionHandler handler;
    private final RelatedProcessCoordinationPlanService planner;
    private final RelatedProcessCoordinationPublisher publisher;
    private final ObjectMapper json;

    @Override
    @Transactional(readOnly = true)
    public Impact preview() {
        return coordinate(false).impact();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result execute() {
        return coordinate(true);
    }

    private Result coordinate(boolean publish) {
        var invocation = PublishedFlowActionInvocation.requireCurrent();
        var context = new FlowActionContext();
        context.setActionId(invocation.actionId());
        context.setProcessVersionId(invocation.processVersionId());
        context.setProcessDefinitionId(invocation.processDefinitionId());
        context.setProcessInstanceId(invocation.processInstanceId());
        context.setEntityCode(invocation.entityCode());
        context.setEntityDataId(invocation.recordId());
        context.setOperatorId(invocation.operatorId());
        context.setIdempotencyKey(invocation.executionKey());
        context.setExecutionMode(invocation.executionMode());
        context.setFailurePolicy(invocation.failurePolicy());
        var command = configuration(invocation.publishedParameters());
        handler.requireBlockingPolicy(context, command);
        var plan = planner.plan(context, command);
        var targets = plan.targets().stream()
                .map(t -> new Target(t.record().entityCode(), t.record().recordId(), t.processInstanceId(),
                        t.state().name()))
                .toList();
        var impact = new Impact(plan.planId(), plan.operation().name(), targets, plan.activeCount(),
                plan.terminalCount(), plan.noProcessCount());
        return new Result(impact, publish && plan.writeOperation() ? publisher.publish(plan, command) : 0);
    }

    /** 只读取发布配置中的固定关系路径和动作，避免运行时参数扩大影响范围。 */
    private Command configuration(String parameters) {
        if (StringUtils.hasText(parameters)) {
            try {
                var root = json.readTree(parameters);
                var coordination = root == null ? null : root.get("relatedProcessCoordination");
                if (coordination != null && coordination.isObject()) return json.treeToValue(coordination, Command.class);
            } catch (JsonProcessingException error) {
                throw new IllegalArgumentException("关联流程协调发布配置无效", error);
            }
        }
        throw new IllegalArgumentException("流程动作未配置 relatedProcessCoordination 发布参数");
    }
}
