package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectMemberChangeService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * F07 批准后生效成员、投入、权限和角色交接结果。
 */
@Component("applyProjectMemberChangeHandler")
public class ApplyProjectMemberChangeHandler
        implements FlowActionHandler {

    private final ProjectMemberChangeService service;

    public ApplyProjectMemberChangeHandler(
            ProjectMemberChangeService service) {
        this.service = service;
    }

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_COMPLETED");
    }

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("AFTER_COMMIT");
    }

    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    @Override
    public boolean retryable() {
        return true;
    }

    @Override
    public void execute(FlowActionContext context) {
        if (!"project_member_change_request".equals(
                context.getEntityCode())
                || !"approve".equals(String.valueOf(
                context.getVariable("approved")))) {
            context.addExecutionTrace(
                    "SKIPPED",
                    "Project member change was not approved.");
            return;
        }
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Map<String, Object> result =
                service.applyChange(request, context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "MEMBER_CHANGE_APPLIED",
                "Applied member, access and role handover changes.",
                result);
    }
}
