package com.workflow.biz.project.service;

import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

import static com.workflow.biz.project.service.ProjectGovernanceValues.requireEntity;

/** Persists audit checkpoints for the member-change workflow. */
@Component
final class ProjectMemberChangeTraceSupport {

    private static final String REQUEST = "project_member_change_request";
    private final ProjectEntityMutationExecutor mutationExecutor;
    private final ProjectMemberChangeRuleSupport rules;

    /**
     * 初始化项目成员变更追踪支持，保存构造参数供后续方法使用。
     *
     * @param mutationExecutor 变更执行器依赖，保存到当前对象供后续业务方法调用
     * @param rules 规则集合依赖，保存到当前对象供后续业务方法调用
     */
    ProjectMemberChangeTraceSupport(
            ProjectEntityMutationExecutor mutationExecutor,
            ProjectMemberChangeRuleSupport rules) {
        this.mutationExecutor = mutationExecutor;
        this.rules = rules;
    }

    /**
     * 捕获{@code manager}{@code review}；结果供调用方的后续步骤使用。
     *
     * @param request 本次请求，后续经校验后用于捕获{@code manager}{@code review}
     * @param context 执行上下文，向后续{@code manager}{@code review}步骤传递身份、配置或状态
     * @return {@code manager}{@code review}键值结果，供调用方继续处理
     */
    Map<String, Object> captureManagerReview(
            EntityDataDTO request, FlowActionContext context) {
        requireEntity(request, REQUEST);
        LocalDateTime reviewedAt = LocalDateTime.now();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("manager_reviewed_at", reviewedAt);
        values.put("manager_review_operator_id", context.getOperatorId());
        mutationExecutor.update(REQUEST, request.getId(), Map.of("data", values));
        return Map.of("requestId", request.getId(), "reviewedAt", reviewedAt,
                "operatorId", String.valueOf(context.getOperatorId()));
    }

    /**
     * 记录决策；供后续追溯或审计使用。
     *
     * @param request 本次请求，后续经校验后用于记录决策
     * @param context 执行上下文，向后续决策步骤传递身份、配置或状态
     * @param decision 决策，作为 {@code trace.put} 的输入影响后续处理
     * @return 决策键值结果，供调用方继续处理
     */
    Map<String, Object> recordDecision(
            EntityDataDTO request, FlowActionContext context, String decision) {
        requireEntity(request, REQUEST);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("decision", decision == null || decision.isBlank() ? "UNKNOWN" : decision);
        // decision_trace 属于既有业务审计契约，仅替换取值来源，不改历史键名。
        trace.put("sequenceFlowId", context.getElementId());
        trace.put("sourceNodeId", context.getSourceNodeId());
        trace.put("targetNodeId", context.getTargetNodeId());
        trace.put("operatorId", context.getOperatorId());
        trace.put("recordedAt", LocalDateTime.now());
        mutationExecutor.update(REQUEST, request.getId(),
                Map.of("data", Map.of("decision_trace", rules.json(trace))));
        return trace;
    }
}
