package com.workflow.embed.management.api;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.embed.management.application.EmbedOptionsQueryService;
import com.workflow.embed.management.domain.EmbedManagementModel.ApplicationOption;
import com.workflow.embed.management.domain.EmbedManagementModel.IdentityProviderOption;
import com.workflow.embed.management.domain.EmbedManagementModel.OptionsFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import java.time.ZoneOffset;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 沿用普通 Flow 身份鉴权，仅向有权进入任一嵌入配置工作区的用户开放名称选择必需的信息。 */
@RestController
@RequestMapping("/api/embed-management/v1/options")
@RequiresPermission(value = {
        "system:embed:view",
        "system:embed:identity-manage"
}, any = true)
public class EmbedOptionsManagementController {

    private final EmbedOptionsQueryService service;

    public EmbedOptionsManagementController(EmbedOptionsQueryService service) {
        this.service = service;
    }

    /** 按 ID、名称或 clientId 分页搜索应用；status 省略时可回显已禁用、撤销或过期项。 */
    @GetMapping("/applications")
    public ApiResponse<PageResult<EmbedManagementViews.ApplicationOptionView>> applications(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<ApplicationOption> page = service.applications(new OptionsFilter(keyword,
                EmbedViewManagementController.enumOrNull(SecurityStatus.class, status),
                pageNum, pageSize));
        return ApiResponse.success(new PageResult<>(page.records().stream()
                .map(option -> new EmbedManagementViews.ApplicationOptionView(
                        option.id(), option.name(), option.clientId(), option.status().name(),
                        option.expiresAt() == null ? null
                                : option.expiresAt().toInstant(ZoneOffset.UTC),
                        option.embedLaunchReady()))
                .toList(), page.total(), page.pageNum(), page.pageSize()));
    }

    /** 按 ID 或名称分页搜索身份源；完整验证配置仍由身份管理权限保护。 */
    @GetMapping("/identity-providers")
    public ApiResponse<PageResult<EmbedManagementViews.IdentityProviderOptionView>> identityProviders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<IdentityProviderOption> page = service.identityProviders(new OptionsFilter(keyword,
                EmbedViewManagementController.enumOrNull(SecurityStatus.class, status),
                pageNum, pageSize));
        return ApiResponse.success(new PageResult<>(page.records().stream()
                .map(option -> new EmbedManagementViews.IdentityProviderOptionView(
                        option.id(), option.name(), option.type().name(), option.status().name()))
                .toList(), page.total(), page.pageNum(), page.pageSize()));
    }
}
