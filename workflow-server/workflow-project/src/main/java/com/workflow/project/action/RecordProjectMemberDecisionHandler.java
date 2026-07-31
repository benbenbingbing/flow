package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectMemberChangeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 最终顺序流被选中时记录批准决策，验证 SEQUENCE_FLOW 自定义动作。
 */
@Component("recordProjectMemberDecisionHandler")
public class RecordProjectMemberDecisionHandler
        implements FlowActionHandler {

    private final ProjectMemberChangeService service;

    public RecordProjectMemberDecisionHandler(
            ProjectMemberChangeService service) {
        this.service = service;
    }

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("TRANSITION_TAKEN");
    }

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("IN_TRANSACTION");
    }

    @Override
    public String recommendedExecutionMode() {
        return "IN_TRANSACTION";
    }

    @Override
    public Map<String, Object> extraParamSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "decision", Map.of(
                                "type", "string",
                                "title", "决策编码")),
                "required", List.of("decision"));
    }

    @Override
    public void execute(FlowActionContext context) {
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Object configuredDecision =
                context.getExtraParams() == null
                        ? null
                        : context.getExtraParams().get(
                        "decision");
        Map<String, Object> result =
                service.recordDecision(
                        request,
                        context,
                        configuredDecision == null
                                ? null
                                : String.valueOf(
                                configuredDecision));
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "FINAL_DECISION_RECORDED",
                "Recorded the selected final approval transition.",
                result);
    }
}
