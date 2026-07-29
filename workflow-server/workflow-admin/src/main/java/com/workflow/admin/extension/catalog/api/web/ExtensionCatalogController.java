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

    @GetMapping("/manage")
    public Result<PageResult<ExtensionCatalogItem>> manage(
            @RequestParam(required = false) String capabilityType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        requireListAccess();
        return Result.success(service.manage(
                capabilityType,
                keyword,
                status,
                pageNum,
                pageSize));
    }

    @GetMapping("/options")
    public Result<List<ExtensionCatalogItem>> options(
            @RequestParam(required = false) String capabilityType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String processConfigId,
            @RequestParam(required = false) String usage) {
        return Result.success(service.options(
                capabilityType,
                keyword,
                limit,
                processConfigId,
                usage));
    }

    private void requireListAccess() {
        if (currentUserRoleService.isAdministrator()
                || PermissionUtil.hasPermission("system:extension:list")) {
            return;
        }
        throw new ForbiddenException("没有扩展管理查看权限");
    }
}
