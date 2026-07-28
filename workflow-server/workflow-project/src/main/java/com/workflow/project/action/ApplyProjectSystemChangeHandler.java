package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectGovernanceService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Applies an approved ADD, UPDATE or REMOVE operation to the governed relationship entity.
 */
@Component("applyProjectSystemChangeHandler")
public class ApplyProjectSystemChangeHandler implements FlowActionHandler {

    private final ProjectGovernanceService governanceService;

    public ApplyProjectSystemChangeHandler(ProjectGovernanceService governanceService) {
        this.governanceService = governanceService;
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
    public void execute(FlowActionContext context) {
        if (!"project_system_change_request".equals(context.getEntityCode())
                || !"approve".equals(String.valueOf(context.getVariable("approved")))) {
            context.addExecutionTrace("SKIPPED", "Project-system change was not approved.");
            return;
        }
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException("Project-system change data is unavailable.");
        }
        Map<String, Object> result =
                governanceService.applyProjectSystemChange(request);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "RELATIONSHIP_APPLIED",
                "Applied the approved project-system relationship change.",
                result);
    }
}
