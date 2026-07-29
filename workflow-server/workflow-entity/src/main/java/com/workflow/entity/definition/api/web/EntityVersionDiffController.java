package com.workflow.entity.definition.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.ApiResponse;
import com.workflow.entity.definition.api.response.EntityVersionDiffDTO;
import com.workflow.entity.definition.application.EntityVersionDiffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体版本差异对比控制器
 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/entity-version-diff")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityVersionDiffController {

    private final EntityVersionDiffService versionDiffService;

    /**
     * 获取即将发布的版本差异预览
     * 用于发布前查看本次将要发布的内容
     */
    @GetMapping("/pending/{entityId}")
    public ApiResponse<EntityVersionDiffDTO> getPendingPublishDiff(@PathVariable String entityId) {
        return ApiResponse.success(versionDiffService.getPendingPublishDiff(entityId));
    }

    /**
     * 比较两个版本之间的差异
     */
    @GetMapping("/compare/{entityId}")
    public ApiResponse<EntityVersionDiffDTO> compareVersions(
            @PathVariable String entityId,
            @RequestParam Integer versionFrom,
            @RequestParam Integer versionTo) {
        return ApiResponse.success(versionDiffService.compareVersions(entityId, versionFrom, versionTo));
    }

    /**
     * 比较指定版本与上一版本的差异
     */
    @GetMapping("/compare/{entityId}/{version}")
    public ApiResponse<EntityVersionDiffDTO> compareWithPrevious(
            @PathVariable String entityId,
            @PathVariable Integer version) {
        if (version == null || version < 2) {
            throw new IllegalArgumentException("版本号必须大于等于 2 才能比较上一版本");
        }
        return ApiResponse.success(versionDiffService.compareVersions(entityId, version - 1, version));
    }
}
