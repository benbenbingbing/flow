package com.workflow.admin.identity.position.api.web;

import com.workflow.admin.identity.position.api.request.PositionRequests;
import com.workflow.admin.identity.position.api.response.PositionViews;
import com.workflow.admin.identity.position.application.PositionDefinitionService;
import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.core.security.RequiresPermission;
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
 * 全局职务定义管理接口。
 */
@RestController
@RequestMapping("/api/system/position")
@RequiresPermission("system:position:view")
@RequiredArgsConstructor
public class SysPositionController {

    private final PositionDefinitionService service;

    /**
     * 分页查询系统位置；查询结果供调用方展示或继续处理。
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，作为 {@code Result.success} 的输入影响后续处理
     * @param applicableUnitType 适用单元类型标识，决定后续系统位置采用的处理分支
     * @param holderMode 持有者模式标识，决定后续系统位置采用的处理分支
     * @param status 状态标识，决定后续系统位置采用的处理分支
     * @return 符合条件的位置视图结果，供调用方继续处理
     */
    @GetMapping("/page")
    public Result<PageResult<PositionViews.PositionView>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String applicableUnitType,
            @RequestParam(required = false) String holderMode,
            @RequestParam(required = false) String status) {
        return Result.success(service.page(
                pageNum, pageSize, keyword, applicableUnitType,
                holderMode, status));
    }

    /**
     * 处理启用，并将结果传给后续步骤。
     *
     * @param applicableUnitType 适用单元类型标识，决定后续启用采用的处理分支
     * @return 处理后的启用结果，供调用方继续处理
     */
    @GetMapping("/enabled")
    @RequiresPermission(
            value = {"system:position:view", "process:definition:view",
                    "process:definition:manage", "system:position:assign"},
            any = true)
    public Result<List<PositionViews.PositionView>> enabled(
            @RequestParam(required = false) String applicableUnitType) {
        return Result.success(service.enabled(applicableUnitType));
    }

    /**
     * 读取{@code result<position}{@code views.position}{@code view>}；结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的{@code result<position}{@code views.position}{@code view>}结果，供调用方继续处理
     */
    @GetMapping("/{id}")
    public Result<PositionViews.PositionView> get(@PathVariable String id) {
        return Result.success(service.get(id));
    }

    /**
     * 创建系统位置；结果供后续流程传递或持久化。
     *
     * @param request 本次请求，后续经校验后用于创建系统位置
     * @return 创建后的系统位置结果，供调用方继续处理
     */
    @PostMapping
    @RequiresPermission("system:position:manage")
    public Result<PositionViews.PositionView> create(
            @RequestBody PositionRequests.CreatePosition request) {
        return Result.success(service.create(request));
    }

    /**
     * 更新系统位置；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于更新系统位置
     * @return 更新后的系统位置结果，供调用方继续处理
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("system:position:manage")
    public Result<PositionViews.PositionView> update(
            @PathVariable String id,
            @RequestBody PositionRequests.UpdatePosition request) {
        return Result.success(service.update(id, request));
    }

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于处理状态
     * @return 处理后的状态结果，供调用方继续处理
     */
    @PostMapping("/{id}/status")
    @RequiresPermission("system:position:manage")
    public Result<Void> status(
            @PathVariable String id,
            @RequestBody PositionRequests.ChangePositionStatus request) {
        service.changeStatus(id, request);
        return Result.success();
    }

    /**
     * 删除系统位置；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于删除系统位置
     * @return 删除后的系统位置结果，供调用方继续处理
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("system:position:manage")
    public Result<Void> delete(
            @PathVariable String id,
            @RequestBody(required = false) PositionRequests.DeletePosition request) {
        service.delete(id, request);
        return Result.success();
    }
}
