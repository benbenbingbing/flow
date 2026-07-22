package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.service.EntityFormService;
import com.workflow.service.ProcessNodeFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程节点表单绑定控制器
 */
@RestController
@RequestMapping("/api/process-node-form")
@RequiredArgsConstructor
public class ProcessNodeFormController {
    
    private final ProcessNodeFormService nodeFormService;
    private final EntityFormService formService;
    
    /**
     * 查询流程的节点表单绑定
     */
    @GetMapping("/process/{processConfigId}")
    public Result<List<ProcessNodeForm>> listByProcess(@PathVariable String processConfigId) {
        return Result.success(nodeFormService.getByProcessConfigId(processConfigId));
    }
    
    /**
     * 查询节点的表单绑定
     */
    @GetMapping("/process/{processConfigId}/node/{nodeId}")
    public Result<ProcessNodeForm> getByNodeId(
            @PathVariable String processConfigId,
            @PathVariable String nodeId) {
        return Result.success(nodeFormService.getByNodeId(processConfigId, nodeId));
    }
    
    /**
     * 保存节点表单绑定
     */
    @PostMapping
    public Result<ProcessNodeForm> save(@RequestBody ProcessNodeForm nodeForm) {
        return Result.success(nodeFormService.saveNodeForm(nodeForm));
    }
    
    /**
     * 删除节点表单绑定
     */
    @PostMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        nodeFormService.deleteNodeForm(id);
        return Result.success();
    }
    
    /**
     * 批量保存节点表单绑定
     */
    @PostMapping("/process/{processConfigId}")
    public Result<Void> saveNodeForms(
            @PathVariable String processConfigId,
            @RequestBody List<ProcessNodeForm> nodeForms) {
        nodeFormService.saveNodeForms(processConfigId, nodeForms);
        return Result.success();
    }
    
    /**
     * 查询实体的表单列表（用于绑定选择）
     */
    @GetMapping("/entity/{entityId}/forms")
    public Result<List<EntityForm>> getEntityForms(@PathVariable String entityId) {
        return Result.success(formService.getFormsByEntityId(entityId));
    }
}
