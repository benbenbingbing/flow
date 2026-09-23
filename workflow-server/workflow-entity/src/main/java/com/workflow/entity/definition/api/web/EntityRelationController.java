package com.workflow.entity.definition.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.definition.api.request.EntityRelationSaveRequest;
import com.workflow.entity.definition.api.response.EntityRelationDTO;
import com.workflow.entity.definition.application.EntityRelationDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 独立实体关系管理接口。 */
@RestController
@RequestMapping("/api/entity/{entityId}/relations")
@RequiresPermission("entity:definition:view")
@RequiredArgsConstructor
public class EntityRelationController {

    private final EntityRelationDefinitionService relationService;

    /**
     * 列出实体关系；查询结果供调用方展示或继续处理。
     *
     * @param entityId 实体ID，后续用于列出实体关系时定位或关联目标
     * @return 符合条件的实体关系结果，供调用方继续处理
     */
    @GetMapping
    public ApiResponse<List<EntityRelationDTO>> list(
            @PathVariable String entityId) {
        return ApiResponse.success(relationService.list(entityId));
    }

    /**
     * 读取API{@code response<entity}关系{@code dto>}；结果供调用方展示或继续处理。
     *
     * @param entityId 实体ID，后续用于读取实体关系时定位或关联目标
     * @param relationId 关系ID，后续用于读取实体关系时定位或关联目标
     * @return 符合条件的API{@code response<entity}关系{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/{relationId}")
    public ApiResponse<EntityRelationDTO> get(
            @PathVariable String entityId,
            @PathVariable String relationId) {
        return ApiResponse.success(
                relationService.get(entityId, relationId));
    }

    /**
     * 表单和列表共用关系目录，包含反向查看所属记录的入口。
     *
     * @param entityId 实体ID，后续用于处理可用时定位或关联目标
     * @return 处理后的可用结果，供调用方继续处理
     */
    @GetMapping("/available")
    public ApiResponse<List<EntityRelationDTO>> available(@PathVariable String entityId) {
        return ApiResponse.success(relationService.available(entityId));
    }

    /**
     * 创建实体关系；结果供后续流程传递或持久化。
     *
     * @param entityId 实体ID，后续用于创建实体关系时定位或关联目标
     * @param request 本次请求，后续经校验后用于创建实体关系
     * @return 创建后的实体关系结果，供调用方继续处理
     */
    @PostMapping
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityRelationDTO> create(
            @PathVariable String entityId,
            @RequestBody EntityRelationSaveRequest request) {
        return ApiResponse.success(
                relationService.create(entityId, request));
    }

    /**
     * 更新实体关系；后续读取或执行将使用更新后的状态。
     *
     * @param entityId 实体ID，后续用于更新实体关系时定位或关联目标
     * @param relationId 关系ID，后续用于更新实体关系时定位或关联目标
     * @param request 本次请求，后续经校验后用于更新实体关系
     * @return 更新后的实体关系结果，供调用方继续处理
     */
    @PostMapping("/{relationId}")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityRelationDTO> update(
            @PathVariable String entityId,
            @PathVariable String relationId,
            @RequestBody EntityRelationSaveRequest request) {
        return ApiResponse.success(
                relationService.update(entityId, relationId, request));
    }

    /**
     * 删除实体关系；后续读取或执行将使用更新后的状态。
     *
     * @param entityId 实体ID，后续用于删除实体关系时定位或关联目标
     * @param relationId 关系ID，后续用于删除实体关系时定位或关联目标
     * @return 删除后的实体关系结果，供调用方继续处理
     */
    @PostMapping("/{relationId}/delete")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<Void> delete(
            @PathVariable String entityId,
            @PathVariable String relationId) {
        relationService.delete(entityId, relationId);
        return ApiResponse.success();
    }
}
