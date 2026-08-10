package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.Result;
import com.workflow.entity.ui.api.request.UiComponentTemplateSaveRequest;
import com.workflow.entity.ui.api.request.UiComponentTemplateUpgradeRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplate;
import com.workflow.entity.ui.infrastructure.persistence.record.UiComponentTemplateVersion;
import com.workflow.entity.ui.application.UiComponentTemplateService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * UI 组件模板管理控制器。
 * <p>提供组件模板的查询、保存、快照读取及版本型模板升级接口，
 * 所有操作需全局配置权限。列表列模板只用于初始化，通过当前快照接口读取，
 * 不提供版本历史、创建版本和升级能力。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-component-templates")
@RequiredArgsConstructor
public class UiComponentTemplateController {

    private final UiComponentTemplateService service;
    private final UiConfigurationAccessService accessService;

    /**
     * 查询组件模板列表。GET /api/ui-component-templates
     *
     * @param templateType 模板类型（可选过滤）
     * @return 匹配的组件模板列表
     */
    @GetMapping
    @RequiresPermission("system:list-column-template:view")
    public Result<List<UiComponentTemplate>> list(
            @RequestParam(required = false) String templateType) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.list(templateType));
    }

    /**
     * 保存组件模板（新增或更新）。POST /api/ui-component-templates
     *
     * @param request 模板保存请求
     * @return 保存后的组件模板
     */
    @PostMapping
    @RequiresPermission("system:list-column-template:manage")
    public Result<UiComponentTemplate> save(
            @RequestBody UiComponentTemplateSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.save(request));
    }

    /**
     * 读取组件模板当前快照。GET /api/ui-component-templates/{id}/snapshot
     *
     * @param id 模板ID
     * @return 当前模板快照
     */
    @GetMapping("/{id}/snapshot")
    @RequiresPermission("system:list-column-template:view")
    public Result<Map<String, Object>> currentSnapshot(
            @PathVariable String id) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.currentSnapshot(id));
    }

    /**
     * 查询版本型组件模板的版本历史，列表列模板不支持。
     *
     * @param id 模板ID
     * @return 模板版本列表
     */
    @GetMapping("/{id}/versions")
    @RequiresPermission("system:list-column-template:view")
    public Result<List<UiComponentTemplateVersion>> versions(
            @PathVariable String id) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.versions(id));
    }

    /**
     * 基于现有快照为版本型组件模板创建新版本。
     *
     * @param id      模板ID
     * @param request 模板保存请求（取其快照与描述）
     * @return 新建的模板版本
     */
    @PostMapping("/{id}/versions")
    @RequiresPermission("system:list-column-template:manage")
    public Result<UiComponentTemplateVersion> createVersion(
            @PathVariable String id,
            @RequestBody UiComponentTemplateSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.createVersion(
                id, request.getSnapshot(), request.getDescription()));
    }

    /**
     * 将引用版本型模板的配置升级到指定新版本。
     *
     * @param id      模板ID
     * @param request 升级请求（含目标版本与策略）
     * @return 升级结果报告
     */
    @PostMapping("/{id}/upgrade")
    @RequiresPermission("system:list-column-template:manage")
    public Result<Map<String, Object>> upgrade(
            @PathVariable String id,
            @RequestBody UiComponentTemplateUpgradeRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.upgrade(id, request));
    }
}
