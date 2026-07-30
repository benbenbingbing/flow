package com.workflow.openapi.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.openapi.api.request.CreateIntegrationConnectorRequest;
import com.workflow.openapi.api.request.UpdateIntegrationConnectorRequest;
import com.workflow.openapi.api.request.TestIntegrationConnectorRequest;
import com.workflow.openapi.api.response.IntegrationConnectorView;
import com.workflow.openapi.connector.config.IntegrationConnectorAdministrationService;
import com.workflow.openapi.connector.config.IntegrationConnectorTestService;
import com.workflow.contracts.integration.IntegrationResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/integration-applications/{applicationId}/connectors")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "workflow.integration.connector.http.enabled",
        havingValue = "true")
public class IntegrationConnectorController {

    private final IntegrationConnectorAdministrationService service;
    private final IntegrationConnectorTestService testService;

    @GetMapping
    @RequiresPermission("system:integration:view")
    public Result<List<IntegrationConnectorView>> list(
            @PathVariable String applicationId) {
        return Result.success(service.list(applicationId));
    }

    @PostMapping
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationConnectorView> create(
            @PathVariable String applicationId,
            @Valid @RequestBody
            CreateIntegrationConnectorRequest request) {
        return Result.success(service.create(applicationId, request));
    }

    @PostMapping("/{configId}/update")
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationConnectorView> update(
            @PathVariable String applicationId,
            @PathVariable String configId,
            @Valid @RequestBody
            UpdateIntegrationConnectorRequest request) {
        return Result.success(service.update(
                applicationId,
                configId,
                request));
    }

    @PostMapping("/{configId}/test")
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationResult> test(
            @PathVariable String applicationId,
            @PathVariable String configId,
            @Valid @RequestBody
            TestIntegrationConnectorRequest request) {
        return Result.success(testService.test(
                applicationId,
                configId,
                request));
    }
}
