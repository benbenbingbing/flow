package com.workflow.admin.externalsystem.api.web;

import com.workflow.admin.externalsystem.api.request.ExternalSystemRequests;
import com.workflow.admin.externalsystem.api.response.ExternalSystemViews;
import com.workflow.admin.externalsystem.application.ExternalSystemService;
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

/**
 * 外部系统配置管理接口。
 *
 * <p>本接口只维护外部系统基本信息和参数，具体对接接口由各业务模块定制开发。</p>
 */
@RestController
@RequestMapping("/api/system/external-system")
@RequiresPermission(
        value = {
                "system:external-system:view",
                "system:external-system:manage"
        },
        any = true)
@RequiredArgsConstructor
public class ExternalSystemController {

    private final ExternalSystemService externalSystemService;

    /**
     * 分页查询外部系统摘要。
     *
     * @param pageNum 页码
     * @param pageSize 每页条数，服务端最大为 100
     * @param systemName 系统名称模糊筛选
     * @param systemCode 系统编码模糊筛选
     * @param status 状态筛选，仅支持 0/1
     * @return 外部系统分页摘要，不含参数值
     */
    @GetMapping("/page")
    public Result<PageResult<ExternalSystemViews.ExternalSystemSummary>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String status) {
        return Result.success(externalSystemService.page(
                pageNum, pageSize, systemName, systemCode, status));
    }

    /**
     * 查询外部系统基本信息和完整参数集合。
     *
     * @param id 外部系统 ID
     * @return 外部系统详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("system:external-system:manage")
    public Result<ExternalSystemViews.ExternalSystemDetail> get(
            @PathVariable String id) {
        return Result.success(externalSystemService.get(id));
    }

    /**
     * 创建外部系统并原子保存参数集合。
     *
     * @param request 系统基本信息和参数集合
     * @return 创建后的详情
     */
    @PostMapping
    @RequiresPermission("system:external-system:manage")
    public Result<ExternalSystemViews.ExternalSystemDetail> create(
            @RequestBody ExternalSystemRequests.CreateExternalSystem request) {
        return Result.success(externalSystemService.create(request));
    }

    /**
     * 更新可变基本信息并原子替换参数集合；系统编码不可修改。
     *
     * @param id 外部系统 ID
     * @param request 可变基本信息和完整参数集合
     * @return 更新后的详情
     */
    @PostMapping("/{id}/update")
    @RequiresPermission("system:external-system:manage")
    public Result<ExternalSystemViews.ExternalSystemDetail> update(
            @PathVariable String id,
            @RequestBody ExternalSystemRequests.UpdateExternalSystem request) {
        return Result.success(externalSystemService.update(id, request));
    }

    /**
     * 启用或禁用外部系统。
     *
     * @param id 外部系统 ID
     * @param request 目标状态
     * @return 空成功响应
     */
    @PostMapping("/{id}/status")
    @RequiresPermission("system:external-system:manage")
    public Result<Void> changeStatus(
            @PathVariable String id,
            @RequestBody ExternalSystemRequests.ChangeExternalSystemStatus request) {
        externalSystemService.changeStatus(id, request);
        return Result.success();
    }

    /**
     * 逻辑删除外部系统及其全部活动参数。
     *
     * @param id 外部系统 ID
     * @param request 调用方读取到的期望版本
     * @return 空成功响应
     */
    @PostMapping("/{id}/delete")
    @RequiresPermission("system:external-system:manage")
    public Result<Void> delete(
            @PathVariable String id,
            @RequestBody ExternalSystemRequests.DeleteExternalSystem request) {
        externalSystemService.delete(id, request);
        return Result.success();
    }
}
