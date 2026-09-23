package com.workflow.process.form.api.web;

import com.workflow.core.security.RequiresPermission;

import com.workflow.core.result.Result;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.process.form.application.ProcessNodeFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程节点表单绑定控制器
 */
@RequiresPermission("process:definition:view")
@RestController
@RequestMapping("/api/process-node-form")
@RequiredArgsConstructor
public class ProcessNodeFormController {
    
    private final ProcessNodeFormService nodeFormService;
    private final EntityFormService formService;
    
    /**
     * 查询流程的节点表单绑定
     *
     * @param processConfigId 流程配置ID，后续用于列出流程时定位或关联目标
     * @return 符合条件的流程节点表单结果，供调用方继续处理
     */
    @GetMapping("/process/{processConfigId}")
    public Result<List<ProcessNodeForm>> listByProcess(@PathVariable String processConfigId) {
        return Result.success(nodeFormService.getByProcessConfigId(processConfigId));
    }
    
    /**
     * 查询节点的表单绑定
     *
     * @param processConfigId 流程配置ID，后续用于读取节点ID时定位或关联目标
     * @param nodeId 节点ID，后续用于读取节点ID时定位或关联目标
     * @return 符合条件的{@code result<process}节点{@code form>}结果，供调用方继续处理
     */
    @GetMapping("/process/{processConfigId}/node/{nodeId}")
    public Result<ProcessNodeForm> getByNodeId(
            @PathVariable String processConfigId,
            @PathVariable String nodeId) {
        return Result.success(nodeFormService.getByNodeId(processConfigId, nodeId));
    }
    
    /**
     * 保存节点表单绑定
     *
     * @param nodeForm 节点表单，作为 {@code Result.success} 的输入影响后续处理
     * @return 保存后的流程节点表单结果，供调用方继续处理
     */
    @PostMapping
    @RequiresPermission("process:definition:manage")
    public Result<ProcessNodeForm> save(@RequestBody ProcessNodeForm nodeForm) {
        return Result.success(nodeFormService.saveNodeForm(nodeForm));
    }
    
    /**
     * 删除节点表单绑定
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 删除后的流程节点表单结果，供调用方继续处理
     */
    @PostMapping("/{id}")
    @RequiresPermission("process:definition:manage")
    public Result<Void> delete(@PathVariable String id) {
        nodeFormService.deleteNodeForm(id);
        return Result.success();
    }
    
    /**
     * 批量保存节点表单绑定
     *
     * @param processConfigId 流程配置ID，后续用于保存节点表单集合时定位或关联目标
     * @param nodeForms 节点表单集合，供本方法保存节点表单集合时使用
     * @return 保存后的节点表单集合结果，供调用方继续处理
     */
    @PostMapping("/process/{processConfigId}")
    @RequiresPermission("process:definition:manage")
    public Result<Void> saveNodeForms(
            @PathVariable String processConfigId,
            @RequestBody List<ProcessNodeForm> nodeForms) {
        nodeFormService.saveNodeForms(processConfigId, nodeForms);
        return Result.success();
    }
    
    /**
     * 查询实体的表单列表（用于绑定选择）
     *
     * @param entityId 实体ID，后续用于读取实体表单集合时定位或关联目标
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    @GetMapping("/entity/{entityId}/forms")
    public Result<List<EntityForm>> getEntityForms(@PathVariable String entityId) {
        return Result.success(formService.getFormsByEntityId(entityId));
    }
}
