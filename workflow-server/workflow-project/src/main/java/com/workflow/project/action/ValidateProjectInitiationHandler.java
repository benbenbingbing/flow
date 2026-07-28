package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectGovernanceService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Blocks process start when project cross-entity prerequisites are not satisfied.
 */
@Component("validateProjectInitiationHandler")
public class ValidateProjectInitiationHandler implements FlowActionHandler {

    private final ProjectGovernanceService governanceService;

    public ValidateProjectInitiationHandler(ProjectGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_STARTED");
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
        if (!(entityData instanceof EntityDataDTO project)) {
            throw new IllegalStateException("Project initiation data is unavailable.");
        }
        Map<String, Object> result =
                governanceService.validateProjectInitiation(project);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "CROSS_ENTITY_VALIDATED",
                "Validated requirement allocation, system status and key project data.",
                result);
    }
}
