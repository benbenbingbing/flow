package com.workflow.openapi.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.openapi.api.request.CreateIntegrationWorkflowScenarioRequest;
import com.workflow.openapi.api.request.UpdateIntegrationWorkflowScenarioRequest;
import com.workflow.openapi.api.response.IntegrationWorkflowScenarioView;
import com.workflow.openapi.api.response.IntegrationWorkflowScenarioValidationView;
import com.workflow.openapi.application.IntegrationWorkflowScenarioService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration-applications/{applicationId}/scenarios")
@RequiredArgsConstructor
public class IntegrationWorkflowScenarioController {

    private final IntegrationWorkflowScenarioService service;

    @GetMapping
    @RequiresPermission("system:integration:view")
    public Result<List<IntegrationWorkflowScenarioView>> list(
            @PathVariable String applicationId) {
        return Result.success(service.list(applicationId));
    }

    @PostMapping
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationWorkflowScenarioView> create(
            @PathVariable String applicationId,
            @Valid @RequestBody CreateIntegrationWorkflowScenarioRequest request) {
        return Result.success(service.create(applicationId, request));
    }

    @PostMapping("/validate")
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationWorkflowScenarioValidationView> validate(
            @PathVariable String applicationId,
            @Valid @RequestBody CreateIntegrationWorkflowScenarioRequest request) {
        return Result.success(service.validate(applicationId, request));
    }

    @PostMapping("/{scenarioKey}")
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationWorkflowScenarioView> update(
            @PathVariable String applicationId,
            @PathVariable String scenarioKey,
            @Valid @RequestBody UpdateIntegrationWorkflowScenarioRequest request) {
        return Result.success(service.update(applicationId, scenarioKey, request));
    }

    @PostMapping("/{scenarioKey}/disable")
    @RequiresPermission("system:integration:manage")
    public Result<Void> disable(
            @PathVariable String applicationId,
            @PathVariable String scenarioKey,
            @RequestBody @Valid ScenarioRevisionRequest request) {
        service.disable(applicationId, scenarioKey, request.expectedRevision());
        return Result.success(null);
    }

    @PostMapping("/{scenarioKey}/publish")
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationWorkflowScenarioView> publish(
            @PathVariable String applicationId,
            @PathVariable String scenarioKey,
            @RequestBody @Valid ScenarioRevisionRequest request) {
        return Result.success(service.publish(
                applicationId, scenarioKey, request.expectedRevision()));
    }

    public record ScenarioRevisionRequest(
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Positive Long expectedRevision) {
    }
}
