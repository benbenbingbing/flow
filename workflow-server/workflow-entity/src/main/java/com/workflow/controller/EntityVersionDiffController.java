package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.EntityVersionDiffDTO;
import com.workflow.service.EntityVersionDiffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实体版本差异对比控制器
 */
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
        return ApiResponse.success(versionDiffService.compareVersions(entityId, version - 1, version));
    }
}
