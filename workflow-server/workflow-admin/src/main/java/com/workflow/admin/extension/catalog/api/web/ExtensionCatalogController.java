package com.workflow.admin.extension.catalog.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.extension.catalog.api.response.ExtensionCatalogItem;
import com.workflow.admin.extension.catalog.application.ExtensionCatalogService;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统一扩展目录接口。
 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/extension-catalog")
@RequiredArgsConstructor
public class ExtensionCatalogController {

    private final ExtensionCatalogService service;
    private final CurrentUserRoleService currentUserRoleService;

    /**
     * 处理{@code manage}，并将结果传给后续步骤。
     *
     * @param capabilityType 能力类型标识，决定后续{@code manage}采用的处理分支
     * @param keyword 关键字，作为 {@code Result.success} 的输入影响后续处理
     * @param status 状态标识，决定后续{@code manage}采用的处理分支
     * @param implementationOrigin 实现来源，作为 {@code Result.success} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的{@code manage}结果，供调用方继续处理
     */
    @GetMapping("/manage")
    public Result<PageResult<ExtensionCatalogItem>> manage(
            @RequestParam(required = false) String capabilityType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String implementationOrigin,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        requireListAccess();
        return Result.success(service.manage(
                capabilityType,
                keyword,
                status,
                implementationOrigin,
                pageNum,
                pageSize));
    }

    /**
     * 处理选项，并将结果传给后续步骤。
     *
     * @param capabilityType 能力类型标识，决定后续选项采用的处理分支
     * @param keyword 关键字，作为 {@code Result.success} 的输入影响后续处理
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param processConfigId 流程配置ID，后续用于处理选项时定位或关联目标
     * @param usage 使用场景，作为 {@code Result.success} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的选项结果，供调用方继续处理
     */
    @GetMapping("/options")
    public Result<List<ExtensionCatalogItem>> options(
            @RequestParam(required = false) String capabilityType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String processConfigId,
            @RequestParam(required = false) String usage,
            @RequestParam(required = false) String entityCode) {
        return Result.success(service.options(
                capabilityType,
                keyword,
                limit,
                processConfigId,
                usage,
                entityCode));
    }

    /**
     * 校验并获取列表访问；不满足约束时阻止后续处理。
     *
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private void requireListAccess() {
        if (currentUserRoleService.isAdministrator()
                || PermissionUtil.hasPermission("system:extension:list")) {
            return;
        }
        throw new ForbiddenException("没有扩展管理查看权限");
    }
}
