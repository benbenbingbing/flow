package com.workflow.openapi.api.web;

import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.openapi.api.request.CreateWebhookEndpointRequest;
import com.workflow.openapi.api.request.RotateWebhookSecretRequest;
import com.workflow.openapi.api.request.UpdateWebhookEndpointRequest;
import com.workflow.openapi.api.request.ReplayWebhookDeliveryRequest;
import com.workflow.openapi.api.response.IssuedWebhookSecretView;
import com.workflow.openapi.api.response.WebhookEndpointView;
import com.workflow.openapi.api.response.WebhookDeliveryView;
import com.workflow.openapi.webhook.application.WebhookAdministrationService;
import com.workflow.openapi.webhook.application.WebhookDeliveryAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration-applications/{applicationId}/webhooks")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "workflow.open-api.webhook.enabled",
        havingValue = "true")
public class WebhookAdministrationController {

    private final WebhookAdministrationService service;
    private final WebhookDeliveryAdministrationService
            deliveryService;

    @GetMapping
    @RequiresPermission("system:integration:view")
    public Result<List<WebhookEndpointView>> list(
            @PathVariable String applicationId) {
        return Result.success(service.list(applicationId));
    }

    @PostMapping
    @RequiresPermission("system:integration:manage")
    public Result<IssuedWebhookSecretView> create(
            @PathVariable String applicationId,
            @Valid @RequestBody
            CreateWebhookEndpointRequest request) {
        return Result.success(service.create(
                applicationId,
                request));
    }

    @PutMapping("/{endpointId}")
    @RequiresPermission("system:integration:manage")
    public Result<WebhookEndpointView> update(
            @PathVariable String applicationId,
            @PathVariable String endpointId,
            @Valid @RequestBody
            UpdateWebhookEndpointRequest request) {
        return Result.success(service.update(
                applicationId,
                endpointId,
                request));
    }

    @PostMapping("/{endpointId}/secret/rotate")
    @RequiresPermission("system:integration:secret-rotate")
    public Result<IssuedWebhookSecretView> rotateSecret(
            @PathVariable String applicationId,
            @PathVariable String endpointId,
            @Valid @RequestBody
            RotateWebhookSecretRequest request) {
        return Result.success(service.rotateSecret(
                applicationId,
                endpointId,
                request));
    }

    @GetMapping("/deliveries")
    @RequiresPermission("system:integration:view")
    public Result<List<WebhookDeliveryView>> listDeliveries(
            @PathVariable String applicationId) {
        return Result.success(
                deliveryService.list(applicationId));
    }

    @PostMapping("/deliveries/{deliveryId}/replay")
    @RequiresPermission("system:integration:delivery-replay")
    public Result<WebhookDeliveryView> replay(
            @PathVariable String applicationId,
            @PathVariable String deliveryId,
            @Valid @RequestBody
            ReplayWebhookDeliveryRequest request) {
        return Result.success(deliveryService.replay(
                applicationId,
                deliveryId,
                request));
    }
}
