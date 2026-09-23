package com.workflow.entity.definition.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
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
public class EntityPublishHistoryController {

    private final EntityPublishHistoryService historyService;

    /**
     * 获取实体的版本历史列表
     *
     * @param entityId 实体ID，后续用于读取版本历史时定位或关联目标
     * @return 符合条件的实体发布历史结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityId}")
    public ApiResponse<List<EntityPublishHistoryDTO>> getVersionHistory(@PathVariable String entityId) {
        return ApiResponse.success(historyService.getVersionHistory(entityId));
    }

    /**
     * 分页获取实体版本历史，支持前端弹窗滚动到底部后继续加载。
     *
     * @param entityId 实体定义 ID
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 版本号倒序排列的分页结果
     */
    @GetMapping("/entity/{entityId}/page")
    public ApiResponse<PageResult<EntityPublishHistoryDTO>> getVersionHistoryPage(
            @PathVariable String entityId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "5") Integer pageSize) {
        return ApiResponse.success(historyService.getVersionHistoryPage(
                entityId, pageNum, pageSize));
    }

    /**
     * 获取实体的最新版本
     *
     * @param entityId 实体ID，后续用于读取最新版本时定位或关联目标
     * @return 符合条件的API{@code response<entity}发布历史{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityId}/latest")
    public ApiResponse<EntityPublishHistoryDTO> getLatestVersion(@PathVariable String entityId) {
        return ApiResponse.success(historyService.getLatestVersion(entityId));
    }

    /**
     * 获取版本详情
     *
     * @param historyId 历史ID，后续用于读取版本详情时定位或关联目标
     * @return 符合条件的API{@code response<entity}发布历史{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/{historyId}")
    public ApiResponse<EntityPublishHistoryDTO> getVersionDetail(@PathVariable String historyId) {
        return ApiResponse.success(historyService.getVersionDetail(historyId));
    }

    /**
     * 比较两个版本
     *
     * @param version1 {@code version1}，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @param version2 {@code version2}，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 比较后的{@code versions}结果，供调用方继续处理
     */
    @GetMapping("/compare")
    public ApiResponse<String> compareVersions(
            @RequestParam String version1,
            @RequestParam String version2) {
        return ApiResponse.success(historyService.compareVersions(version1, version2));
    }
}
