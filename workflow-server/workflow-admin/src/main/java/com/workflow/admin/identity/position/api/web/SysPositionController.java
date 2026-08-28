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

    @GetMapping("/enabled")
    @RequiresPermission(
            value = {"system:position:view", "process:definition:view",
                    "process:definition:manage", "system:position:assign"},
            any = true)
    public Result<List<PositionViews.PositionView>> enabled(
            @RequestParam(required = false) String applicableUnitType) {
        return Result.success(service.enabled(applicableUnitType));
    }

    @GetMapping("/{id}")
    public Result<PositionViews.PositionView> get(@PathVariable String id) {
        return Result.success(service.get(id));
    }

    @PostMapping
    @RequiresPermission("system:position:manage")
    public Result<PositionViews.PositionView> create(
            @RequestBody PositionRequests.CreatePosition request) {
        return Result.success(service.create(request));
    }

    @PostMapping("/{id}/update")
    @RequiresPermission("system:position:manage")
    public Result<PositionViews.PositionView> update(
            @PathVariable String id,
            @RequestBody PositionRequests.UpdatePosition request) {
        return Result.success(service.update(id, request));
    }

    @PostMapping("/{id}/status")
    @RequiresPermission("system:position:manage")
    public Result<Void> status(
            @PathVariable String id,
            @RequestBody PositionRequests.ChangePositionStatus request) {
        service.changeStatus(id, request);
        return Result.success();
    }

    @PostMapping("/{id}/delete")
    @RequiresPermission("system:position:manage")
    public Result<Void> delete(
            @PathVariable String id,
            @RequestBody(required = false) PositionRequests.DeletePosition request) {
        service.delete(id, request);
        return Result.success();
    }
}
