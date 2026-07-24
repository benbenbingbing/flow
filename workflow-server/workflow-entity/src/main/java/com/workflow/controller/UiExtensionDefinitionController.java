package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.UiExtensionDefinition;
import com.workflow.service.UiConfigurationAccessService;
import com.workflow.service.UiExtensionDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UI 扩展定义管理控制器。
 * <p>提供扩展定义的查询、新增、更新接口，所有写操作需全局配置权限。
 */
@RestController
@RequestMapping("/api/ui-extensions")
@RequiredArgsConstructor
public class UiExtensionDefinitionController {

    private final UiExtensionDefinitionService service;
    private final UiConfigurationAccessService accessService;

    /**
     * 查询扩展定义列表。GET /api/ui-extensions
     *
     * @param extensionType 扩展类型（可选过滤）
     * @param extensionKey  扩展标识（可选过滤）
     * @param status        状态（可选过滤）
     * @return 匹配的扩展定义列表
     */
    @GetMapping
    public Result<List<UiExtensionDefinition>> list(
            @RequestParam(required = false) String extensionType,
            @RequestParam(required = false) String extensionKey,
            @RequestParam(required = false) String status) {
        return Result.success(service.list(
                extensionType, extensionKey, status));
    }

    /**
     * 新增扩展定义。POST /api/ui-extensions
     *
     * @param request 扩展定义保存请求（id 将被忽略并置空）
     * @return 保存后的扩展定义
     */
    @PostMapping
    public Result<UiExtensionDefinition> create(
            @RequestBody UiExtensionDefinitionSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(null);
        return Result.success(service.save(request));
    }

    /**
     * 更新扩展定义。POST /api/ui-extensions/{id}
     *
     * @param id      扩展定义ID
     * @param request 扩展定义保存请求（id 将被覆盖为路径 id）
     * @return 保存后的扩展定义
     */
    @PostMapping("/{id}")
    public Result<UiExtensionDefinition> update(
            @PathVariable String id,
            @RequestBody UiExtensionDefinitionSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(id);
        return Result.success(service.save(request));
    }
}
