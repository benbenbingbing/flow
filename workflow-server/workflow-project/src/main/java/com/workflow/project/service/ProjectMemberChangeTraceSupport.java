package com.workflow.project.service;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

import static com.workflow.project.service.ProjectGovernanceValues.requireEntity;

/** Persists audit checkpoints for the member-change workflow. */
@Component
final class ProjectMemberChangeTraceSupport {

    private static final String REQUEST = "project_member_change_request";
    private final ProjectEntityMutationExecutor mutationExecutor;
    private final ProjectMemberChangeRuleSupport rules;

    ProjectMemberChangeTraceSupport(
            ProjectEntityMutationExecutor mutationExecutor,
            ProjectMemberChangeRuleSupport rules) {
        this.mutationExecutor = mutationExecutor;
        this.rules = rules;
    }

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

    Map<String, Object> recordDecision(
            EntityDataDTO request, FlowActionContext context, String decision) {
        requireEntity(request, REQUEST);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("decision", decision == null || decision.isBlank() ? "UNKNOWN" : decision);
        trace.put("sequenceFlowId", context.getSequenceFlowId());
        trace.put("sourceNodeId", context.getSourceNodeId());
        trace.put("targetNodeId", context.getTargetNodeId());
        trace.put("operatorId", context.getOperatorId());
        trace.put("recordedAt", LocalDateTime.now());
        mutationExecutor.update(REQUEST, request.getId(),
                Map.of("data", Map.of("decision_trace", rules.json(trace))));
        return trace;
    }
}
