package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.dto.FlowActionSaveRequest;
import com.workflow.dto.FlowActionTimingOptionDTO;
import com.workflow.entity.FlowAction;
import com.workflow.process.action.FlowActionTimingCatalog;
import com.workflow.service.FlowActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程动作管理接口
 */
@RestController
@RequestMapping("/api/process-actions")
@RequiredArgsConstructor
public class FlowActionController {
    
    private final FlowActionService flowActionService;
    private final FlowActionTimingCatalog timingCatalog;
    
    /**
     * 查询流程配置下所有草稿动作
     */
    @GetMapping("/process/{processConfigId}")
    public ApiResponse<List<FlowAction>> findDraftActions(@PathVariable String processConfigId) {
        return ApiResponse.success(flowActionService.findDraftActions(processConfigId));
    }
    
    /**
     * 查询顺序流下所有草稿动作
     */
    @GetMapping("/process/{processConfigId}/flow/{sequenceFlowId}")
    public ApiResponse<List<FlowAction>> findDraftActionsBySequenceFlow(
            @PathVariable String processConfigId,
            @PathVariable String sequenceFlowId) {
        return ApiResponse.success(flowActionService.findDraftActionsBySequenceFlow(processConfigId, sequenceFlowId));
    }

    /**
     * 按作用域与元素绑定查询草稿动作。
     *
     * @param processConfigId 流程配置 ID
     * @param scopeType       作用域类型
     * @param elementId       BPMN 元素 ID；流程级可传空
     * @return 草稿动作列表
     */
    @GetMapping("/process/{processConfigId}/binding")
    public ApiResponse<List<FlowAction>> findDraftActionsByBinding(
            @PathVariable String processConfigId,
            @RequestParam String scopeType,
            @RequestParam(required = false) String elementId) {
        return ApiResponse.success(
                flowActionService.findDraftActionsByBinding(processConfigId, scopeType, elementId));
    }

    /**
     * 查询可用触发时机选项，支持按作用域与 BPMN 元素类型过滤。
     *
     * @param scopeType 作用域类型；为空不限
     * @param bpmnType   BPMN 元素类型；用于判断是否用户任务
     * @return 触发时机选项列表
     */
    @GetMapping("/timing-options")
    public ApiResponse<List<FlowActionTimingOptionDTO>> timingOptions(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String bpmnType) {
        return ApiResponse.success(timingCatalog.list(scopeType, bpmnType));
    }
    
    /**
     * 查询版本下所有已发布动作
     */
    @GetMapping("/version/{versionId}")
    public ApiResponse<List<FlowAction>> findPublishedActions(@PathVariable String versionId) {
        return ApiResponse.success(flowActionService.findPublishedActions(versionId));
    }
    
    /**
     * 查询版本下特定顺序流的动作
     */
    @GetMapping("/version/{versionId}/flow/{sequenceFlowId}")
    public ApiResponse<List<FlowAction>> findPublishedActionsBySequenceFlow(
            @PathVariable String versionId,
            @PathVariable String sequenceFlowId) {
        return ApiResponse.success(flowActionService.findPublishedActionsBySequenceFlow(versionId, sequenceFlowId));
    }
    
    /**
     * 保存动作（新增或修改草稿）
     */
    @PostMapping
    public ApiResponse<FlowAction> saveAction(@Valid @RequestBody FlowActionSaveRequest action) {
        return ApiResponse.success(flowActionService.saveAction(action));
    }
    
    /**
     * 删除动作（仅草稿）
     */
    @PostMapping("/{actionId}")
    public ApiResponse<Void> deleteAction(@PathVariable String actionId) {
        flowActionService.deleteAction(actionId);
        return ApiResponse.success();
    }
    
    /**
     * 更新动作排序
     */
    @PostMapping("/sort")
    public ApiResponse<Void> updateSortOrder(@RequestBody List<String> actionIds) {
        flowActionService.updateSortOrder(actionIds);
        return ApiResponse.success();
    }
    
    /**
     * 切换启用状态
     */
    @PostMapping("/{actionId}/toggle")
    public ApiResponse<Void> toggleEnabled(@PathVariable String actionId) {
        flowActionService.toggleEnabled(actionId);
        return ApiResponse.success();
    }
}
