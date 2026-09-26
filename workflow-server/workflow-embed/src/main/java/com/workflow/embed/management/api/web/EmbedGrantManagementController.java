package com.workflow.embed.management.api.web;

import com.workflow.embed.management.api.request.EmbedManagementRequests;
import com.workflow.embed.management.api.response.EmbedManagementViews;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
import com.workflow.embed.management.application.EmbedGrantAdministrationService;
import com.workflow.embed.management.domain.EmbedManagementModel.Capability;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.UpsertGrantCommand;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * 初始化嵌入式授权管理控制器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedGrantManagementController(
            EmbedGrantAdministrationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /**
     * 列出嵌入式授权管理；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于列出嵌入式授权管理时定位或关联目标
     * @return 符合条件的嵌入式管理视图结果，供调用方继续处理
     */
    @GetMapping
    public ApiResponse<List<EmbedManagementViews.GrantView>> list(
            @PathVariable String viewId) {
        return ApiResponse.success(service.list(viewId).stream().map(this::view).toList());
    }

    /**
     * 处理新增或更新，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理新增或更新时定位或关联目标
     * @param applicationId 应用ID，后续用于处理新增或更新时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理新增或更新
     * @return 处理后的新增或更新结果，供调用方继续处理
     */
    @PostMapping("/{applicationId}")
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
                request.maxActiveSessionsPerUser(),
                request.maxSessionSeconds(),
                request.launchLimitPerMinute(),
                request.runtimeLimitPerMinute(),
                request.maxConcurrency(),
                toLocal(request.expiresAt())));
        return ApiResponse.success(view(grant));
    }

    /**
     * 处理变更状态，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理变更状态时定位或关联目标
     * @param applicationId 应用ID，后续用于处理变更状态时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理变更状态
     * @return 处理后的变更状态结果，供调用方继续处理
     */
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

    /**
     * 撤销嵌入式授权管理；后续读取或执行将使用更新后的状态。
     *
     * @param viewId 视图ID，后续用于撤销嵌入式授权管理时定位或关联目标
     * @param applicationId 应用ID，后续用于撤销嵌入式授权管理时定位或关联目标
     * @param request 本次请求，后续经校验后用于撤销嵌入式授权管理
     * @return 撤销后的嵌入式授权管理结果，供调用方继续处理
     */
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

    /**
     * 处理视图，并将结果传给后续步骤。
     *
     * @param value 待处理视图的原始输入，结果供调用方继续使用
     * @return 处理后的视图结果，供调用方继续处理
     */
    private EmbedManagementViews.GrantView view(GrantState value) {
        return new EmbedManagementViews.GrantView(
                value.id(), value.applicationId(), value.viewId(),
                value.identityProviderId(), value.status().name(),
                value.trustedSubjectAssertion(), json(value.capabilityCeilingJson()),
                value.allowedOrigins(), value.maxActiveSessionsPerUser(),
                value.maxSessionSeconds(), value.launchLimitPerMinute(),
                value.runtimeLimitPerMinute(), value.maxConcurrency(), value.expiresAt(),
                value.lockVersion(), value.securityVersion(),
                value.createTime(), value.updateTime());
    }

    /**
     * 处理JSON，并将结果传给后续步骤。
     *
     * @param value 待处理JSON的原始输入，结果供调用方继续使用
     * @return 处理后的JSON结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed Grant JSON 数据损坏", exception);
        }
    }

    /**
     * 转换为本地；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为本地的原始输入，结果供调用方继续使用
     * @return 转换为后的本地结果，供调用方继续处理
     */
    private static LocalDateTime toLocal(java.time.Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
