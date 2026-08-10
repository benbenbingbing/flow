package com.workflow.entity.ui.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.ui.api.request.UiDataSourceDeleteRequest;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.api.request.UiDataSourceSaveRequest;
import com.workflow.entity.ui.api.response.UiAvailableOperation;
import com.workflow.entity.ui.application.UiAvailableOperationService;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiDataSourceService;
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
 * UI 数据源管理控制器。
 * <p>提供数据源目录查询、增删改、预览/执行及绑定校验接口；
 * 除执行接口（面向运行态）外，其余写操作与配置查询需全局配置权限。
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/ui-data-sources")
@RequiredArgsConstructor
public class UiDataSourceController {

    private final UiDataSourceService service;
    private final UiAvailableOperationService availableOperationService;
    private final UiConfigurationAccessService accessService;

    /**
     * 查询数据源目录（按类型/能力分类的可用数据源清单）。GET /api/ui-data-sources/catalog
     *
     * @return 数据源目录结构
     */
    @RequiresPermission("system:interface-service:list")
    @GetMapping("/catalog")
    public Result<Map<String, Object>> catalog() {
        return Result.success(service.catalog());
    }

    /**
     * 查询数据源定义列表。GET /api/ui-data-sources
     *
     * @param scopeType  作用范围类型（可选过滤）
     * @param scopeId    作用范围ID（可选过滤）
     * @param sourceType 数据源类型（可选过滤）
     * @return 匹配的数据源定义列表
     */
    @RequiresPermission("system:interface-service:list")
    @GetMapping
    public Result<List<UiDataSourceDefinition>> list(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId,
            @RequestParam(required = false) String sourceType) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.list(scopeType, scopeId, sourceType));
    }

    @GetMapping("/available-operations")
    public Result<List<UiAvailableOperation>> availableOperations(
            @RequestParam String ownerType,
            @RequestParam String ownerId,
            @RequestParam String bindingCode) {
        return Result.success(availableOperationService.available(
                ownerType,
                ownerId,
                bindingCode));
    }

    /**
     * 新增数据源定义。POST /api/ui-data-sources
     *
     * @param request 数据源保存请求（id 将被忽略并置空）
     * @return 保存后的数据源定义
     */
    @RequiresPermission("system:interface-service:update")
    @PostMapping
    public Result<UiDataSourceDefinition> create(
            @RequestBody UiDataSourceSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(null);
        return Result.success(service.save(request));
    }

    /**
     * 更新数据源定义。POST /api/ui-data-sources/{id}/update
     *
     * @param id      数据源ID
     * @param request 数据源保存请求（id 将被覆盖为路径 id）
     * @return 保存后的数据源定义
     */
    @RequiresPermission("system:interface-service:update")
    @PostMapping("/{id}/update")
    public Result<UiDataSourceDefinition> update(
            @PathVariable String id,
            @RequestBody UiDataSourceSaveRequest request) {
        accessService.requireGlobalConfigurationAccess();
        request.setId(id);
        return Result.success(service.save(request));
    }

    /**
     * 删除数据源定义（乐观锁校验）。POST /api/ui-data-sources/{id}/delete
     *
     * @param id      数据源ID
     * @param request 删除请求，携带期望版本号
     * @return 无数据返回
     */
    @RequiresPermission("system:interface-service:update")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(
            @PathVariable String id,
            @RequestBody UiDataSourceDeleteRequest request) {
        accessService.requireGlobalConfigurationAccess();
        service.delete(id, request.getExpectedRevision());
        return Result.success();
    }

    /**
     * 预览数据源执行结果（仅返回样本数据，不产生副作用）。POST /api/ui-data-sources/{id}/preview
     *
     * @param id      数据源ID
     * @param request 执行参数
     * @return 预览结果
     */
    @RequiresPermission("system:interface-service:test")
    @PostMapping("/{id}/preview")
    public Result<Object> preview(
            @PathVariable String id,
            @RequestBody UiDataSourceExecuteRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.preview(id, request));
    }

    @RequiresPermission("system:interface-service:list")
    @GetMapping("/{id}/operations")
    public Result<List<Map<String, Object>>> operations(
            @PathVariable String id) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.operations(id));
    }

    @RequiresPermission("system:interface-service:test")
    @PostMapping("/{id}/operations/{operationCode}/preview")
    public Result<Object> previewOperation(
            @PathVariable String id,
            @PathVariable String operationCode,
            @RequestBody UiDataSourceExecuteRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.previewOperation(
                id,
                operationCode,
                request));
    }

    /**
     * 校验数据源在指定用途下的绑定是否合法。POST /api/ui-data-sources/{id}/bindings/{usage}/validate
     *
     * @param id     数据源ID
     * @param usage  数据源用途标识
     * @return 校验结果（含合法性及诊断信息）
     */
    @RequiresPermission("system:interface-service:update")
    @PostMapping("/{id}/bindings/{usage}/validate")
    public Result<Map<String, Object>> validateBinding(
            @PathVariable String id,
            @PathVariable String usage) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.validateBinding(id, usage));
    }
}
