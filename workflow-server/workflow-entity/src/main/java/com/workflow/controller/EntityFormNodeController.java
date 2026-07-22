package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.dto.EntityFormNodeCreateRequest;
import com.workflow.dto.EntityFormNodeDeleteRequest;
import com.workflow.dto.EntityFormNodePatchRequest;
import com.workflow.dto.EntityFormNodeReorderRequest;
import com.workflow.entity.EntityFormNode;
import com.workflow.service.EntityFormNodeService;
import com.workflow.service.UiConfigurationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/entity-forms/{formId}/nodes")
@RequiredArgsConstructor
public class EntityFormNodeController {

    private final EntityFormNodeService nodeService;
    private final UiConfigurationAccessService accessService;

    @GetMapping
    public Result<List<EntityFormNode>> list(@PathVariable String formId) {
        accessService.requireFormAccess(formId);
        return Result.success(nodeService.findByFormId(formId));
    }

    @PostMapping
    public Result<EntityFormNode> create(
            @PathVariable String formId,
            @RequestBody EntityFormNodeCreateRequest request) {
        accessService.requireFormAccess(formId);
        return Result.success(nodeService.create(formId, request));
    }

    @PostMapping("/{nodeId}/patch")
    public Result<EntityFormNode> patch(
            @PathVariable String formId,
            @PathVariable String nodeId,
            @RequestBody EntityFormNodePatchRequest request) {
        accessService.requireFormAccess(formId);
        return Result.success(nodeService.patch(formId, nodeId, request));
    }

    @PostMapping("/{nodeId}/order")
    public Result<EntityFormNode> reorder(
            @PathVariable String formId,
            @PathVariable String nodeId,
            @RequestBody EntityFormNodeReorderRequest request) {
        accessService.requireFormAccess(formId);
        return Result.success(nodeService.reorder(formId, nodeId, request));
    }

    @PostMapping("/{nodeId}/delete")
    public Result<Void> delete(
            @PathVariable String formId,
            @PathVariable String nodeId,
            @RequestBody EntityFormNodeDeleteRequest request) {
        accessService.requireFormAccess(formId);
        nodeService.delete(formId, nodeId, request.getExpectedRevision());
        return Result.success();
    }

    @PostMapping("/update")
    public Result<Void> replaceByDiff(
            @PathVariable String formId,
            @RequestParam Integer expectedRevision,
            @RequestBody List<EntityFormNode> nodes) {
        accessService.requireFormAccess(formId);
        nodeService.replaceByDiff(formId, nodes, expectedRevision);
        return Result.success();
    }
}
