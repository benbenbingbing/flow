package com.workflow.entity.definition.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.definition.api.request.EntityRelationSaveRequest;
import com.workflow.entity.definition.api.response.EntityRelationDTO;
import com.workflow.entity.definition.application.EntityRelationDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping
    public ApiResponse<List<EntityRelationDTO>> list(
            @PathVariable String entityId) {
        return ApiResponse.success(relationService.list(entityId));
    }

    @GetMapping("/{relationId}")
    public ApiResponse<EntityRelationDTO> get(
            @PathVariable String entityId,
            @PathVariable String relationId) {
        return ApiResponse.success(
                relationService.get(entityId, relationId));
    }

    @PostMapping
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityRelationDTO> create(
            @PathVariable String entityId,
            @RequestBody EntityRelationSaveRequest request) {
        return ApiResponse.success(
                relationService.create(entityId, request));
    }

    @PutMapping("/{relationId}")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<EntityRelationDTO> update(
            @PathVariable String entityId,
            @PathVariable String relationId,
            @RequestBody EntityRelationSaveRequest request) {
        return ApiResponse.success(
                relationService.update(entityId, relationId, request));
    }

    @DeleteMapping("/{relationId}")
    @RequiresPermission("entity:definition:manage")
    public ApiResponse<Void> delete(
            @PathVariable String entityId,
            @PathVariable String relationId) {
        relationService.delete(entityId, relationId);
        return ApiResponse.success();
    }
}
