package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectMemberChangeService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 项目经理节点完成后写入业务检查点，验证 NODE 级自定义动作。
 */
@Component("captureProjectMemberManagerReviewHandler")
public class CaptureProjectMemberManagerReviewHandler
        implements FlowActionHandler {

    private final ProjectMemberChangeService service;

    public CaptureProjectMemberManagerReviewHandler(
            ProjectMemberChangeService service) {
        this.service = service;
    }

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("NODE_COMPLETED");
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
    public void execute(FlowActionContext context) {
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Map<String, Object> result =
                service.captureManagerReview(
                        request, context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "MANAGER_REVIEW_CAPTURED",
                "Captured the project manager review checkpoint.",
                result);
    }
}
