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

    /**
     * 读取选项；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体权限选项结果，供调用方继续处理
     */
    @GetMapping("/entity-permission-options")
    public Result<List<EntityPermissionOptionDTO>> getOptions(
            @RequestParam String entityCode) {
        return Result.success(permissionCatalogService.getOptions(entityCode));
    }
}
