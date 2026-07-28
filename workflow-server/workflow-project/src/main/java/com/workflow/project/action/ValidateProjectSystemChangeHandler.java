package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectGovernanceService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Executes project-system relationship and removal dependency gates in the start transaction.
 */
@Component("validateProjectSystemChangeHandler")
public class ValidateProjectSystemChangeHandler implements FlowActionHandler {

    private final ProjectGovernanceService governanceService;

    public ValidateProjectSystemChangeHandler(ProjectGovernanceService governanceService) {
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
        if (!(entityData instanceof EntityDataDTO request)) {
            throw new IllegalStateException("Project-system change data is unavailable.");
        }
        Map<String, Object> result =
                governanceService.validateProjectSystemChange(request);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "CROSS_ENTITY_VALIDATED",
                "Validated current link, responsible members and removal blockers.",
                result);
    }
}
