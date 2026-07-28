package com.workflow.entity.permission.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.entity.permission.api.response.EntityPermissionOptionDTO;
import com.workflow.entity.permission.application.EntityPermissionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实体权限选项目录接口。
 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class EntityPermissionOptionController {

    private final EntityPermissionCatalogService permissionCatalogService;

    @GetMapping("/entity-permission-options")
    public Result<List<EntityPermissionOptionDTO>> getOptions(
            @RequestParam String entityCode) {
        return Result.success(permissionCatalogService.getOptions(entityCode));
    }
}
