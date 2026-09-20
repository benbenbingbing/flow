package com.workflow.entity.list.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.entity.list.api.request.EntityListActionDeleteRequest;
import com.workflow.entity.list.api.request.EntityListActionSaveRequest;
import com.workflow.entity.list.api.request.EntityListItemReorderRequest;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListAction;
import com.workflow.entity.list.application.EntityListRelationalConfigService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实体列表动作管理控制器。
 * <p>针对单个列表配置维护其行级动作（action），
 * 所有操作均需通过列表访问权限校验。
 */
@AuthenticatedApi(objectAuthorization = true)
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

}
