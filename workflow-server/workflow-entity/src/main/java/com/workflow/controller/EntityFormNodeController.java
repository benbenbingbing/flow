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

/**
 * 实体表单节点管理控制器。
 * <p>针对单个表单维护其节点树：查询、创建、增量更新（patch）、排序、删除及整包替换，
 * 所有操作均需通过表单访问权限校验。
 */
@RestController
@RequestMapping("/api/entity-forms/{formId}/nodes")
@RequiredArgsConstructor
public class EntityFormNodeController {

    private final EntityFormNodeService nodeService;
    private final UiConfigurationAccessService accessService;

    /**
     * 查询表单的全部节点。GET /api/entity-forms/{formId}/nodes
     *
     * @param formId 表单ID
     * @return 节点列表
     */
    @GetMapping
    public Result<List<EntityFormNode>> list(@PathVariable String formId) {
        accessService.requireFormAccess(formId);
        return Result.success(nodeService.findByFormId(formId));
    }

    /**
     * 新增表单节点。POST /api/entity-forms/{formId}/nodes
     *
     * @param formId  表单ID
     * @param request 节点创建请求
     * @return 创建后的节点
     */
    @PostMapping
    public Result<EntityFormNode> create(
            @PathVariable String formId,
            @RequestBody EntityFormNodeCreateRequest request) {
        accessService.requireFormAccess(formId);
        return Result.success(nodeService.create(formId, request));
    }

    /**
     * 增量更新节点。POST /api/entity-forms/{formId}/nodes/{nodeId}/patch
     *
     * @param formId  表单ID
     * @param nodeId  节点ID
     * @param request 节点补丁请求（含期望版本号）
     * @return 更新后的节点
     */
    @PostMapping("/{nodeId}/patch")
    public Result<EntityFormNode> patch(
            @PathVariable String formId,
            @PathVariable String nodeId,
            @RequestBody EntityFormNodePatchRequest request) {
        accessService.requireFormAccess(formId);
        return Result.success(nodeService.patch(formId, nodeId, request));
    }

    /**
     * 调整节点排序。POST /api/entity-forms/{formId}/nodes/{nodeId}/order
     *
     * @param formId  表单ID
     * @param nodeId  节点ID
     * @param request 排序请求（含期望版本号）
     * @return 排序后的节点
     */
    @PostMapping("/{nodeId}/order")
    public Result<EntityFormNode> reorder(
            @PathVariable String formId,
            @PathVariable String nodeId,
            @RequestBody EntityFormNodeReorderRequest request) {
        accessService.requireFormAccess(formId);
        return Result.success(nodeService.reorder(formId, nodeId, request));
    }

    /**
     * 删除节点（乐观锁校验）。POST /api/entity-forms/{formId}/nodes/{nodeId}/delete
     *
     * @param formId  表单ID
     * @param nodeId  节点ID
     * @param request 删除请求，携带期望版本号
     * @return 无数据返回
     */
    @PostMapping("/{nodeId}/delete")
    public Result<Void> delete(
            @PathVariable String formId,
            @PathVariable String nodeId,
            @RequestBody EntityFormNodeDeleteRequest request) {
        accessService.requireFormAccess(formId);
        nodeService.delete(formId, nodeId, request.getExpectedRevision());
        return Result.success();
    }

    /**
     * 整包替换表单节点（按差异写入，需表单 CAS 校验）。POST /api/entity-forms/{formId}/nodes/update
     *
     * @param formId           表单ID
     * @param expectedRevision 期望的表单版本号
     * @param nodes             替换后的完整节点列表
     * @return 无数据返回
     */
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
