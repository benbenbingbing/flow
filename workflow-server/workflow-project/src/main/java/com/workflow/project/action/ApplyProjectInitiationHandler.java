package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectGovernanceService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Creates governed members and role assignments and activates initial links after approval.
 */
@Component("applyProjectInitiationHandler")
public class ApplyProjectInitiationHandler implements FlowActionHandler {

    private final ProjectGovernanceService governanceService;

    public ApplyProjectInitiationHandler(ProjectGovernanceService governanceService) {
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
        if (!"project".equals(context.getEntityCode())
                || !"approve".equals(String.valueOf(context.getVariable("approved")))) {
            context.addExecutionTrace("SKIPPED", "Project initiation was not approved.");
            return;
        }
        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO project)) {
            throw new IllegalStateException("Project initiation data is unavailable.");
        }
        Map<String, Object> result =
                governanceService.applyProjectInitiation(project);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "PROJECT_INITIALIZED",
                "Activated initial links and created governed members and roles.",
                result);
    }
}
