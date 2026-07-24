package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.UiDataSourceDeleteRequest;
import com.workflow.dto.UiDataSourceExecuteRequest;
import com.workflow.dto.UiDataSourceSaveRequest;
import com.workflow.entity.UiDataSourceDefinition;
import com.workflow.service.UiConfigurationAccessService;
import com.workflow.service.UiDataSourceService;
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
@RestController
@RequestMapping("/api/ui-data-sources")
@RequiredArgsConstructor
public class UiDataSourceController {

    private final UiDataSourceService service;
    private final UiConfigurationAccessService accessService;

    /**
     * 查询数据源目录（按类型/能力分类的可用数据源清单）。GET /api/ui-data-sources/catalog
     *
     * @return 数据源目录结构
     */
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
    @GetMapping
    public Result<List<UiDataSourceDefinition>> list(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId,
            @RequestParam(required = false) String sourceType) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.list(scopeType, scopeId, sourceType));
    }

    /**
     * 新增数据源定义。POST /api/ui-data-sources
     *
     * @param request 数据源保存请求（id 将被忽略并置空）
     * @return 保存后的数据源定义
     */
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
    @PostMapping("/{id}/preview")
    public Result<Object> preview(
            @PathVariable String id,
            @RequestBody UiDataSourceExecuteRequest request) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.preview(id, request));
    }

    /**
     * 运行态执行数据源。POST /api/ui-data-sources/{id}/execute
     *
     * @param id      数据源ID
     * @param request 执行参数
     * @return 执行结果
     */
    @PostMapping("/{id}/execute")
    public Result<Object> execute(
            @PathVariable String id,
            @RequestBody UiDataSourceExecuteRequest request) {
        return Result.success(service.execute(id, request));
    }

    /**
     * 校验数据源在指定用途下的绑定是否合法。POST /api/ui-data-sources/{id}/bindings/{usage}/validate
     *
     * @param id     数据源ID
     * @param usage  数据源用途标识
     * @return 校验结果（含合法性及诊断信息）
     */
    @PostMapping("/{id}/bindings/{usage}/validate")
    public Result<Map<String, Object>> validateBinding(
            @PathVariable String id,
            @PathVariable String usage) {
        accessService.requireGlobalConfigurationAccess();
        return Result.success(service.validateBinding(id, usage));
    }
}
