package com.workflow.entity.definition.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.ApiResponse;
import com.workflow.entity.definition.api.response.EntityPublishHistoryDTO;
import com.workflow.entity.definition.application.EntityPublishHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体发布版本历史控制器
 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/entity-publish-history")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EntityPublishHistoryController {

    private final EntityPublishHistoryService historyService;

    /**
     * 获取实体的版本历史列表
     */
    @GetMapping("/entity/{entityId}")
    public ApiResponse<List<EntityPublishHistoryDTO>> getVersionHistory(@PathVariable String entityId) {
        return ApiResponse.success(historyService.getVersionHistory(entityId));
    }

    /**
     * 获取实体的最新版本
     */
    @GetMapping("/entity/{entityId}/latest")
    public ApiResponse<EntityPublishHistoryDTO> getLatestVersion(@PathVariable String entityId) {
        return ApiResponse.success(historyService.getLatestVersion(entityId));
    }

    /**
     * 获取版本详情
     */
    @GetMapping("/{historyId}")
    public ApiResponse<EntityPublishHistoryDTO> getVersionDetail(@PathVariable String historyId) {
        return ApiResponse.success(historyService.getVersionDetail(historyId));
    }

    /**
     * 比较两个版本
     */
    @GetMapping("/compare")
    public ApiResponse<String> compareVersions(
            @RequestParam String version1,
            @RequestParam String version2) {
        return ApiResponse.success(historyService.compareVersions(version1, version2));
    }
}
