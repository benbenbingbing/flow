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

    /**
     * 初始化嵌入式身份管理控制器，保存构造参数供后续方法使用。
     *
     * @param service 服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedIdentityManagementController(
            EmbedIdentityAdministrationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理提供者集合，并将结果传给后续步骤。
     *
     * @param keyword 关键字，作为 {@code validatePage} 的输入影响后续处理
     * @param status 状态标识，决定后续提供者集合采用的处理分支
     * @param type 类型标识，决定后续提供者集合采用的处理分支
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的提供者集合结果，供调用方继续处理
     */
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

    /**
     * 创建提供者；结果供后续流程传递或持久化。
     *
     * @param request 本次请求，后续经校验后用于创建提供者
     * @return 创建后的提供者结果，供调用方继续处理
     */
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

    /**
     * 处理提供者，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理提供者时定位或关联目标
     * @return 处理后的提供者结果，供调用方继续处理
     */
    @GetMapping("/identity-providers/{providerId}")
    public ApiResponse<EmbedManagementViews.ProviderView> provider(
            @PathVariable String providerId) {
        return ApiResponse.success(provider(service.provider(providerId)));
    }

    /**
     * 更新提供者；后续读取或执行将使用更新后的状态。
     *
     * @param providerId 提供者ID，后续用于更新提供者时定位或关联目标
     * @param request 本次请求，后续经校验后用于更新提供者
     * @return 更新后的提供者结果，供调用方继续处理
     */
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

    /**
     * 处理提供者状态，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理提供者状态时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理提供者状态
     * @return 处理后的提供者状态结果，供调用方继续处理
     */
    @PostMapping("/identity-providers/{providerId}/status")
    public ApiResponse<EmbedManagementViews.ProviderView> providerStatus(
            @PathVariable String providerId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(provider(service.changeProviderStatus(
                providerId, new ChangeStatusCommand(
                        request.expectedVersion(), request.status(), request.reason()), false)));
    }

    /**
     * 撤销提供者；后续读取或执行将使用更新后的状态。
     *
     * @param providerId 提供者ID，后续用于撤销提供者时定位或关联目标
     * @param request 本次请求，后续经校验后用于撤销提供者
     * @return 撤销后的提供者结果，供调用方继续处理
     */
    @PostMapping("/identity-providers/{providerId}/revoke")
    public ApiResponse<EmbedManagementViews.ProviderView> revokeProvider(
            @PathVariable String providerId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(provider(service.changeProviderStatus(
                providerId, new ChangeStatusCommand(
                        request.expectedVersion(), "REVOKED", request.reason()), true)));
    }

    /**
     * 处理轮换提供者键，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理轮换提供者键时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理轮换提供者键
     * @return 处理后的轮换提供者键结果，供调用方继续处理
     */
    @PostMapping("/identity-providers/{providerId}/rotate-key")
    public ApiResponse<EmbedManagementViews.ProviderView> rotateProviderKey(
            @PathVariable String providerId,
            @Valid @RequestBody EmbedManagementRequests.RotateProviderKeyRequest request) {
        return ApiResponse.success(provider(service.rotateProviderKey(
                providerId, request.expectedVersion(), request.jwks())));
    }

    /**
     * 处理绑定集合，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理绑定集合时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于处理绑定集合时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于处理绑定集合时定位或关联目标
     * @param status 状态标识，决定后续绑定集合采用的处理分支
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的绑定集合结果，供调用方继续处理
     */
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

    /**
     * 查找绑定；结果供调用方的后续步骤使用。
     *
     * @param request 本次请求，后续经校验后用于查找绑定
     * @return 查找后的绑定结果，供调用方继续处理
     */
    @PostMapping("/identity-bindings/lookup")
    public ApiResponse<EmbedManagementViews.BindingView> lookupBinding(
            @Valid @RequestBody EmbedManagementRequests.LookupBindingRequest request) {
        BindingState binding = service.lookupBinding(
                request.applicationId(), request.identityProviderId(), request.externalSubject());
        return ApiResponse.success(binding == null ? null : binding(binding));
    }

    /**
     * 创建绑定；结果供后续流程传递或持久化。
     *
     * @param request 本次请求，后续经校验后用于创建绑定
     * @return 创建后的绑定结果，供调用方继续处理
     */
    @PostMapping("/identity-bindings")
    public ApiResponse<EmbedManagementViews.BindingView> createBinding(
            @Valid @RequestBody EmbedManagementRequests.CreateBindingRequest request) {
        BindingState binding = service.createBinding(new CreateBindingCommand(
                request.applicationId(), request.identityProviderId(), request.externalSubject(),
                request.flowUserId(), toLocal(request.effectiveAt()),
                toLocal(request.expiresAt()), request.remark()));
        return ApiResponse.success(binding(binding));
    }

    /**
     * 处理绑定状态，并将结果传给后续步骤。
     *
     * @param bindingId 绑定ID，后续用于处理绑定状态时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理绑定状态
     * @return 处理后的绑定状态结果，供调用方继续处理
     */
    @PostMapping("/identity-bindings/{bindingId}/status")
    public ApiResponse<EmbedManagementViews.BindingView> bindingStatus(
            @PathVariable String bindingId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(binding(service.changeBindingStatus(
                bindingId, new ChangeStatusCommand(
                        request.expectedVersion(), request.status(), request.reason()), false)));
    }

    /**
     * 撤销绑定；后续读取或执行将使用更新后的状态。
     *
     * @param bindingId 绑定ID，后续用于撤销绑定时定位或关联目标
     * @param request 本次请求，后续经校验后用于撤销绑定
     * @return 撤销后的绑定结果，供调用方继续处理
     */
    @PostMapping("/identity-bindings/{bindingId}/revoke")
    public ApiResponse<EmbedManagementViews.BindingView> revokeBinding(
            @PathVariable String bindingId,
            @Valid @RequestBody EmbedManagementRequests.ChangeStatusRequest request) {
        return ApiResponse.success(binding(service.changeBindingStatus(
                bindingId, new ChangeStatusCommand(
                        request.expectedVersion(), "REVOKED", request.reason()), true)));
    }

    /**
     * 处理提供者，并将结果传给后续步骤。
     *
     * @param value 待处理提供者的原始输入，结果供调用方继续使用
     * @return 处理后的提供者结果，供调用方继续处理
     */
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

    /**
     * 处理绑定，并将结果传给后续步骤。
     *
     * @param value 待处理绑定的原始输入，结果供调用方继续使用
     * @return 处理后的绑定结果，供调用方继续处理
     */
    private static EmbedManagementViews.BindingView binding(BindingState value) {
        return new EmbedManagementViews.BindingView(
                value.id(), value.applicationId(), value.identityProviderId(),
                value.subjectHint(), value.flowUserId(), value.flowUserReady(), value.status().name(),
                value.bindingVersion(), value.effectiveAt(), value.expiresAt(),
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
            throw new IllegalStateException("Embed Provider JSON 数据损坏", exception);
        }
    }

    /**
     * 处理JSON可空，并将结果传给后续步骤。
     *
     * @param value 待处理JSON可空的原始输入，结果供调用方继续使用
     * @return 处理后的JSON可空结果，供调用方继续处理
     */
    private JsonNode jsonNullable(String value) {
        return value == null ? null : json(value);
    }

    /**
     * 转换为本地；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为本地的原始输入，结果供调用方继续使用
     * @return 转换为后的本地结果，供调用方继续处理
     */
    private static LocalDateTime toLocal(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 校验嵌入式身份管理分页；不满足约束时阻止后续处理。
     *
     * @param keyword 关键字，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void validatePage(String keyword, int pageNum, int pageSize) {
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("keyword 最大长度为 100");
        }
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNum 必须大于 0，pageSize 必须为 1 到 100");
        }
    }
}
