package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.entity.FlowAction;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程动作管理接口
 */
@RestController
@RequestMapping("/api/flow-actions")
@RequiredArgsConstructor
public class FlowActionController {
    
    private final FlowActionService flowActionService;
    
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
    public ApiResponse<FlowAction> saveAction(@RequestBody FlowAction action) {
        return ApiResponse.success(flowActionService.saveAction(action));
    }
    
    /**
     * 删除动作（仅草稿）
     */
    @DeleteMapping("/{actionId}")
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
