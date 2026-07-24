package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.UiComponentTemplateSaveRequest;
import com.workflow.dto.UiComponentTemplateUpgradeRequest;
import com.workflow.entity.UiComponentTemplate;
import com.workflow.entity.UiComponentTemplateVersion;
import com.workflow.service.UiComponentTemplateService;
import com.workflow.service.UiConfigurationAccessService;
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
 * <p>提供组件模板的查询、保存、版本列表、创建版本及升级接口，所有操作需全局配置权限。
 */
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
    public Result<UiComponentTemplate> save(
            @RequestBody UiComponentTemplateSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.save(request));
    }

    /**
     * 查询组件模板的历史版本列表。GET /api/ui-component-templates/{id}/versions
     *
     * @param id 模板ID
     * @return 模板版本列表
     */
    @GetMapping("/{id}/versions")
    public Result<List<UiComponentTemplateVersion>> versions(
            @PathVariable String id) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.versions(id));
    }

    /**
     * 基于现有快照为组件模板创建新版本。POST /api/ui-component-templates/{id}/versions
     *
     * @param id      模板ID
     * @param request 模板保存请求（取其快照与描述）
     * @return 新建的模板版本
     */
    @PostMapping("/{id}/versions")
    public Result<UiComponentTemplateVersion> createVersion(
            @PathVariable String id,
            @RequestBody UiComponentTemplateSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.createVersion(
                id, request.getSnapshot(), request.getDescription()));
    }

    /**
     * 将引用该模板的配置升级到指定新版本。POST /api/ui-component-templates/{id}/upgrade
     *
     * @param id      模板ID
     * @param request 升级请求（含目标版本与策略）
     * @return 升级结果报告
     */
    @PostMapping("/{id}/upgrade")
    public Result<Map<String, Object>> upgrade(
            @PathVariable String id,
            @RequestBody UiComponentTemplateUpgradeRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.upgrade(id, request));
    }
}
