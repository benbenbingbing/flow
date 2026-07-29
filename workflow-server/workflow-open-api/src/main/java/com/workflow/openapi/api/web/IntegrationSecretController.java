package com.workflow.openapi.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.openapi.api.request.CreateIntegrationSecretRequest;
import com.workflow.openapi.api.request.RevokeIntegrationSecretRequest;
import com.workflow.openapi.api.request.RotateIntegrationSecretRequest;
import com.workflow.openapi.api.response.IntegrationSecretView;
import com.workflow.openapi.api.response.IssuedIntegrationSecretView;
import com.workflow.openapi.connector.secret.IntegrationSecretAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration-applications/{applicationId}/secrets")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "workflow.integration.connector.http.enabled",
        havingValue = "true")
public class IntegrationSecretController {

    private final IntegrationSecretAdministrationService service;

    @GetMapping
    @RequiresPermission("system:integration:view")
    public Result<List<IntegrationSecretView>> list(
            @PathVariable String applicationId) {
        return Result.success(service.list(applicationId));
    }

    @PostMapping
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IssuedIntegrationSecretView> create(
            @PathVariable String applicationId,
            @Valid @RequestBody CreateIntegrationSecretRequest request) {
        return Result.success(service.create(applicationId, request));
    }

    @PostMapping("/{secretName}/rotate")
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IssuedIntegrationSecretView> rotate(
            @PathVariable String applicationId,
            @PathVariable String secretName,
            @Valid @RequestBody RotateIntegrationSecretRequest request) {
        return Result.success(service.rotate(
                applicationId,
                secretName,
                request));
    }

    @PostMapping("/{secretName}/revoke")
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IntegrationSecretView> revoke(
            @PathVariable String applicationId,
            @PathVariable String secretName,
            @Valid @RequestBody RevokeIntegrationSecretRequest request) {
        return Result.success(service.revoke(
                applicationId,
                secretName,
                request));
    }

    @DeleteMapping("/{secretId}")
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IntegrationSecretView> destroy(
            @PathVariable String applicationId,
            @PathVariable String secretId) {
        return Result.success(service.destroy(applicationId, secretId));
    }
}
