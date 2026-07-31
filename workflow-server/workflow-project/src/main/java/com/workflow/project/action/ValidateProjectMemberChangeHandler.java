package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.project.service.ProjectMemberChangeService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 在 F07 启动事务内执行成员、投入、角色、权限和交接门禁。
 */
@Component("validateProjectMemberChangeHandler")
public class ValidateProjectMemberChangeHandler
        implements FlowActionHandler {

    private final ProjectMemberChangeService service;

    public ValidateProjectMemberChangeHandler(
            ProjectMemberChangeService service) {
        this.service = service;
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
            throw new IllegalStateException(
                    "Project member change data is unavailable.");
        }
        Map<String, Object> result =
                service.validateChange(request, context);
        context.setExecutionResult(result);
        context.addExecutionTrace(
                "MEMBER_CHANGE_VALIDATED",
                "Validated member status, allocation, roles, access and handover.",
                result);
    }
}
