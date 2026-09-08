package com.workflow.embed.management.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.embed.management.application.EmbedIdentityAdministrationService;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingState;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateBindingCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateProviderCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.JwksMode;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateProviderCommand;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Identity Provider 与精确外部身份 Binding 管理 API。 */
@RestController
@RequestMapping("/api/embed-management/v1")
@RequiresPermission("system:embed:identity-manage")
public class EmbedIdentityManagementController {

    private final EmbedIdentityAdministrationService service;
    private final ObjectMapper objectMapper;

    public EmbedIdentityManagementController(
            EmbedIdentityAdministrationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/identity-providers")
    public ApiResponse<PageResult<EmbedManagementViews.ProviderView>> providers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(keyword, pageNum, pageSize);
        Page<ProviderState> page = service.providers(new ProviderFilter(
                EmbedViewManagementController.trim(keyword),
                EmbedViewManagementController.enumOrNull(SecurityStatus.class, status),
                EmbedViewManagementController.enumOrNull(ProviderType.class, type),
                pageNum, pageSize));
        return ApiResponse.success(new PageResult<>(
                page.records().stream().map(this::provider).toList(), page.total(),
                page.pageNum(), page.pageSize()));
    }

    @PostMapping("/identity-providers")
    public ApiResponse<EmbedManagementViews.ProviderView> createProvider(
            @Valid @RequestBody EmbedManagementRequests.CreateProviderRequest request) {
        ProviderState provider = service.createProvider(new CreateProviderCommand(
                request.name(),
                EmbedViewManagementController.enumRequired(ProviderType.class, request.type()),
                request.issuer(), request.audiences(), request.subjectNamespace(),
                request.algorithms(),
                EmbedViewManagementController.enumOrNull(JwksMode.class, request.jwksMode()),
                request.jwks(), request.jwksUrl(), request.clockSkewSeconds(),
                request.maxAssertionLifetimeSeconds()));
        return ApiResponse.success(provider(provider));
    }

    @GetMapping("/identity-providers/{providerId}")
    public ApiResponse<EmbedManagementViews.ProviderView> provider(
            @PathVariable String providerId) {
        return ApiResponse.success(provider(service.provider(providerId)));
    }

    @PatchMapping("/identity-providers/{providerId}")
    public ApiResponse<EmbedManagementViews.ProviderView> updateProvider(
            @PathVariable String providerId,
            @Valid @RequestBody EmbedManagementRequests.UpdateProviderRequest request) {
        ProviderState provider = service.updateProvider(providerId, new UpdateProviderCommand(
                request.expectedVersion(), request.name(), request.issuer(), request.audiences(),
                request.subjectNamespace(), request.algorithms(),
                EmbedViewManagementController.enumOrNull(JwksMode.class, request.jwksMode()),
                request.jwks(), request.jwksUrl(), request.clockSkewSeconds(),
                request.maxAssertionLifetimeSeconds()));
        return ApiResponse.success(provider(provider));
    }

    @PostMapping("/identity-providers/{providerId}/status")
    public ApiResponse<EmbedManagementViews.ProviderView> providerStatus(
            @PathVariable String providerId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(provider(service.changeProviderStatus(
                providerId, new ChangeStatusCommand(
                        request.expectedVersion(), request.status(), request.reason()), false)));
    }

    @PostMapping("/identity-providers/{providerId}/revoke")
    public ApiResponse<EmbedManagementViews.ProviderView> revokeProvider(
            @PathVariable String providerId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(provider(service.changeProviderStatus(
                providerId, new ChangeStatusCommand(
                        request.expectedVersion(), "REVOKED", request.reason()), true)));
    }

    @PostMapping("/identity-providers/{providerId}/rotate-key")
    public ApiResponse<EmbedManagementViews.ProviderView> rotateProviderKey(
            @PathVariable String providerId,
            @Valid @RequestBody EmbedManagementRequests.RotateProviderKeyRequest request) {
        return ApiResponse.success(provider(service.rotateProviderKey(
                providerId, request.expectedVersion(), request.jwks())));
    }

    @GetMapping("/identity-bindings")
    public ApiResponse<PageResult<EmbedManagementViews.BindingView>> bindings(
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) String identityProviderId,
            @RequestParam(required = false) String flowUserId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(null, pageNum, pageSize);
        Page<BindingState> page = service.bindings(new BindingFilter(
                EmbedViewManagementController.trim(applicationId),
                EmbedViewManagementController.trim(identityProviderId),
                EmbedViewManagementController.trim(flowUserId),
                EmbedViewManagementController.enumOrNull(SecurityStatus.class, status),
                pageNum, pageSize));
        return ApiResponse.success(new PageResult<>(
                page.records().stream()
                        .map(EmbedIdentityManagementController::binding).toList(), page.total(),
                page.pageNum(), page.pageSize()));
    }

    @PostMapping("/identity-bindings/lookup")
    public ApiResponse<EmbedManagementViews.BindingView> lookupBinding(
            @Valid @RequestBody EmbedManagementRequests.LookupBindingRequest request) {
        BindingState binding = service.lookupBinding(
                request.applicationId(), request.identityProviderId(), request.externalSubject());
        return ApiResponse.success(binding == null ? null : binding(binding));
    }

    @PostMapping("/identity-bindings")
    public ApiResponse<EmbedManagementViews.BindingView> createBinding(
            @Valid @RequestBody EmbedManagementRequests.CreateBindingRequest request) {
        BindingState binding = service.createBinding(new CreateBindingCommand(
                request.applicationId(), request.identityProviderId(), request.externalSubject(),
                request.flowUserId(), toLocal(request.effectiveAt()),
                toLocal(request.expiresAt()), request.remark()));
        return ApiResponse.success(binding(binding));
    }

    @PostMapping("/identity-bindings/{bindingId}/status")
    public ApiResponse<EmbedManagementViews.BindingView> bindingStatus(
            @PathVariable String bindingId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(binding(service.changeBindingStatus(
                bindingId, new ChangeStatusCommand(
                        request.expectedVersion(), request.status(), request.reason()), false)));
    }

    @PostMapping("/identity-bindings/{bindingId}/revoke")
    public ApiResponse<EmbedManagementViews.BindingView> revokeBinding(
            @PathVariable String bindingId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(binding(service.changeBindingStatus(
                bindingId, new ChangeStatusCommand(
                        request.expectedVersion(), "REVOKED", request.reason()), true)));
    }

    private EmbedManagementViews.ProviderView provider(ProviderState value) {
        return new EmbedManagementViews.ProviderView(
                value.id(), value.name(), value.type().name(), value.status().name(),
                value.issuer(), value.subjectNamespace(), json(value.audiencesJson()),
                json(value.algorithmsJson()),
                value.jwksMode() == null ? null : value.jwksMode().name(),
                jsonNullable(value.jwksJson()), value.jwksUrl(),
                value.clockSkewSeconds(), value.maxAssertionLifetimeSeconds(),
                value.keyVersion(), value.lockVersion(), value.securityVersion(),
                value.createTime(), value.updateTime());
    }

    private static EmbedManagementViews.BindingView binding(BindingState value) {
        return new EmbedManagementViews.BindingView(
                value.id(), value.applicationId(), value.identityProviderId(),
                value.subjectHint(), value.flowUserId(), value.flowUserReady(), value.status().name(),
                value.bindingVersion(), value.effectiveAt(), value.expiresAt(),
                value.createTime(), value.updateTime());
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed Provider JSON 数据损坏", exception);
        }
    }

    private JsonNode jsonNullable(String value) {
        return value == null ? null : json(value);
    }

    private static LocalDateTime toLocal(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void validatePage(String keyword, int pageNum, int pageSize) {
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("keyword 最大长度为 100");
        }
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNum 必须大于 0，pageSize 必须为 1 到 100");
        }
    }
}
