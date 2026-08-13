package com.workflow.project.action;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionExecutionMode;
import com.workflow.contracts.action.FlowActionTriggerTiming;
import com.workflow.contracts.action.TypedFlowActionHandler;
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
        implements TypedFlowActionHandler<
        RecordProjectMemberDecisionHandler.Parameters> {

    private final ProjectMemberChangeService service;

    public RecordProjectMemberDecisionHandler(
            ProjectMemberChangeService service) {
        this.service = service;
    }

    @Override
    public Class<Parameters> getParamType() {
        return Parameters.class;
    }

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of(
                FlowActionTriggerTiming.TRANSITION_TAKEN
                        .name());
    }

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of(
                FlowActionExecutionMode.IN_TRANSACTION
                        .name());
    }

    @Override
    public String recommendedExecutionMode() {
        return FlowActionExecutionMode.IN_TRANSACTION.name();
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
    public void execute(
            FlowActionContext context,
            Parameters parameters) {
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Map<String, Object> result =
                service.recordDecision(
                        request,
                        context,
                        parameters == null
                                ? null
                                : parameters.decision());
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "FINAL_DECISION_RECORDED",
                "Recorded the selected final approval transition.",
                result);
    }

    /**
     * 项目成员变更决策动作参数。
     *
     * @param decision 被选中审批连线代表的业务决策编码；该值会连同连线、节点和
     *                 操作人信息写入成员变更申请的 {@code decision_trace}，
     *                 未提供时由业务服务记录为 {@code UNKNOWN}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parameters(String decision) {
    }
}
