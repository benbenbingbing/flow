package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.EntityListActionDeleteRequest;
import com.workflow.dto.EntityListActionSaveRequest;
import com.workflow.dto.EntityListItemReorderRequest;
import com.workflow.dto.EntityListSceneDeleteRequest;
import com.workflow.dto.EntityListSceneSaveRequest;
import com.workflow.entity.EntityListAction;
import com.workflow.entity.EntityListScene;
import com.workflow.service.EntityListRelationalConfigService;
import com.workflow.service.UiConfigurationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实体列表项（动作与场景）管理控制器。
 * <p>针对单个列表配置维护其行级动作（action）与场景（scene），
 * 所有操作均需通过列表访问权限校验。
 */
@RestController
@RequestMapping("/api/entity-list-config/{listId}")
@RequiredArgsConstructor
public class EntityListItemController {

    private final EntityListRelationalConfigService service;
    private final UiConfigurationAccessService accessService;

    /**
     * 新增列表动作。POST /api/entity-list-config/{listId}/actions
     *
     * @param listId  列表配置ID
     * @param request 动作保存请求
     * @return 创建后的动作
     */
    @PostMapping("/actions")
    public Result<EntityListAction> createAction(
            @PathVariable String listId,
            @RequestBody EntityListActionSaveRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.createAction(listId, request));
    }

    /**
     * 增量更新列表动作。POST /api/entity-list-config/{listId}/actions/{actionId}/patch
     *
     * @param listId   列表配置ID
     * @param actionId 动作ID
     * @param request  动作保存请求
     * @return 更新后的动作
     */
    @PostMapping("/actions/{actionId}/patch")
    public Result<EntityListAction> patchAction(
            @PathVariable String listId,
            @PathVariable String actionId,
            @RequestBody EntityListActionSaveRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.patchAction(listId, actionId, request));
    }

    /**
     * 调整列表动作排序。POST /api/entity-list-config/{listId}/actions/{actionId}/order
     *
     * @param listId   列表配置ID
     * @param actionId 动作ID
     * @param request  排序请求（含目标位置/参考节点）
     * @return 排序后的动作
     */
    @PostMapping("/actions/{actionId}/order")
    public Result<EntityListAction> reorderAction(
            @PathVariable String listId,
            @PathVariable String actionId,
            @RequestBody EntityListItemReorderRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.reorderAction(listId, actionId, request));
    }

    /**
     * 删除列表动作（乐观锁校验）。POST /api/entity-list-config/{listId}/actions/{actionId}/delete
     *
     * @param listId   列表配置ID
     * @param actionId 动作ID
     * @param request  删除请求，携带期望版本号
     * @return 无数据返回
     */
    @PostMapping("/actions/{actionId}/delete")
    public Result<Void> deleteAction(
            @PathVariable String listId,
            @PathVariable String actionId,
            @RequestBody EntityListActionDeleteRequest request) {
        accessService.requireListAccess(listId);
        service.deleteAction(listId, actionId, request.getExpectedRevision());
        return Result.success();
    }

    /**
     * 新增列表场景。POST /api/entity-list-config/{listId}/scenes
     *
     * @param listId  列表配置ID
     * @param request 场景保存请求
     * @return 创建后的场景
     */
    @PostMapping("/scenes")
    public Result<EntityListScene> createScene(
            @PathVariable String listId,
            @RequestBody EntityListSceneSaveRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.createScene(listId, request));
    }

    /**
     * 查询列表全部场景项。GET /api/entity-list-config/{listId}/scenes
     *
     * @param listId 列表配置ID
     * @return 场景列表
     */
    @GetMapping("/scenes")
    public Result<List<EntityListScene>> scenes(@PathVariable String listId) {
        accessService.requireListAccess(listId);
        return Result.success(service.findSceneItems(listId));
    }

    /**
     * 增量更新列表场景。POST /api/entity-list-config/{listId}/scenes/{sceneId}/patch
     *
     * @param listId  列表配置ID
     * @param sceneId 场景ID
     * @param request 场景保存请求
     * @return 更新后的场景
     */
    @PostMapping("/scenes/{sceneId}/patch")
    public Result<EntityListScene> patchScene(
            @PathVariable String listId,
            @PathVariable String sceneId,
            @RequestBody EntityListSceneSaveRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.patchScene(listId, sceneId, request));
    }

    /**
     * 删除列表场景（乐观锁校验）。POST /api/entity-list-config/{listId}/scenes/{sceneId}/delete
     *
     * @param listId  列表配置ID
     * @param sceneId 场景ID
     * @param request 删除请求，携带期望版本号
     * @return 无数据返回
     */
    @PostMapping("/scenes/{sceneId}/delete")
    public Result<Void> deleteScene(
            @PathVariable String listId,
            @PathVariable String sceneId,
            @RequestBody EntityListSceneDeleteRequest request) {
        accessService.requireListAccess(listId);
        service.deleteScene(listId, sceneId, request.getExpectedRevision());
        return Result.success();
    }
}
