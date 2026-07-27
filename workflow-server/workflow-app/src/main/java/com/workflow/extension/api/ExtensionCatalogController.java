package com.workflow.extension.api;

import com.workflow.common.ForbiddenException;
import com.workflow.common.PageResult;
import com.workflow.common.PermissionUtil;
import com.workflow.common.Result;
import com.workflow.extension.application.ExtensionCatalogService;
import com.workflow.extension.dto.ExtensionCatalogItemDTO;
import com.workflow.service.CurrentUserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统一扩展目录接口。
 */
@RestController
@RequestMapping("/api/extension-catalog")
@RequiredArgsConstructor
public class ExtensionCatalogController {

    private final ExtensionCatalogService service;
    private final CurrentUserRoleService currentUserRoleService;

    @GetMapping("/manage")
    public Result<PageResult<ExtensionCatalogItemDTO>> manage(
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
    public Result<List<ExtensionCatalogItemDTO>> options(
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
