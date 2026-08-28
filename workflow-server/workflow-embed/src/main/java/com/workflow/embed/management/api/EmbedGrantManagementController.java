package com.workflow.embed.management.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
import com.workflow.embed.management.application.EmbedGrantAdministrationService;
import com.workflow.embed.management.domain.EmbedManagementModel.Capability;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.RevisionMode;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.UpsertGrantCommand;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Embed Application Grant 与精确 Origin 管理 API。 */
@RestController
@RequestMapping("/api/embed-management/v1/views/{viewId}/grants")
@RequiresPermission("system:embed:view")
public class EmbedGrantManagementController {

    private final EmbedGrantAdministrationService service;
    private final ObjectMapper objectMapper;

    public EmbedGrantManagementController(
            EmbedGrantAdministrationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<EmbedManagementViews.GrantView>> list(
            @PathVariable String viewId) {
        return ApiResponse.success(service.list(viewId).stream().map(this::view).toList());
    }

    @PutMapping("/{applicationId}")
    @RequiresPermission("system:embed:manage")
    public ApiResponse<EmbedManagementViews.GrantView> upsert(
            @PathVariable String viewId,
            @PathVariable String applicationId,
            @Valid @RequestBody EmbedManagementRequests.UpsertGrantRequest request) {
        List<Capability> capabilities = request.capabilityCeiling().stream()
                .map(value -> EmbedViewManagementController.enumRequired(Capability.class, value))
                .toList();
        GrantState grant = service.upsert(viewId, applicationId, new UpsertGrantCommand(
                request.expectedVersion(),
                request.status() == null ? SecurityStatus.ACTIVE
                        : EmbedViewManagementController.enumRequired(
                                SecurityStatus.class, request.status()),
                request.identityProviderId(),
                Boolean.TRUE.equals(request.trustedSubjectAssertion()),
                request.allowedOrigins(),
                capabilities,
                EmbedViewManagementController.enumRequired(
                        RevisionMode.class, request.revisionMode()),
                request.pinnedRevision(),
                request.maxActiveSessionsPerUser(),
                request.maxSessionSeconds(),
                request.launchLimitPerMinute(),
                request.runtimeLimitPerMinute(),
                request.maxConcurrency(),
                toLocal(request.expiresAt())));
        return ApiResponse.success(view(grant));
    }

    @PostMapping("/{applicationId}/status")
    @RequiresPermission("system:embed:manage")
    public ApiResponse<EmbedManagementViews.GrantView> changeStatus(
            @PathVariable String viewId,
            @PathVariable String applicationId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(view(service.changeStatus(
                viewId, applicationId,
                new ChangeStatusCommand(
                        request.expectedVersion(), request.status(), request.reason()),
                false)));
    }

    @PostMapping("/{applicationId}/revoke")
    @RequiresPermission("system:embed:manage")
    public ApiResponse<EmbedManagementViews.GrantView> revoke(
            @PathVariable String viewId,
            @PathVariable String applicationId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(view(service.changeStatus(
                viewId, applicationId,
                new ChangeStatusCommand(
                        request.expectedVersion(), "REVOKED", request.reason()),
                true)));
    }

    private EmbedManagementViews.GrantView view(GrantState value) {
        return new EmbedManagementViews.GrantView(
                value.id(), value.applicationId(), value.viewId(),
                value.identityProviderId(), value.status().name(),
                value.trustedSubjectAssertion(), value.revisionMode().name(),
                value.pinnedRevision(), json(value.capabilityCeilingJson()),
                value.allowedOrigins(), value.maxActiveSessionsPerUser(),
                value.maxSessionSeconds(), value.launchLimitPerMinute(),
                value.runtimeLimitPerMinute(), value.maxConcurrency(), value.expiresAt(),
                value.lockVersion(), value.securityVersion(),
                value.createTime(), value.updateTime());
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed Grant JSON 数据损坏", exception);
        }
    }

    private static LocalDateTime toLocal(java.time.Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
