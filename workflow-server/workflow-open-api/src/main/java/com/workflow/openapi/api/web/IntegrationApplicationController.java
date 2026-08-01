package com.workflow.openapi.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.openapi.api.request.CreateIntegrationApplicationRequest;
import com.workflow.openapi.api.request.RevokeIntegrationCredentialRequest;
import com.workflow.openapi.api.request.RotateIntegrationCredentialRequest;
import com.workflow.openapi.api.request.UpdateIntegrationAccessRequest;
import com.workflow.openapi.api.request.UpdateIntegrationProcessContractsRequest;
import com.workflow.openapi.api.request.UpdateIntegrationStatusRequest;
import com.workflow.openapi.api.response.IntegrationApplicationView;
import com.workflow.openapi.api.response.IntegrationManagementCapabilitiesView;
import com.workflow.openapi.api.response.IntegrationProcessContractView;
import com.workflow.openapi.api.response.IssuedIntegrationCredentialView;
import com.workflow.openapi.application.IntegrationApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration-applications")
@RequiredArgsConstructor
public class IntegrationApplicationController {

    private final IntegrationApplicationService service;
    private final Environment environment;

    @GetMapping("/capabilities")
    @RequiresPermission("system:integration:view")
    public Result<IntegrationManagementCapabilitiesView> capabilities() {
        return Result.success(new IntegrationManagementCapabilitiesView(
                enabled("workflow.open-api.enabled"),
                enabled("workflow.open-api.webhook.enabled"),
                enabled("workflow.integration.connector.http.enabled")));
    }

    @GetMapping
    @RequiresPermission("system:integration:view")
    public Result<List<IntegrationApplicationView>> list() {
        return Result.success(service.list());
    }

    @PostMapping
    @RequiresPermission("system:integration:manage")
    public Result<IssuedIntegrationCredentialView> create(
            @Valid @RequestBody CreateIntegrationApplicationRequest request) {
        return Result.success(service.create(request));
    }

    @PostMapping("/{applicationId}/access/update")
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationApplicationView> updateAccess(
            @PathVariable String applicationId,
            @Valid @RequestBody UpdateIntegrationAccessRequest request) {
        return Result.success(service.updateAccess(applicationId, request));
    }

    @PostMapping("/{applicationId}/status")
    @RequiresPermission("system:integration:manage")
    public Result<IntegrationApplicationView> updateStatus(
            @PathVariable String applicationId,
            @Valid @RequestBody UpdateIntegrationStatusRequest request) {
        return Result.success(service.updateStatus(applicationId, request));
    }

    @GetMapping("/{applicationId}/process-contracts")
    @RequiresPermission("system:integration:view")
    public Result<List<IntegrationProcessContractView>>
            listProcessContracts(
                    @PathVariable String applicationId) {
        return Result.success(
                service.listProcessContracts(applicationId));
    }

    @PostMapping("/{applicationId}/process-contracts/update")
    @RequiresPermission("system:integration:manage")
    public Result<List<IntegrationProcessContractView>>
            updateProcessContracts(
                    @PathVariable String applicationId,
                    @Valid @RequestBody
                    UpdateIntegrationProcessContractsRequest request) {
        return Result.success(service.updateProcessContracts(
                applicationId,
                request));
    }

    @PostMapping("/{applicationId}/credentials/rotate")
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IssuedIntegrationCredentialView> rotateCredential(
            @PathVariable String applicationId,
            @Valid @RequestBody
            RotateIntegrationCredentialRequest request) {
        return Result.success(service.rotateCredential(
                applicationId,
                request));
    }

    @PostMapping("/{applicationId}/credentials/revoke")
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IntegrationApplicationView> revokeCredential(
            @PathVariable String applicationId,
            @Valid @RequestBody
            RevokeIntegrationCredentialRequest request) {
        return Result.success(service.revokeCredential(
                applicationId,
                request));
    }

    private boolean enabled(String property) {
        return environment.getProperty(
                property,
                Boolean.class,
                false);
    }
}
