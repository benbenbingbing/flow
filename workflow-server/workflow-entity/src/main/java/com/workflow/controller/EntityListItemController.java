package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.EntityListActionSaveRequest;
import com.workflow.dto.EntityListItemReorderRequest;
import com.workflow.dto.EntityListSceneSaveRequest;
import com.workflow.entity.EntityListAction;
import com.workflow.entity.EntityListScene;
import com.workflow.service.EntityListRelationalConfigService;
import com.workflow.service.UiConfigurationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/entity-list-config/{listId}")
@RequiredArgsConstructor
public class EntityListItemController {

    private final EntityListRelationalConfigService service;
    private final UiConfigurationAccessService accessService;

    @PostMapping("/actions")
    public Result<EntityListAction> createAction(
            @PathVariable String listId,
            @RequestBody EntityListActionSaveRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.createAction(listId, request));
    }

    @PatchMapping("/actions/{actionId}")
    public Result<EntityListAction> patchAction(
            @PathVariable String listId,
            @PathVariable String actionId,
            @RequestBody EntityListActionSaveRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.patchAction(listId, actionId, request));
    }

    @PutMapping("/actions/{actionId}/order")
    public Result<EntityListAction> reorderAction(
            @PathVariable String listId,
            @PathVariable String actionId,
            @RequestBody EntityListItemReorderRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.reorderAction(listId, actionId, request));
    }

    @DeleteMapping("/actions/{actionId}")
    public Result<Void> deleteAction(
            @PathVariable String listId,
            @PathVariable String actionId,
            @RequestParam Integer expectedRevision) {
        accessService.requireListAccess(listId);
        service.deleteAction(listId, actionId, expectedRevision);
        return Result.success();
    }

    @PostMapping("/scenes")
    public Result<EntityListScene> createScene(
            @PathVariable String listId,
            @RequestBody EntityListSceneSaveRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.createScene(listId, request));
    }

    @GetMapping("/scenes")
    public Result<List<EntityListScene>> scenes(@PathVariable String listId) {
        accessService.requireListAccess(listId);
        return Result.success(service.findSceneItems(listId));
    }

    @PatchMapping("/scenes/{sceneId}")
    public Result<EntityListScene> patchScene(
            @PathVariable String listId,
            @PathVariable String sceneId,
            @RequestBody EntityListSceneSaveRequest request) {
        accessService.requireListAccess(listId);
        return Result.success(service.patchScene(listId, sceneId, request));
    }

    @DeleteMapping("/scenes/{sceneId}")
    public Result<Void> deleteScene(
            @PathVariable String listId,
            @PathVariable String sceneId,
            @RequestParam Integer expectedRevision) {
        accessService.requireListAccess(listId);
        service.deleteScene(listId, sceneId, expectedRevision);
        return Result.success();
    }
}
